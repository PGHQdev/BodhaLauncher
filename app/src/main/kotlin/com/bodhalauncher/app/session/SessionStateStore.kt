package com.bodhalauncher.app.session

import android.content.Context
import androidx.core.content.edit
import com.bodhalauncher.engine.EngineState
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.SessionPhase
import java.time.Instant

/** Persists the engine snapshot locally (ADR 0009: nothing leaves the phone). */
class SessionStateStore(context: Context) {

    private val prefs = context.getSharedPreferences("session_engine", Context.MODE_PRIVATE)

    fun save(state: EngineState) {
        prefs.edit {
            putLong(KEY_NEXT_ID, state.nextSessionId)
            putBoolean(KEY_PENDING_PEEK, state.pendingPeek)
            putLong(KEY_LAST_OBSERVED, state.lastObservedAt?.toEpochMilli() ?: NONE)
            when (val phase = state.phase) {
                SessionPhase.Idle -> {
                    putString(KEY_PHASE, PHASE_IDLE)
                    putLong(KEY_SESSION, NONE)
                    putLong(KEY_ENDED_AT, NONE)
                }
                is SessionPhase.Active -> {
                    putString(KEY_PHASE, PHASE_ACTIVE)
                    putLong(KEY_SESSION, phase.session.value)
                    putLong(KEY_ENDED_AT, NONE)
                }
                is SessionPhase.ProvisionalEnd -> {
                    putString(KEY_PHASE, PHASE_PROVISIONAL)
                    putLong(KEY_SESSION, phase.session.value)
                    putLong(KEY_ENDED_AT, phase.endedAt.toEpochMilli())
                }
            }
        }
    }

    fun load(): EngineState {
        if (!prefs.contains(KEY_PHASE)) return EngineState.Initial
        val phase = when (prefs.getString(KEY_PHASE, PHASE_IDLE)) {
            PHASE_ACTIVE -> SessionPhase.Active(SessionId(prefs.getLong(KEY_SESSION, NONE)))
            PHASE_PROVISIONAL -> SessionPhase.ProvisionalEnd(
                SessionId(prefs.getLong(KEY_SESSION, NONE)),
                Instant.ofEpochMilli(prefs.getLong(KEY_ENDED_AT, NONE)),
            )
            else -> SessionPhase.Idle
        }
        return EngineState(
            nextSessionId = prefs.getLong(KEY_NEXT_ID, 1L),
            phase = phase,
            pendingPeek = prefs.getBoolean(KEY_PENDING_PEEK, false),
            lastObservedAt = prefs.getLong(KEY_LAST_OBSERVED, NONE)
                .takeIf { it != NONE }?.let(Instant::ofEpochMilli),
        )
    }

    private companion object {
        const val KEY_PHASE = "phase"
        const val KEY_SESSION = "session"
        const val KEY_ENDED_AT = "endedAt"
        const val KEY_NEXT_ID = "nextSessionId"
        const val KEY_PENDING_PEEK = "pendingPeek"
        const val KEY_LAST_OBSERVED = "lastObservedAt"
        const val PHASE_IDLE = "idle"
        const val PHASE_ACTIVE = "active"
        const val PHASE_PROVISIONAL = "provisionalEnd"
        const val NONE = -1L
    }
}
