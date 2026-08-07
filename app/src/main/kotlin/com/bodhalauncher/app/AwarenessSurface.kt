package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.focus.toIntentSignal
import com.bodhalauncher.app.intent.IntentRecordStore
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.app.session.toRecord
import com.bodhalauncher.app.ui.AwarenessScreen
import com.bodhalauncher.app.ui.minuteNow
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.IntentSignal
import com.bodhalauncher.engine.dayKey
import com.bodhalauncher.engine.dayStart
import com.bodhalauncher.engine.resolveAwarenessSessions
import com.bodhalauncher.engine.resolveAwarenessToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What one read of the day yielded — resolved together so the count and the rows never disagree. */
private data class AwarenessDay(
    val today: AwarenessToday,
    val sessions: List<AwarenessSession>,
)

/**
 * Awareness, opening on Today (#171, #172): the day's session records, each
 * classified by whether the user stated an intent in it.
 *
 * Reads live from the durable stores, re-reading on every session transition —
 * [SessionRuntime.phase] is snapshot state — and each minute so crossing the 4am
 * boundary (ADR 0003) rolls the day without a relaunch.
 *
 * Two of ADR 0013's three signals come from the intent records; the third is the
 * Focus records, which exist only once a session has ended. A source that cannot
 * be read contributes nothing, which leaves a session unclassified rather than
 * asserting something false about it.
 */
@Composable
fun AwarenessSurface(sessions: SessionRuntime, onBack: () -> Unit) {
    val now = minuteNow()
    val phase by sessions.phase
    val context = LocalContext.current
    val intents = remember { IntentRecordStore(context) }
    // Null while the store is still being read — and stays null if the read
    // fails: the screen shows nothing rather than a 0 standing in for an
    // unknown (#171). Only an actual empty read resolves to the named absence.
    val day by produceState<AwarenessDay?>(null, now, phase) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val database = BodhaDatabase.get(context)
                val records = database.sessionRecords()
                    .forDay(dayKey(now).toEpochDay())
                    .map { it.toRecord() }
                // A session still running may have started before the boundary,
                // so signals are read from whichever came first — the day, or
                // the earliest session the view is about to show.
                val from = minOf(
                    dayStart(now),
                    records.minOfOrNull { it.start } ?: dayStart(now),
                ).toInstant()
                val focus = database.focusRecords().startedSince(from.toEpochMilli())
                    .map { it.toIntentSignal() }
                AwarenessDay(
                    today = resolveAwarenessToday(records, now),
                    sessions = resolveAwarenessSessions(records, intents.signalsSince(from) + focus),
                )
            }.getOrNull()
        } ?: value
    }
    AwarenessScreen(
        today = day?.today,
        sessions = day?.sessions.orEmpty(),
        onBack = onBack,
    )
}

private fun LocalDateTime.toInstant(): Instant = atZone(ZoneId.systemDefault()).toInstant()
