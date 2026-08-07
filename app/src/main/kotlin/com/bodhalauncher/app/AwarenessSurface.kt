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
import com.bodhalauncher.app.capability.CapabilityEducation
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.app.focus.toIntentSignal
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.UsageReader
import com.bodhalauncher.app.intent.IntentRecordStore
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.app.session.toRecord
import com.bodhalauncher.app.ui.AppOpensScreen
import com.bodhalauncher.app.ui.AwarenessScreen
import com.bodhalauncher.app.ui.AwarenessWeekScreen
import com.bodhalauncher.app.ui.SessionDetailScreen
import com.bodhalauncher.app.ui.minuteNow
import com.bodhalauncher.engine.AWARENESS_WEEK_DAYS
import com.bodhalauncher.engine.AwarenessView
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.IntentSignal
import com.bodhalauncher.engine.LaunchRecord
import com.bodhalauncher.engine.RetentionCategory
import com.bodhalauncher.engine.RetentionConfig
import com.bodhalauncher.engine.SessionDetail
import com.bodhalauncher.engine.SessionRecord
import com.bodhalauncher.engine.awarenessDayRecords
import com.bodhalauncher.engine.dayKey
import com.bodhalauncher.engine.dayStart
import com.bodhalauncher.engine.mergeLaunches
import com.bodhalauncher.engine.resolveAppDuration
import com.bodhalauncher.engine.resolveAppOpens
import com.bodhalauncher.engine.resolveAwarenessDay
import com.bodhalauncher.engine.resolveAwarenessSessions
import com.bodhalauncher.engine.resolveAwarenessUsage
import com.bodhalauncher.engine.resolveAwarenessWeek
import com.bodhalauncher.engine.resolveRetention
import com.bodhalauncher.engine.resolveSessionDetail
import com.bodhalauncher.engine.totalForegroundMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What one read of an app yielded (#175): the launch log and Android's usage
 * statistics, merged and folded on the IO thread, and the raw foreground map
 * left as it came back so the difference between "absent from a reading" and
 * "no reading" survives to [resolveAppDuration].
 */
private data class AppReading(
    val launches: List<LaunchRecord>,
    val foreground: Map<String, Long>?,
)

/**
 * What one read of a day yielded, **raw** (#176): the records and the signals,
 * with nothing resolved.
 *
 * Resolution moved out of the read and into composition, and it pays for itself
 * twice over. #177 needs a Pro flip to be a recomposition rather than a second
 * trip to the database, and #178 needs toggling an exclusion to cost no IO
 * either; both are keys on a `remember` here and neither is a re-read. It also
 * fixed a live leak: the old shape counted a running session day-agnostically,
 * so a record a later filter withheld would still have put "one running now" on
 * the line above the rows it was missing from.
 */
private data class AwarenessDay(
    val records: List<SessionRecord>,
    /** Kept, because opening a session reads the same signals for its statement (#173). */
    val signals: List<IntentSignal>,
)

/**
 * What one read of the week yielded (#176): the seven days' records, the signals
 * over the same stretch, and the two foreground readings left as maps.
 *
 * The maps are folded in composition rather than here, for the reason the day's
 * records are resolved there: #178 excludes apps by package, and an exclusion
 * the reader just toggled must change the figure without another trip to
 * Android's usage statistics.
 */
private data class AwarenessWeekReading(
    val records: List<SessionRecord>,
    val signals: List<IntentSignal>,
    val foreground: Map<String, Long>?,
    val previousForeground: Map<String, Long>?,
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
 * Which session is open, and which app inside it, are held here rather than in
 * the navigation model — and the drill-down being two levels deep is why that is
 * worth stating rather than assuming. `MAX_SURFACE_DEPTH` and ADR 0019's
 * "exactly one level" both bound the depth of *surfaces*, and this surface stays
 * at depth 0 throughout: every branch's back leaves for **root**, so no branch is
 * a stack frame and no state is ever popped rather than dropped. That is ADR
 * 0011 read literally rather than stretched.
 *
 * The cost is named rather than hidden: Escape from the App view drops to root
 * and loses the session the reader was in, which is the same trade ADR 0011
 * already accepted for losing a Search query on the way to Settings.
 *
 * Where usage access is granted, the App view fills in what the launch log
 * cannot see (#175). The grant is read here rather than per view, so one state
 * words every degraded sentence on the surface and no two figures can disagree
 * about why they are missing.
 *
 * Which view is showing, and which day it is showing, are `remember`s beside the
 * two drill-downs and for the same reason (#176). Today and Week are two ways of
 * reading the same records rather than two places, so neither is a `Place` and
 * neither adds depth; picking a day on the Week sets the day and switches the
 * view, and switching the view by hand drops both the picked day and the open
 * session — a switch answers "which view", not "where was I".
 */
@Composable
fun AwarenessSurface(
    sessions: SessionRuntime,
    events: EventLogger,
    catalog: AppCatalog,
    usage: UsageReader,
    education: CapabilityEducation,
    onBack: () -> Unit,
) {
    val now = minuteNow()
    val phase by sessions.phase
    val context = LocalContext.current
    val intents = remember { IntentRecordStore(context) }
    var view by remember { mutableStateOf(AwarenessView.Today) }
    // The day the Today view is showing: the live one, until the Week hands it
    // one of its seven.
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val today = dayKey(now)
    val shown = picked ?: today
    val isToday = shown == today
    // Null while the store is still being read — and stays null if the read
    // fails: the screen shows nothing rather than a 0 standing in for an
    // unknown (#171). Only an actual empty read resolves to the named absence.
    val day by produceState<AwarenessDay?>(null, now, phase, shown) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val database = BodhaDatabase.get(context)
                val records = database.sessionRecords()
                    .forDay(shown.toEpochDay())
                    .map { it.toRecord() }
                // A session still running may have started before the boundary,
                // so signals are read from whichever came first — the day, or
                // the earliest session the view is about to show.
                val from = minOf(
                    dayStart(shown),
                    records.minOfOrNull { it.start } ?: dayStart(shown),
                ).toInstant()
                val focus = database.focusRecords().startedSince(from.toEpochMilli())
                    .map { it.toIntentSignal() }
                AwarenessDay(records = records, signals = intents.signalsSince(from) + focus)
            }.getOrNull()
        } ?: value
    }
    // Resolved here rather than inside the read, so what renders is decided in
    // composition over a list already in hand. #177 adds the entitlement window
    // to these keys and #178 adds the exclusions; both are recompositions.
    val shownDay = remember(day, shown, now) {
        day?.let { resolveAwarenessDay(it.records, shown, now) }
    }
    val shownSessions = remember(day, shown, now) {
        day?.let { resolveAwarenessSessions(awarenessDayRecords(it.records, shown, now), it.signals) }
            .orEmpty()
    }
    // Resolved once per id rather than per recomposition: an icon is a binder
    // call and a bitmap decode, and a label is a walk of every installed app.
    // Not getOrPut — an app with no readable icon caches its null, as the
    // inbox's marks do. Hoisted above the branch because every branch names apps.
    val icons = remember { mutableMapOf<String, ImageBitmap?>() }
    val labels = remember(catalog.apps.value) { catalog.apps.value.associateBy { it.id } }
    // Null is an app uninstalled since the launch: it has no name left to give,
    // and the engine takes the absence rather than a fallback, because "named as
    // uninstalled" is its rule to state (#174).
    val labelFor: (String) -> String? = { id -> labels[id]?.label }
    val iconFor: (String) -> ImageBitmap? = { id ->
        if (id !in icons) icons[id] = runCatching { catalog.icon(id) }.getOrNull()
        icons[id]
    }
    val usageGranted = education.granted(Capability.UsageAccess)
    // Seen-held marks a later absence as a revocation rather than never-granted,
    // the digest slot's rule (#161). Composition-scoped, because no foreground
    // reading is stored (ADR 0009) and there is nothing else to infer it from.
    var usageGrantSeen by remember { mutableStateOf(false) }
    if (usageGranted) usageGrantSeen = true
    val usageState = resolveAwarenessUsage(
        granted = usageGranted,
        educationShown = education.educationShown(Capability.UsageAccess),
        grantSeen = usageGrantSeen,
    )
    // The one window both usage reads take: the 4am-snapped floor `launch_record`
    // is itself pruned to, so a span and the opens under it provably cover the
    // same stretch of time. It moves once a day rather than once a minute, which
    // is what makes it safe to key a read on.
    val usageFrom = resolveRetention(now, RetentionConfig())
        .cutoffs.getValue(RetentionCategory.RawUsageEvents)
        .toInstant().toEpochMilli()
    // Bodha's own package, because time spent reading Awareness is not time
    // spent on the phone's other apps — and counting it would make looking at
    // the figure move the figure. #178 widens this set (#176).
    val excludedPackages = remember(context) { setOf(context.packageName) }
    var open by remember { mutableStateOf<Long?>(null) }
    var openApp by remember { mutableStateOf<String?>(null) }
    val openSession = shownSessions.firstOrNull { it.record.id == open }
    val appId = openApp
    // A switch answers "which view", so it drops the day the Week handed over
    // and the session that was open: neither is a place to come back to.
    val onPickView: (AwarenessView) -> Unit = { next ->
        view = next
        picked = null
        open = null
    }
    // One `when` rather than an early return per branch: the four views are the
    // branches, and whatever must render over all of them goes after it. The
    // entitlement dialog (#177) and the actions sheets (#178) each need a site
    // reachable from every view, and an early return leaves them nowhere to go.
    when {
        appId != null -> {
            // Read unfiltered and newest-first; what renders is decided here in
            // composition, not in SQL. Resolving in composition rather than
            // inside the read is what lets a catalog that answers later name the
            // app without the log being read again.
            //
            // Both usage reads are made unconditionally rather than behind
            // `if (usageGranted)`: each guards itself on the grant, so the read
            // that decides what is drawn is the freshest one rather than a
            // composition-old boolean (#175).
            val reading by produceState<AppReading?>(
                null, appId, usageFrom, usageGranted, education.resumeTick,
            ) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val logged = BodhaDatabase.get(context).launchRecords()
                            .forApp(appId)
                            .map { it.toRecord() }
                        AppReading(
                            launches = mergeLaunches(
                                appId = appId,
                                logged = logged,
                                entries = usage.foregroundEntries(usageFrom).orEmpty(),
                            ),
                            foreground = usage.usedSince(usageFrom),
                        )
                    }.getOrNull()
                }
            }
            val label = labelFor(appId)
            AppOpensScreen(
                view = reading?.let {
                    resolveAppOpens(
                        appId = appId,
                        label = label,
                        launches = it.launches,
                        // The one place the id-to-package bridge is needed: the
                        // merge needs none, because a work-profile id matches no
                        // usage entry by construction and so collapses to the
                        // launch log on its own.
                        foreground = resolveAppDuration(
                            usage = usageState,
                            packageName = catalog.primaryPackage(appId),
                            reading = it.foreground,
                        ),
                    )
                },
                label = label ?: appId,
                usage = usageState,
                onTurnOn = { education.ask(Capability.UsageAccess, EducationEntry.FeatureTouch) },
            )
        }

        openSession != null -> {
            // The launches are the session's own; the checks and the repeated
            // open come from the event log, which carries no session, so the span
            // is what selects them. A running session's span is open, so it ends
            // at now.
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
            SessionDetailScreen(
                detail = detail,
                // An app uninstalled since the launch has no name left to give,
                // and its id is what Bodha actually holds — shown rather than
                // hidden (#24).
                labelFor = { id -> labelFor(id) ?: id },
                iconFor = iconFor,
                onOpenApp = { openApp = it },
            )
        }

        view == AwarenessView.Week -> {
            // Keyed on the day rather than the minute: a week's figures are
            // day-scale, and two binder calls a minute for a number that moves
            // once a day is not a trade worth making. `phase` still catches
            // every session transition, which is what actually changes a count.
            val reading by produceState<AwarenessWeekReading?>(null, today, phase, usageGranted) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val database = BodhaDatabase.get(context)
                        val first = today.minusDays(AWARENESS_WEEK_DAYS - 1L)
                        val records = database.sessionRecords()
                            .forDays(first.toEpochDay(), today.toEpochDay())
                            .map { it.toRecord() }
                        val from = minOf(
                            dayStart(first),
                            records.minOfOrNull { it.start } ?: dayStart(first),
                        ).toInstant()
                        val focus = database.focusRecords().startedSince(from.toEpochMilli())
                            .map { it.toIntentSignal() }
                        // Two reads, not nine: the period and the one before it.
                        // A single 4am day cannot be read from Android's
                        // midnight-aligned buckets at all, which is why no day
                        // row carries a duration (#176).
                        val periodStart = dayStart(first).toInstant().toEpochMilli()
                        AwarenessWeekReading(
                            records = records,
                            signals = intents.signalsSince(from) + focus,
                            foreground = usage.usedSince(periodStart),
                            previousForeground = usage.usedBetween(
                                dayStart(first.minusDays(AWARENESS_WEEK_DAYS.toLong()))
                                    .toInstant().toEpochMilli(),
                                periodStart,
                            ),
                        )
                    }.getOrNull()
                } ?: value
            }
            AwarenessWeekScreen(
                week = remember(reading, usageState, now, excludedPackages) {
                    reading?.let {
                        resolveAwarenessWeek(
                            records = it.records,
                            signals = it.signals,
                            foregroundMillis = totalForegroundMillis(it.foreground, excludedPackages),
                            previousForegroundMillis =
                                totalForegroundMillis(it.previousForeground, excludedPackages),
                            usage = usageState,
                            now = now,
                        )
                    }
                },
                usage = usageState,
                onPickView = onPickView,
                // Picking a day is a day and a view at once: the Week hands the
                // Today view a date, and the Today view is where a day is read.
                onOpenDay = {
                    picked = it
                    view = AwarenessView.Today
                    open = null
                },
                onTurnOnUsage = {
                    education.ask(Capability.UsageAccess, EducationEntry.FeatureTouch)
                },
            )
        }

        else -> AwarenessScreen(
            today = shownDay,
            sessions = shownSessions,
            day = shown,
            isToday = isToday,
            onPickView = onPickView,
            onOpenSession = { open = it.record.id },
            onBack = onBack,
        )
    }
}

private fun LocalDateTime.toInstant(): Instant = atZone(ZoneId.systemDefault()).toInstant()
