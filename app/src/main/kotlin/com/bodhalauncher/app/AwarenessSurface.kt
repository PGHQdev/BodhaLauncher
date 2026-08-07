package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.awareness.toRecord
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.app.focus.toIntentSignal
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.intent.IntentRecordStore
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.app.session.toRecord
import com.bodhalauncher.app.ui.AwarenessScreen
import com.bodhalauncher.app.ui.SessionDetailScreen
import com.bodhalauncher.app.ui.minuteNow
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.IntentSignal
import com.bodhalauncher.engine.SessionDetail
import com.bodhalauncher.engine.dayKey
import com.bodhalauncher.engine.dayStart
import com.bodhalauncher.engine.resolveAwarenessSessions
import com.bodhalauncher.engine.resolveAwarenessToday
import com.bodhalauncher.engine.resolveSessionDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What one read of the day yielded — resolved together so the count and the rows never disagree. */
private data class AwarenessDay(
    val today: AwarenessToday,
    val sessions: List<AwarenessSession>,
    /** Kept, because opening a session reads the same signals for its statement (#173). */
    val signals: List<IntentSignal>,
)

/**
 * Awareness, opening on Today (#171, #172): the day's session records, each
 * classified by whether the user stated an intent in it, and each opening its
 * own Session view (#173).
 *
 * Reads live from the durable stores, re-reading on every session transition —
 * [SessionRuntime.phase] is snapshot state — and each minute so crossing the 4am
 * boundary (ADR 0003) rolls the day without a relaunch.
 *
 * Two of ADR 0013's three signals come from the intent records; the third is the
 * Focus records, which exist only once a session has ended. A source that cannot
 * be read contributes nothing, which leaves a session unclassified rather than
 * asserting something false about it.
 *
 * Which session is open is held here rather than in the navigation model: ADR
 * 0011's depth is a bound on surfaces with a sub-screen to return to, and this
 * view has none — back leaves for root, which drops this state with the surface.
 */
@Composable
fun AwarenessSurface(
    sessions: SessionRuntime,
    events: EventLogger,
    catalog: AppCatalog,
    onBack: () -> Unit,
) {
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
                val signals = intents.signalsSince(from) + focus
                AwarenessDay(
                    today = resolveAwarenessToday(records, now),
                    sessions = resolveAwarenessSessions(records, signals),
                    signals = signals,
                )
            }.getOrNull()
        } ?: value
    }
    var open by remember { mutableStateOf<Long?>(null) }
    val openSession = day?.sessions?.firstOrNull { it.record.id == open }
    if (open == null || openSession == null) {
        AwarenessScreen(
            today = day?.today,
            sessions = day?.sessions.orEmpty(),
            onOpenSession = { open = it.record.id },
            onBack = onBack,
        )
        return
    }
    // The launches are the session's own; the checks and the repeated open come
    // from the event log, which carries no session, so the span is what selects
    // them. A running session's span is open, so it ends at now.
    val detail by produceState<SessionDetail?>(null, openSession, now) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val launches = BodhaDatabase.get(context).launchRecords()
                    .forSession(openSession.record.id)
                    .map { it.toRecord() }
                resolveSessionDetail(
                    session = openSession,
                    launches = launches,
                    events = events.between(
                        openSession.record.start,
                        openSession.record.end ?: LocalDateTime.now(),
                    ),
                    signals = day?.signals.orEmpty(),
                )
            }.getOrNull()
        }
    }
    // Resolved once per id rather than per recomposition: an icon is a binder
    // call and a bitmap decode, and a label is a walk of every installed app.
    // Not getOrPut — an app with no readable icon caches its null, as the
    // inbox's marks do.
    val icons = remember { mutableMapOf<String, ImageBitmap?>() }
    val labels = remember(catalog.apps.value) { catalog.apps.value.associateBy { it.id } }
    SessionDetailScreen(
        detail = detail,
        // An app uninstalled since the launch has no name left to give, and its
        // id is what Bodha actually holds — shown rather than hidden (#24).
        labelFor = { id -> labels[id]?.label ?: id },
        iconFor = { id ->
            if (id !in icons) icons[id] = runCatching { catalog.icon(id) }.getOrNull()
            icons[id]
        },
    )
}

private fun LocalDateTime.toInstant(): Instant = atZone(ZoneId.systemDefault()).toInstant()
