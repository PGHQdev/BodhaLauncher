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

    /**
     * The launcher process (re)started; carries the polled device state
     * (PowerManager.isInteractive + KeyguardManager.isKeyguardLocked, per ADR 0001)
     * so restored engine state can be reconciled instead of assuming the session closed.
     */
    data class Restarted(
        override val at: Instant,
        val interactive: Boolean,
        val keyguardLocked: Boolean,
    ) : DeviceEvent
}

/**
 * A UsageStats-shaped history entry (mirrors the UsageEvents.Event types ADR 0001
 * names for backfill), kept as its own type so the engine stays Android-free.
 */
sealed interface UsageRecord {
    val at: Instant

    data class ScreenInteractive(override val at: Instant) : UsageRecord
    data class ScreenNonInteractive(override val at: Instant) : UsageRecord

    /** Keyguard went away — an unlock as UsageStats sees it. */
    data class KeyguardHidden(override val at: Instant) : UsageRecord
}

/** Whether a session is currently running, provisionally ended, or absent. */
sealed interface SessionPhase {
    data object Idle : SessionPhase
    data class Active(val session: SessionId) : SessionPhase
    data class ProvisionalEnd(val session: SessionId, val endedAt: Instant) : SessionPhase
}

/**
 * The engine's complete state, exposed after every transition so the adapter can
 * persist it; restoring it into a fresh engine continues exactly where this one was.
 */
data class EngineState(
    val nextSessionId: Long,
    val phase: SessionPhase,
    val pendingPeek: Boolean,
    /** Last instant the engine observed — where a session interrupted by process death is known to have still been alive. */
    val lastObservedAt: Instant?,
) {
    companion object {
        val Initial = EngineState(nextSessionId = 1L, phase = SessionPhase.Idle, pendingPeek = false, lastObservedAt = null)
    }
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
 * The engine is pure: persistence lives outside; it only exposes ([snapshot])
 * and accepts (constructor) its [EngineState].
 */
class SessionEngine(initial: EngineState = EngineState.Initial) {

    private var nextSessionId = initial.nextSessionId
    private var phase: SessionPhase = initial.phase
    private var pendingPeek = initial.pendingPeek
    private var lastObservedAt: Instant? = initial.lastObservedAt

    fun snapshot(): EngineState = EngineState(nextSessionId, phase, pendingPeek, lastObservedAt)

    fun onEvent(event: DeviceEvent): List<Transition> {
        val out = mutableListOf<Transition>()
        finalizeIfWindowPassed(event.at)?.let(out::add)

        when (event) {
            is DeviceEvent.ScreenOn -> {
                if (phase !is SessionPhase.Active) pendingPeek = true
            }

            is DeviceEvent.Unlocked -> unlocked(event.at, out)

            is DeviceEvent.ScreenOff -> {
                when (val s = phase) {
                    is SessionPhase.Active -> phase = SessionPhase.ProvisionalEnd(s.session, event.at)
                    else -> if (pendingPeek) {
                        pendingPeek = false
                        out += Transition.PeekObserved(event.at)
                    }
                }
            }

            is DeviceEvent.Restarted -> {
                reconcile(event, out)
                // A provisional end created (or kept) by reconciliation may already be due.
                finalizeIfWindowPassed(event.at)?.let(out::add)
            }
        }
        lastObservedAt = event.at
        return out
    }

    /**
     * Restored state meets the polled device state. A session known alive at
     * [EngineState.lastObservedAt] but not confirmed by the current poll ends
     * provisionally at that last observed point, so a quick re-unlock still
     * merges exactly as it would on an unkilled engine.
     */
    private fun reconcile(event: DeviceEvent.Restarted, out: MutableList<Transition>) {
        val s = phase
        when {
            event.interactive && !event.keyguardLocked -> unlocked(event.at, out)

            else -> { // lock screen showing, or screen off: an unseen screen-off happened
                if (s is SessionPhase.Active) {
                    phase = SessionPhase.ProvisionalEnd(s.session, lastObservedAt ?: event.at)
                } else if (pendingPeek && !event.interactive) {
                    // A screen-on we saw ended without an unlock while we were dead.
                    pendingPeek = false
                    out += Transition.PeekObserved(lastObservedAt ?: event.at)
                }
                // On the lock screen the screen is on without an unlock — a peek may be forming.
                if (event.interactive) pendingPeek = true
            }
        }
    }

    private fun unlocked(at: Instant, out: MutableList<Transition>) {
        pendingPeek = false
        when (val s = phase) {
            is SessionPhase.Active -> Unit // still unlocked: the session carries on
            is SessionPhase.ProvisionalEnd -> {
                // Inside the window, else the entry finalize already cleared it.
                phase = SessionPhase.Active(s.session)
                out += Transition.SessionResumed(s.session, at)
            }
            SessionPhase.Idle -> out += start(at)
        }
    }

    private fun start(at: Instant): Transition {
        val session = SessionId(nextSessionId++)
        phase = SessionPhase.Active(session)
        return Transition.SessionStarted(session, at)
    }

    /**
     * Repairs sessions missed during process death by replaying UsageStats-shaped
     * history through the ordinary event rules — merge window and peek included.
     * Records at or before [EngineState.lastObservedAt] are already recorded live
     * and are never replayed. Deliberately conservative: when live receipt times and
     * the UsageStats log disagree around the cutoff, events are dropped rather than
     * risk rewriting a recorded session. Unlock detection rests on KeyguardHidden,
     * so gaps on a device with no lock screen may repair as peeks — best-effort.
     * Run before [DeviceEvent.Restarted] so reconciliation settles what remains.
     */
    fun backfill(records: List<UsageRecord>): List<Transition> {
        val cutoff = lastObservedAt
        return records
            .filter { cutoff == null || it.at.isAfter(cutoff) }
            .sortedBy { it.at }
            .flatMap { onEvent(it.toDeviceEvent()) }
    }

    private fun UsageRecord.toDeviceEvent(): DeviceEvent = when (this) {
        is UsageRecord.ScreenInteractive -> DeviceEvent.ScreenOn(at)
        is UsageRecord.ScreenNonInteractive -> DeviceEvent.ScreenOff(at)
        is UsageRecord.KeyguardHidden -> DeviceEvent.Unlocked(at)
    }

    /** Reports that [now] has been reached without a device event, finalizing any due end. */
    fun advanceTo(now: Instant): List<Transition> {
        val out = listOfNotNull(finalizeIfWindowPassed(now))
        lastObservedAt = now
        return out
    }

    private fun finalizeIfWindowPassed(now: Instant): Transition? {
        val s = phase
        if (s !is SessionPhase.ProvisionalEnd || Duration.between(s.endedAt, now) <= MERGE_WINDOW) return null
        phase = SessionPhase.Idle
        return Transition.SessionEnded(s.session, s.endedAt)
    }

    companion object {
        /** ADR 0001: how long a session end stays provisional. Adapters schedule finalization from it. */
        val MERGE_WINDOW: Duration = Duration.ofSeconds(30)
    }
}
