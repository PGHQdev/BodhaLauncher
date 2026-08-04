package com.bodhalauncher.app.intent

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
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
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var launcherVisible = false

    /** Set from composition while an Open Check is on screen — one pause per opening (#77). */
    @Volatile
    var openCheckShowing = false

    /** The prompt due for the active session, if any; null once handled. */
    val promptDue = mutableStateOf<PromptDecision?>(null)

    /** The current session's chosen intent — Home's reducer input; null when none. */
    val sessionIntent = mutableStateOf<IntentCategory?>(null)

    fun start() {
        sessions.addTransitionListener { transition ->
            // Session-scoped: a new session starts clean, a final end clears it,
            // and a merge-window resume keeps it (neither transition fires).
            if (transition is Transition.SessionStarted || transition is Transition.SessionEnded) {
                sessionIntent.value = null
            }
            val decision = engine.onTransition(transition, currentSuppression())
            store.save(engine.snapshot())
            val active = (sessions.phase.value as? SessionPhase.Active)?.session
            when {
                decision != null && decision.session == active -> surface(decision)
                // The session the pending prompt belonged to is gone.
                promptDue.value != null && promptDue.value?.session != active -> promptDue.value = null
            }
        }
    }

    /**
     * The prompt lives on Home, so it can never draw over another app. If the
     * unlock went straight elsewhere — camera via a system shortcut, a
     * notification — the decision expires unless Home appears within the present
     * window: that moment of reflexive use has passed.
     */
    private fun surface(decision: PromptDecision) {
        promptDue.value = decision
        handler.removeCallbacks(expireUnpresented)
        if (!launcherVisible) handler.postDelayed(expireUnpresented, PRESENT_WINDOW_MS)
    }

    private val expireUnpresented = Runnable {
        if (!launcherVisible) promptDue.value = null
    }

    /** Called from the launcher activity's onResume/onPause. */
    fun onLauncherVisible() {
        launcherVisible = true
        handler.removeCallbacks(expireUnpresented)
    }

    fun onLauncherHidden() {
        launcherVisible = false
    }

    /**
     * The spec's five suppression states. An active or incoming call reads from
     * the audio mode (no permission needed). Navigation, emergency/utility flows,
     * and focus-task return have no signal yet — stubbed false until their
     * detection (or the Focus feature, #9) exists. Camera-via-shortcut is covered
     * by the present window in [surface] rather than a flag at decision time.
     */
    private fun currentSuppression() = SuppressionFlags(
        callActive = audio.mode == AudioManager.MODE_RINGTONE ||
            audio.mode == AudioManager.MODE_IN_CALL ||
            audio.mode == AudioManager.MODE_IN_COMMUNICATION,
        openCheckShowing = openCheckShowing,
    )

    /**
     * The user chose an intent (or typed free text with no category). The engine
     * started the cooldown when the prompt fired, so selection and dismissal
     * already rest identically; here we only record and clear.
     */
    fun select(category: IntentCategory?, text: String?) {
        val decision = promptDue.value ?: return
        records.appendSelection(decision, category, text)
        // "Just looking" opens Home without judgment — it shapes nothing.
        if (category != null && category != IntentCategory.JustLooking) {
            sessionIntent.value = category
        }
        promptDue.value = null
    }

    /** Swiped down or tapped outside — recorded, and the cooldown stands. */
    fun dismiss() {
        val decision = promptDue.value ?: return
        records.appendDismissal(decision)
        promptDue.value = null
    }

    private companion object {
        const val PRESENT_WINDOW_MS = 5000L
    }
}
