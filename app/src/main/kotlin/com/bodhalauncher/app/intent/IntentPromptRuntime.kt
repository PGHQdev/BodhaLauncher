package com.bodhalauncher.app.intent

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.engine.IntentCategory
import com.bodhalauncher.engine.IntentPromptEngine
import com.bodhalauncher.engine.PromptDecision
import com.bodhalauncher.engine.SessionPhase
import com.bodhalauncher.engine.SuppressionFlags
import com.bodhalauncher.engine.Transition

/**
 * Adapter for the pure trigger engine: feeds it the session transition stream,
 * persists its snapshot after every dispatch, and exposes the pending decision.
 * Backfilled/replayed starts keep the engine's window honest, but only a decision
 * for the currently active session is surfaced — a stale prompt is never shown.
 */
class IntentPromptRuntime(context: Context, private val sessions: SessionRuntime) {

    private val store = IntentPromptStateStore(context)
    private val records = IntentRecordStore(context)
    private val engine = IntentPromptEngine(initial = store.load())

    /** The prompt due for the active session, if any; null once handled. */
    val promptDue = mutableStateOf<PromptDecision?>(null)

    fun start() {
        sessions.addTransitionListener { transition ->
            val decision = engine.onTransition(transition, currentSuppression())
            store.save(engine.snapshot())
            val active = (sessions.phase.value as? SessionPhase.Active)?.session
            when {
                decision != null && decision.session == active -> promptDue.value = decision
                // The session the pending prompt belonged to is gone.
                promptDue.value != null && promptDue.value?.session != active -> promptDue.value = null
            }
        }
    }

    /** Suppression detection lands with #56; until then nothing suppresses. */
    private fun currentSuppression() = SuppressionFlags()

    /**
     * The user chose an intent (or typed free text with no category). The engine
     * started the cooldown when the prompt fired, so selection and dismissal
     * already rest identically; here we only record and clear.
     */
    fun select(category: IntentCategory?, text: String?) {
        val decision = promptDue.value ?: return
        records.appendSelection(decision, category, text)
        promptDue.value = null
    }

    /** Swiped down or tapped outside — recorded, and the cooldown stands. */
    fun dismiss() {
        val decision = promptDue.value ?: return
        records.appendDismissal(decision)
        promptDue.value = null
    }
}
