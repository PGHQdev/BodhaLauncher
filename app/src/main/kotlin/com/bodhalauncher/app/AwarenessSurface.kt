package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.app.session.toRecord
import com.bodhalauncher.app.ui.AwarenessScreen
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.dayKey
import com.bodhalauncher.engine.resolveAwarenessToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Awareness, opening on Today (#171): today's session records as one count.
 * Reads live from the durable store, re-reading on every session transition —
 * [SessionRuntime.phase] is snapshot state — and each minute so crossing the
 * 4am boundary (ADR 0003) rolls the day without a relaunch.
 */
@Composable
fun AwarenessSurface(sessions: SessionRuntime, onBack: () -> Unit) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay((60 - LocalDateTime.now().second) * 1000L)
            value = LocalDateTime.now()
        }
    }
    val phase by sessions.phase
    val context = LocalContext.current
    // Null while the store is still being read: the screen shows nothing rather
    // than a 0 standing in for an unknown (#171).
    val today by produceState<AwarenessToday?>(null, now, phase) {
        value = withContext(Dispatchers.IO) {
            val records = BodhaDatabase.get(context).sessionRecords()
                .forDay(dayKey(now).toEpochDay())
                .map { it.toRecord() }
            resolveAwarenessToday(records, now)
        }
    }
    AwarenessScreen(today = today, onBack = onBack)
}
