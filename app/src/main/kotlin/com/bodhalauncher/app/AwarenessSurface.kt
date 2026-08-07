package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.awareness.ExclusionStore
import com.bodhalauncher.app.awareness.toRecord
import com.bodhalauncher.app.capability.CapabilityEducation
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.app.entitlement.EntitlementStore
import com.bodhalauncher.app.focus.toIntentSignal
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.UsageReader
import com.bodhalauncher.app.intent.IntentRecordStore
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.app.session.toRecord
import com.bodhalauncher.app.ui.AppOpensScreen
import com.bodhalauncher.app.ui.AwarenessActionsSheet
import com.bodhalauncher.app.ui.AwarenessScreen
import com.bodhalauncher.app.ui.AwarenessWeekScreen
import com.bodhalauncher.app.ui.ExclusionsScreen
import com.bodhalauncher.app.ui.LocalBodhaFormats
import com.bodhalauncher.app.ui.ProBoundaryDialog
import com.bodhalauncher.app.ui.SessionDetailScreen
import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.app.ui.minuteNow
import com.bodhalauncher.engine.AWARENESS_WEEK_DAYS
import com.bodhalauncher.engine.AwarenessView
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.IntentSignal
import com.bodhalauncher.engine.LaunchRecord
import com.bodhalauncher.engine.ProBoundary
import com.bodhalauncher.engine.RetentionCategory
import com.bodhalauncher.engine.RetentionConfig
import com.bodhalauncher.engine.SessionDetail
import com.bodhalauncher.engine.SessionRecord
import com.bodhalauncher.engine.awarenessDayRecords
import com.bodhalauncher.engine.awarenessSessionLine
import com.bodhalauncher.engine.dayKey
import com.bodhalauncher.engine.dayStart
import com.bodhalauncher.engine.mergeLaunches
import com.bodhalauncher.engine.resolveAppDuration
import com.bodhalauncher.engine.resolveAppOpens
import com.bodhalauncher.engine.resolveAwarenessDay
import com.bodhalauncher.engine.resolveAwarenessSessions
import com.bodhalauncher.engine.resolveAwarenessUsage
import com.bodhalauncher.engine.resolveAwarenessWeek
import com.bodhalauncher.engine.resolveAwarenessWindow
import com.bodhalauncher.engine.resolveRetention
import com.bodhalauncher.engine.resolveSessionDetail
import com.bodhalauncher.engine.retainedLaunches
import com.bodhalauncher.engine.retainedSessions
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
    /**
     * The excluded sessions' records (#178), read here because the App view is
     * the one place a launch has to be matched against a **span**: an unmediated
     * open carries no session id, so the only way it can be recognised as having
     * happened inside an excluded session is the clock.
     */
    val excludedSessions: List<SessionRecord>,
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
    /**
     * The day this read was for.
     *
     * `produceState` keeps its value across a key change — only the effect is
     * re-keyed — so without a stamp, picking a day on the Week would resolve the
     * *previous* day's records against the newly shown date, filter them to
     * nothing, and draw "Tuesday, 4 August · No sessions" over a day nobody had
     * read yet. That is the named absence standing in for an unknown, which is
     * the one thing this surface's reads are shaped to prevent (#171, #176).
     */
    val day: LocalDate,
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
 *
 * **Every branch runs the same pipeline, in this order: read unfiltered, then
 * exclude, then clamp, then resolve** (#177, #178). No query that produces the
 * records a view renders is narrowed by entitlement or by an exclusion, so
 * retention, the privacy dashboard and any later export are unaffected by
 * construction rather than by care — and a Pro flip or an exclusion toggle is a
 * recomposition over a list already in hand rather than a second trip to the
 * database. That claim rests entirely on `window` and `exclusions` being
 * `remember` keys wherever the resolution happens, so the key lists below are
 * load-bearing rather than incidental.
 *
 * The one exclusion-aware read is `sessionRecords().withIds(...)`, and it is the
 * exception that shows the rule: it fetches the excluded sessions *themselves* —
 * for the undo list, and for the span that recognises an unmediated open as
 * having happened inside one — rather than narrowing anything a view draws.
 *
 * **Exclusion runs before the window, and the order matters.** `AwarenessRender`
 * decides whether to state the Pro boundary by comparing sizes, so a record the
 * *reader* took out must not be what makes the boundary speak: clamping first
 * would have an exclusion announce a paywall.
 */
@Composable
fun AwarenessSurface(
    sessions: SessionRuntime,
    events: EventLogger,
    catalog: AppCatalog,
    /** The same cached snapshot the Library's rule cap reads (#22) — never a fetch. */
    entitlementStore: EntitlementStore,
    usage: UsageReader,
    education: CapabilityEducation,
    /** The one sheet (ADR 0011, #133): the row actions open into it, never beside it. */
    sheets: SheetSlot,
    onBack: () -> Unit,
) {
    val now = minuteNow()
    val phase by sessions.phase
    val context = LocalContext.current
    val intents = remember { IntentRecordStore(context) }
    // One construction site, so every view on this surface reads the same
    // `mutableStateOf` and an exclusion made on one is visible on the next
    // without a reload (#178).
    val exclusionStore = remember { ExclusionStore(context) }
    val exclusions by exclusionStore.exclusions
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
    val read by produceState<AwarenessDay?>(null, now, phase, shown) {
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
                AwarenessDay(
                    day = shown,
                    records = records,
                    signals = intents.signalsSince(from) + focus,
                )
            }.getOrNull()
        } ?: value
    }
    // A read for a different day than the one showing is not this day's read.
    // Retaining the last good value is what carries a failed re-read of *this*
    // day; carrying it across a day change would let one day's records answer
    // for another's, so the stamp is checked rather than trusted (#176).
    val day = read?.takeIf { it.day == shown }
    // How much of what was read renders (#177). Resolved from the cached
    // snapshot rather than fetched: the cache is the gate's whole truth, and a
    // billing outage must never narrow a window mid-session.
    val window = resolveAwarenessWindow(entitlementStore.snapshot.value, now)
    var boundaryShown by remember { mutableStateOf<ProBoundary?>(null) }
    // Excluded, then clamped, both here rather than inside the read: what renders
    // is decided in composition over a list already in hand, which is what makes
    // flipping the snapshot to Pro — or putting a session back — a recomposition
    // rather than a second query. The exclusion goes first, so a record the
    // reader took out is never what makes the boundary speak (#177, #178).
    val dayRender = remember(day, exclusions, window) {
        day?.let { window.sessions(retainedSessions(it.records, exclusions)) }
    }
    // Resolved here rather than inside the read, so what renders is decided in
    // composition over a list already in hand.
    val shownDay = remember(dayRender, shown, now) {
        dayRender?.let { resolveAwarenessDay(it.records, shown, now) }
    }
    val shownSessions = remember(dayRender, day, shown, now) {
        dayRender?.let {
            resolveAwarenessSessions(
                awarenessDayRecords(it.records, shown, now),
                day?.signals.orEmpty(),
            )
        }.orEmpty()
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
    // The 4am-snapped floor `launch_record` is itself pruned to: as far back as
    // any of Bodha's own opens can exist. It moves once a day rather than once a
    // minute, which is what makes it safe to key a read on.
    val retentionFrom = resolveRetention(now, RetentionConfig())
        .cutoffs.getValue(RetentionCategory.RawUsageEvents)
        .toInstant().toEpochMilli()
    // What the App view's usage reads take, which is the floor its **rows**
    // render from rather than the floor its records exist to (#177). The window
    // clamps the launches, so a span read over the retention floor would print
    // "18h 20m in the foreground" above seven days of opens — two answers to two
    // different questions, which is precisely the promise `resolveAppOpens` says
    // it cannot check and the caller owes it. On Pro `from` is null and the two
    // floors are the same one.
    val usageFrom = window.from?.let { dayStart(it).toInstant().toEpochMilli() } ?: retentionFrom
    // Bodha's own package, because time spent reading Awareness is not time
    // spent on the phone's other apps — and counting it would make looking at
    // the figure move the figure (#176). The excluded apps join it (#178): the
    // week rate is the one figure a reader can check against their own sense of
    // the week, and an excluded app still inside it would contradict the rule on
    // the one number where it shows.
    val excludedPackages = remember(context, exclusions) {
        setOf(context.packageName) + exclusions.apps.mapNotNull(catalog::primaryPackage)
    }
    var open by remember { mutableStateOf<Long?>(null) }
    var openApp by remember { mutableStateOf<String?>(null) }
    var showExclusions by remember { mutableStateOf(false) }
    val openSession = shownSessions.firstOrNull { it.record.id == open }
    // An app excluded while its own view was open falls back to the view it was
    // opened from, rather than rendering a screen about a thing that renders
    // nowhere. The Session branch needs no such guard: `openSession` is resolved
    // out of the already-filtered list, so an excluded session leaves it null.
    val appId = openApp?.takeIf { it !in exclusions.apps }
    // A switch answers "which view", so it drops the day the Week handed over,
    // the session that was open and the exclusions list: none is a place to come
    // back to.
    val onPickView: (AwarenessView) -> Unit = { next ->
        view = next
        picked = null
        open = null
        showExclusions = false
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
            //
            // Each also carries its **own** guard rather than sharing the outer
            // one. The launch log is the spine and needs no permission (ADR
            // 0013), so a usage read that throws must cost the reader the span
            // and the unmediated opens and nothing else — under one `runCatching`
            // it took the whole view down instead, leaving the named shell with
            // no opens, no day headings and no sentence saying what was missing.
            // It is also what makes `UnavailableReason.NoReading` reachable by a
            // failed reading at all, which is the case that arm was written for.
            val reading by produceState<AppReading?>(
                null, appId, usageFrom, usageGranted, education.resumeTick, exclusions,
            ) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val database = BodhaDatabase.get(context)
                        val logged = database.launchRecords()
                            .forApp(appId)
                            .map { it.toRecord() }
                        AppReading(
                            launches = mergeLaunches(
                                appId = appId,
                                logged = logged,
                                entries = runCatching { usage.foregroundEntries(usageFrom) }
                                    .getOrNull().orEmpty(),
                            ),
                            foreground = runCatching { usage.usedSince(usageFrom) }.getOrNull(),
                            // Read on the same trip rather than in a second
                            // `produceState`: it is one query against a handful
                            // of ids, and splitting it would let the launches and
                            // the spans that filter them arrive out of step.
                            excludedSessions = database.sessionRecords()
                                .withIds(exclusions.sessions.toList())
                                .map { it.toRecord() },
                        )
                    }.getOrNull()
                }
            }
            val label = labelFor(appId)
            // The clamp is the last step before resolving, over the whole
            // retained log the query returned (#177). Narrowing `forApp` instead
            // would be the same rows on a free phone and a different code path
            // on a Pro one — and this view could then never state a boundary,
            // because there would be nothing withheld for it to compare against.
            val appRender = remember(reading, exclusions, window) {
                reading?.let {
                    window.launches(retainedLaunches(it.launches, exclusions, it.excludedSessions))
                }
            }
            AppOpensScreen(
                view = reading?.let {
                    resolveAppOpens(
                        appId = appId,
                        label = label,
                        launches = appRender?.records.orEmpty(),
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
                boundary = appRender?.boundary,
                onBoundary = { boundaryShown = appRender?.boundary },
            )
        }

        openSession != null -> {
            // The launches are the session's own; the checks and the repeated
            // open come from the event log, which carries no session, so the span
            // is what selects them. A running session's span is open, so it ends
            // at now.
            //
            // Keyed on the exclusions as well, so excluding an app from inside
            // this view re-resolves it. That costs the launch and event reads
            // again — rare, cheap, and cheaper than a second state model holding
            // the unfiltered launches beside the filtered ones (#178).
            val detail by produceState<SessionDetail?>(null, openSession, now, exclusions) {
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
                            // The one resolver handed the exclusions directly:
                            // it has to tell a session that opened nothing from
                            // one whose every open the reader took out.
                            exclusions = exclusions,
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
                onAppActions = { sheets.open(Sheet.AwarenessAppActions(it)) },
            )
        }

        showExclusions -> {
            // Null while the read is in flight and null if it failed, which is
            // what the prune below is gated on: a failed read must not be read as
            // "retention took every one of these".
            val excluded by produceState<List<SessionRecord>?>(null, exclusions) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        BodhaDatabase.get(context).sessionRecords()
                            .withIds(exclusions.sessions.toList())
                            .map { it.toRecord() }
                    }.getOrNull()
                }
            }
            // The one Awareness screen with a side effect on a read path, and it
            // is worth naming: an excluded id whose record retention has taken is
            // a row that can never be drawn and an undo that can never be reached,
            // so it is dropped the first time a successful read proves it gone.
            LaunchedEffect(excluded) {
                excluded?.let { found -> exclusionStore.pruneSessions(found.map { it.id }.toSet()) }
            }
            ExclusionsScreen(
                // Sorted by the name they render under, so the list has an order
                // a reader can predict rather than a set's iteration order.
                apps = exclusions.apps.sortedBy { labelFor(it) ?: it },
                sessions = excluded.orEmpty(),
                exclusions = exclusions,
                labelFor = { id -> labelFor(id) ?: id },
                iconFor = iconFor,
                onIncludeApp = exclusionStore::includeApp,
                onIncludeSession = exclusionStore::includeSession,
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
                        //
                        // **Equal spans, and that is the whole of this block.**
                        // The two totals are divided by the same seven and
                        // printed side by side, so they have to be measured the
                        // same way. The current period runs from the week's first
                        // 4am to now — six-and-a-fraction days, never seven — so
                        // the previous one is that same length ending where this
                        // one begins, rather than a closed seven days that also
                        // swallows the first day's whole bucket at its far end.
                        // Measured any other way the pair reads low every hour of
                        // every day, which is a direction delivered without an
                        // arrow (ADR 0013).
                        val periodStart = dayStart(first).toInstant().toEpochMilli()
                        val periodEnd = System.currentTimeMillis()
                        val previousStart = dayStart(
                            first.minusDays(AWARENESS_WEEK_DAYS.toLong())
                        ).toInstant().toEpochMilli()
                        AwarenessWeekReading(
                            records = records,
                            signals = intents.signalsSince(from) + focus,
                            foreground = usage.usedBetween(periodStart, periodEnd),
                            previousForeground = usage.usedBetween(
                                previousStart,
                                previousStart + (periodEnd - periodStart),
                            ),
                        )
                    }.getOrNull()
                } ?: value
            }
            val week = remember(reading, usageState, now, exclusions, excludedPackages) {
                reading?.let {
                    resolveAwarenessWeek(
                        records = retainedSessions(it.records, exclusions),
                        signals = it.signals,
                        // Both rates are readings of the device rather than
                        // records Bodha kept, so the window does not govern them
                        // and the period before renders at every tier (#177).
                        foregroundMillis = totalForegroundMillis(it.foreground, excludedPackages),
                        previousForegroundMillis =
                            totalForegroundMillis(it.previousForeground, excludedPackages),
                        usage = usageState,
                        now = now,
                    )
                }
            }
            // The seven days consult the same gate every other view does, even
            // though a rolling seven sits inside the free cap by construction and
            // nothing is ever dropped at today's numbers. A withheld day is
            // **absent** rather than drawn as #176's named-empty row, which would
            // say a day held nothing when it held records this tier cannot render.
            val weekRender = remember(week, window) {
                week?.let { w -> window.days(w.days.map { it.day }) }
            }
            val shownDays = weekRender?.records.orEmpty()
            AwarenessWeekScreen(
                week = week?.let { w -> w.copy(days = w.days.filter { it.day in shownDays }) },
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
                boundary = weekRender?.boundary,
                onBoundary = { boundaryShown = weekRender?.boundary },
            )
        }

        else -> AwarenessScreen(
            today = shownDay,
            sessions = shownSessions,
            day = shown,
            isToday = isToday,
            exclusions = exclusions,
            onPickView = onPickView,
            onOpenSession = { open = it.record.id },
            onSessionActions = { sheets.open(Sheet.AwarenessSessionActions(it.record)) },
            onOpenExclusions = { showExclusions = true },
            boundary = dayRender?.boundary,
            onBoundary = { boundaryShown = dayRender?.boundary },
            onBack = onBack,
        )
    }
    // The tail the one `when` exists for: a dialog and two sheets reachable from
    // every branch, opened by whichever row the reader pressed, and stating the
    // same thing wherever they were opened from (#177, #178).
    boundaryShown?.let {
        ProBoundaryDialog(boundary = it, onDismiss = { boundaryShown = null })
    }
    // The sheets are about a row on this surface, so they leave with the surface
    // — the reason the Library's and Search's do (#132).
    DisposableEffect(Unit) {
        onDispose {
            sheets.showing<Sheet.AwarenessSessionActions>()?.let(sheets::close)
            sheets.showing<Sheet.AwarenessAppActions>()?.let(sheets::close)
        }
    }
    sheets.showing<Sheet.AwarenessSessionActions>()?.let { sheet ->
        // Told to the slot as well as used here, so a session ending over this
        // sheet dismisses it the way its own scrim does (ADR 0011, #134).
        val dismiss = sheets.dismissedBy(sheet) { sheets.close(sheet) }
        AwarenessActionsSheet(
            // The session named the way its row names it: a start and a span.
            title = awarenessSessionLine(sheet.record, LocalBodhaFormats.current.clock),
            onExclude = { dismiss(); exclusionStore.excludeSession(sheet.record.id) },
            onDismiss = dismiss,
        )
    }
    sheets.showing<Sheet.AwarenessAppActions>()?.let { sheet ->
        val dismiss = sheets.dismissedBy(sheet) { sheets.close(sheet) }
        AwarenessActionsSheet(
            title = labelFor(sheet.appId) ?: sheet.appId,
            onExclude = { dismiss(); exclusionStore.excludeApp(sheet.appId) },
            onDismiss = dismiss,
        )
    }
}

private fun LocalDateTime.toInstant(): Instant = atZone(ZoneId.systemDefault()).toInstant()
