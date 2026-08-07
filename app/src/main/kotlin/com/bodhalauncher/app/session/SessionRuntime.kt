package com.bodhalauncher.app.session

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.bodhalauncher.engine.DeviceEvent
import com.bodhalauncher.engine.SessionEngine
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.SessionPhase
import com.bodhalauncher.engine.Transition
import com.bodhalauncher.engine.UsageRecord
import com.bodhalauncher.engine.sessionOrNull
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
    private val listeners = mutableListOf<(Transition) -> Unit>()

    /**
     * Which session anything scoped to one belongs to right now — the sheet slot's
     * saved state (#134) and Search's query (#180). Backed by [sessionOrNull], so
     * the merge window's rule that a provisional end still names its session is
     * read from the engine rather than restated beside it.
     *
     * [phase] is snapshot state, so composition reading this recomposes when the
     * session changes.
     */
    val currentSession: SessionId?
        get() = phase.value.sessionOrNull

    /** Register before [start] — restart reconciliation and backfill also publish. */
    fun addTransitionListener(listener: (Transition) -> Unit) {
        listeners += listener
    }

    /** For a listener that outlives less than the process does, such as composition (#134). */
    fun removeTransitionListener(listener: (Transition) -> Unit) {
        listeners -= listener
    }

    fun start() {
        val power = context.getSystemService(PowerManager::class.java)
        val now = Instant.now()
        backfillFromUsageStats(now)
        dispatch(
            DeviceEvent.Restarted(
                at = now,
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
            // Exported is safe and required: these are protected broadcasts only the
            // system can send, and USER_PRESENT is not delivered to not-exported receivers.
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** ADR 0001: backfill only when usage access is already granted — never requested here. */
    private fun backfillFromUsageStats(now: Instant) {
        if (!hasUsageAccess()) return
        val from = engine.snapshot().lastObservedAt ?: return
        // Synchronous binder query on the main thread at cold start: acceptable while
        // the gap since lastObservedAt is normally minutes; revisit if startup traces object.
        val usageEvents = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents(from.toEpochMilli(), now.toEpochMilli())
        val records = buildList {
            val event = UsageEvents.Event()
            while (usageEvents.getNextEvent(event)) {
                val at = Instant.ofEpochMilli(event.timeStamp)
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> add(UsageRecord.ScreenInteractive(at))
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> add(UsageRecord.ScreenNonInteractive(at))
                    UsageEvents.Event.KEYGUARD_HIDDEN -> add(UsageRecord.KeyguardHidden(at))
                }
            }
        }
        publish(engine.backfill(records))
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return when (appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )) {
            AppOpsManager.MODE_ALLOWED -> true
            // adb/policy grants can leave the op at its default; the permission decides then.
            AppOpsManager.MODE_DEFAULT -> context.checkSelfPermission(
                android.Manifest.permission.PACKAGE_USAGE_STATS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            else -> false
        }
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
        val snapshot = engine.snapshot()
        phase.value = snapshot.phase
        store.save(snapshot)
        transitions.forEach { transition -> listeners.forEach { it(transition) } }

        handler.removeCallbacks(finalize)
        val current = snapshot.phase
        if (current is SessionPhase.ProvisionalEnd) {
            val dueIn = Duration.between(Instant.now(), current.endedAt.plus(SessionEngine.MERGE_WINDOW))
            handler.postDelayed(finalize, dueIn.toMillis().coerceAtLeast(0) + FINALIZE_SLACK_MS)
        }
    }

    private companion object {
        const val FINALIZE_SLACK_MS = 1000L
    }
}
