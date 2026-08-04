package com.bodhalauncher.engine

import java.time.Duration
import java.time.Instant

@JvmInline
value class SessionId(val value: Long)

/** A raw device signal, timestamped at the moment it happened (ADR 0001). */
sealed interface DeviceEvent {
    val at: Instant

    /** Actual unlock: USER_PRESENT cross-checked with the keyguard. */
    data class Unlocked(override val at: Instant) : DeviceEvent

    data class ScreenOn(override val at: Instant) : DeviceEvent

    /** Device went non-interactive; always-on display counts as off. */
    data class ScreenOff(override val at: Instant) : DeviceEvent
}

sealed interface Transition {
    data class SessionStarted(val session: SessionId, val at: Instant) : Transition

    /** A re-unlock inside the merge window continued the session — not a start. */
    data class SessionResumed(val session: SessionId, val at: Instant) : Transition

    /** Final: the merge window passed without a re-unlock. [at] is the screen-off moment. */
    data class SessionEnded(val session: SessionId, val at: Instant) : Transition

    /**
     * A screen-on that ended without an unlock. Can also occur inside a merge
     * window that later resumes — a glance mid-gap is still a peek.
     */
    data class PeekObserved(val at: Instant) : Transition
}

/**
 * Pure session state machine per ADR 0001: a session spans unlock → non-interactive,
 * and a screen-off is provisional for the merge window — a re-unlock at or before
 * 30s after screen-off resumes the same session; the end becomes final strictly
 * after 30s, observed on the next event or via [advanceTo].
 * Time enters only through event timestamps and the instants passed to [advanceTo] —
 * the engine never reads a clock, so a given input sequence always yields the
 * same transitions.
 */
class SessionEngine {

    private sealed interface State {
        data object Idle : State
        data class Active(val session: SessionId) : State
        data class ProvisionalEnd(val session: SessionId, val endedAt: Instant) : State
    }

    private var nextSessionId = 1L
    private var state: State = State.Idle
    private var pendingPeek = false

    fun onEvent(event: DeviceEvent): List<Transition> {
        val out = mutableListOf<Transition>()
        finalizeIfWindowPassed(event.at)?.let(out::add)

        when (event) {
            is DeviceEvent.ScreenOn -> {
                if (state !is State.Active) pendingPeek = true
            }

            is DeviceEvent.Unlocked -> {
                pendingPeek = false
                when (val s = state) {
                    is State.Active -> Unit
                    is State.ProvisionalEnd -> {
                        state = State.Active(s.session)
                        out += Transition.SessionResumed(s.session, event.at)
                    }
                    State.Idle -> {
                        val session = SessionId(nextSessionId++)
                        state = State.Active(session)
                        out += Transition.SessionStarted(session, event.at)
                    }
                }
            }

            is DeviceEvent.ScreenOff -> {
                when (val s = state) {
                    is State.Active -> state = State.ProvisionalEnd(s.session, event.at)
                    else -> if (pendingPeek) {
                        pendingPeek = false
                        out += Transition.PeekObserved(event.at)
                    }
                }
            }
        }
        return out
    }

    /** Reports that [now] has been reached without a device event, finalizing any due end. */
    fun advanceTo(now: Instant): List<Transition> = listOfNotNull(finalizeIfWindowPassed(now))

    private fun finalizeIfWindowPassed(now: Instant): Transition? {
        val s = state
        if (s !is State.ProvisionalEnd || Duration.between(s.endedAt, now) <= MERGE_WINDOW) return null
        state = State.Idle
        return Transition.SessionEnded(s.session, s.endedAt)
    }

    private companion object {
        val MERGE_WINDOW: Duration = Duration.ofSeconds(30)
    }
}
