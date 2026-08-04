package com.bodhalauncher.app.session

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.bodhalauncher.engine.DeviceEvent
import com.bodhalauncher.engine.SessionEngine
import com.bodhalauncher.engine.SessionPhase
import com.bodhalauncher.engine.Transition
import java.time.Duration
import java.time.Instant

/**
 * Adapter between the platform and the pure engine: maps broadcasts to events,
 * polls device state on process start (ADR 0001), persists the snapshot after
 * every dispatch, and schedules merge-window finalization. No session rules here.
 */
class SessionRuntime(private val context: Context) {

    private val store = SessionStateStore(context)
    private val engine = SessionEngine(store.load())
    private val handler = Handler(Looper.getMainLooper())
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    val phase = mutableStateOf<SessionPhase>(engine.snapshot().phase)
    val recentTransitions = mutableStateListOf<Transition>()

    fun start() {
        val power = context.getSystemService(PowerManager::class.java)
        dispatch(
            DeviceEvent.Restarted(
                at = Instant.now(),
                interactive = power.isInteractive,
                keyguardLocked = keyguard.isKeyguardLocked,
            )
        )
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = Instant.now()
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> dispatch(DeviceEvent.ScreenOn(now))
                Intent.ACTION_SCREEN_OFF -> dispatch(DeviceEvent.ScreenOff(now))
                Intent.ACTION_USER_PRESENT ->
                    if (!keyguard.isKeyguardLocked) dispatch(DeviceEvent.Unlocked(now))
            }
        }
    }

    // Handler uses the uptime clock, which pauses in deep sleep — the callback can
    // fire late. Harmless: the engine finalizes from the screen-off timestamp on
    // whichever comes first, this callback or the next device event.
    private val finalize = Runnable { publish(engine.advanceTo(Instant.now())) }

    private fun dispatch(event: DeviceEvent) = publish(engine.onEvent(event))

    private fun publish(transitions: List<Transition>) {
        recentTransitions.addAll(transitions)
        while (recentTransitions.size > TRANSITION_LOG_LIMIT) recentTransitions.removeAt(0)
        val snapshot = engine.snapshot()
        phase.value = snapshot.phase
        store.save(snapshot)

        handler.removeCallbacks(finalize)
        val current = snapshot.phase
        if (current is SessionPhase.ProvisionalEnd) {
            val dueIn = Duration.between(Instant.now(), current.endedAt.plus(SessionEngine.MERGE_WINDOW))
            handler.postDelayed(finalize, dueIn.toMillis().coerceAtLeast(0) + FINALIZE_SLACK_MS)
        }
    }

    private companion object {
        const val FINALIZE_SLACK_MS = 1000L
        const val TRANSITION_LOG_LIMIT = 20
    }
}
