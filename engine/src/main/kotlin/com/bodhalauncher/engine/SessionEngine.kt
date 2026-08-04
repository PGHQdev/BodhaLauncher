package com.bodhalauncher.engine

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
    data class SessionEnded(val session: SessionId, val at: Instant) : Transition

    /** A screen-on that ended without an unlock. */
    data class PeekObserved(val at: Instant) : Transition
}

/**
 * Pure session state machine: a session spans unlock → non-interactive.
 * The 30s merge window (a re-unlock resuming the same session, per ADR 0001)
 * is not implemented yet — until then every screen-off ends the session
 * finally, and [Transition.SessionEnded] will become provisional when it lands.
 * Time enters only through event timestamps — the engine never reads a clock,
 * so a given event sequence always yields the same transitions.
 */
class SessionEngine {

    private var nextSessionId = 1L
    private var activeSession: SessionId? = null
    private var pendingPeek = false

    fun onEvent(event: DeviceEvent): List<Transition> = when (event) {
        is DeviceEvent.ScreenOn -> {
            if (activeSession == null) pendingPeek = true
            emptyList()
        }

        is DeviceEvent.Unlocked -> {
            pendingPeek = false
            if (activeSession != null) {
                emptyList()
            } else {
                val session = SessionId(nextSessionId++)
                activeSession = session
                listOf(Transition.SessionStarted(session, event.at))
            }
        }

        is DeviceEvent.ScreenOff -> {
            val session = activeSession
            when {
                session != null -> {
                    activeSession = null
                    listOf(Transition.SessionEnded(session, event.at))
                }
                pendingPeek -> {
                    pendingPeek = false
                    listOf(Transition.PeekObserved(event.at))
                }
                else -> emptyList()
            }
        }
    }
}
