package com.bodhalauncher.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bodhalauncher.app.home.ActionsKeyStore
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.GroupStore
import com.bodhalauncher.app.home.IntentionStore
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.home.UsageReader
import com.bodhalauncher.app.capability.CapabilityEducationHost
import com.bodhalauncher.app.capability.rememberCapabilityEducation
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.app.intent.IntentPromptRuntime
import com.bodhalauncher.app.intent.IntentRecordStore
import com.bodhalauncher.app.entitlement.EntitlementStore
import com.bodhalauncher.app.opencheck.BypassClassifier
import com.bodhalauncher.app.opencheck.OpenCheckRuleStore
import com.bodhalauncher.app.opencheck.OpenCheckStateStore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.ui.ActionOptionsDialog
import com.bodhalauncher.app.ui.ActionsKeyHint
import com.bodhalauncher.app.ui.AppPickerDialog
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.EditHomeDialog
import com.bodhalauncher.app.ui.GestureAction
import com.bodhalauncher.app.ui.HomeGestures
import com.bodhalauncher.app.ui.HomeScreen
import com.bodhalauncher.app.ui.IntentPromptSheet
import com.bodhalauncher.app.ui.IntentionEditorDialog
import com.bodhalauncher.app.ui.LocalActionsKeyHint
import com.bodhalauncher.app.ui.escapeIsBack
import com.bodhalauncher.app.ui.OpenCheckSheet
import com.bodhalauncher.app.ui.PlaceholderSurface
import com.bodhalauncher.app.ui.SessionEndSheet
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.EventType
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.HomeInputs
import com.bodhalauncher.engine.Place
import com.bodhalauncher.engine.Surface
import com.bodhalauncher.engine.resolveBack
import com.bodhalauncher.engine.resolveRoot
import com.bodhalauncher.engine.IntentCategory
import com.bodhalauncher.engine.OpenCheckContext
import com.bodhalauncher.engine.OpenCheckDecision
import com.bodhalauncher.engine.OpenCheckEngine
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.TimedSessionEnd
import com.bodhalauncher.engine.dayStart
import com.bodhalauncher.engine.sessionEndPhrase
import com.bodhalauncher.engine.resolveOpenCheckLines
import com.bodhalauncher.engine.resolveHome
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private lateinit var catalog: AppCatalog

    /**
     * Bumped each time the system Home button re-delivers us. The activity is
     * `singleTask`, so pressing Home while a surface is open arrives here rather
     * than recreating anything — which makes this the only place root can be
     * restored, and the reason the press used to leave the Library showing (#132).
     */
    private val homeIntents = mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeIntents.intValue++
    }

    override fun onResume() {
        super.onResume()
        (application as BodhaApp).intentPrompt.onLauncherVisible()
    }

    override fun onPause() {
        super.onPause()
        (application as BodhaApp).intentPrompt.onLauncherHidden()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BodhaApp
        val pinStore = PinStore(this)
        val intentionStore = IntentionStore(this)
        val libraryStore = LibraryStore(this)
        val groupStore = GroupStore(this)
        val openCheckStore = OpenCheckRuleStore(this)
        val entitlementStore = EntitlementStore(this)
        catalog = AppCatalog(this)
        catalog.onAppsRemoved = { ids ->
            ids.forEach { pinStore.unpin(it); pinStore.unhide(it); openCheckStore.remove(it) }
            groupStore.removeApps(ids)
        }
        catalog.startWatching()
        val actionsKeyStore = ActionsKeyStore(this)
        setContent {
            BodhaTheme {
                // One Escape binding for every surface, and one answer to whether
                // a focused row still teaches the actions key (ADR 0022, 0023).
                CompositionLocalProvider(
                    LocalActionsKeyHint provides ActionsKeyHint(
                        shown = !actionsKeyStore.retired.value,
                        onKeyUsed = actionsKeyStore::retire,
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize().escapeIsBack()) {
                        BodhaHost(pinStore, intentionStore, libraryStore, groupStore, openCheckStore, entitlementStore, catalog, app.intentPrompt, app.events, homeIntents.intValue)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        catalog.stopWatching()
        super.onDestroy()
    }
}

/** The label comes from the surface, so renaming one renames what TalkBack announces. */
private fun openSurface(target: Surface, go: (Surface) -> Unit) =
    GestureAction("Open ${target.title}") { go(target) }

private val homeActionSaver = listSaver<HomeAction?, String>(
    save = { it?.let { action -> listOf(action.id, action.label) } ?: emptyList() },
    restore = { if (it.isEmpty()) null else HomeAction(it[0], it[1]) },
)

/**
 * The one host every surface hangs off. It owns where you are, the single opening
 * path, and the sheets that may appear over any surface; each surface owns its own
 * content. Back and root come from the engine (#132), so the rules are asserted in
 * unit tests rather than by driving this.
 */
@Composable
private fun BodhaHost(
    pinStore: PinStore,
    intentionStore: IntentionStore,
    libraryStore: LibraryStore,
    groupStore: GroupStore,
    openCheckStore: OpenCheckRuleStore,
    entitlementStore: EntitlementStore,
    catalog: AppCatalog,
    intentPrompt: IntentPromptRuntime,
    events: EventLogger,
    homeIntents: Int,
) {
    val pinnedIds by pinStore.pinned
    val hidden by pinStore.hidden
    val allApps by catalog.apps
    val intention by intentionStore.intention
    val sessionIntent by intentPrompt.sessionIntent
    val pinned = remember(pinnedIds, allApps) { catalog.resolve(pinnedIds) }
    var pickerOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<HomeAction?>(null) }
    var editingIntention by remember { mutableStateOf(false) }
    var editingHome by remember { mutableStateOf(false) }
    // Focus has no producer yet; #9's slice supplies it rather than re-deciding the rule.
    val focusRunning = false
    // Every capability Bodha asks for, from any surface, enters here (#157).
    val education = rememberCapabilityEducation(events)
    var place by remember { mutableStateOf(Place(resolveRoot(focusRunning))) }
    // The system Home button lands on root from wherever you were, and takes the
    // education sheet the departed surface opened with it — it belongs to that ask.
    LaunchedEffect(homeIntents) {
        place = Place(resolveRoot(focusRunning))
        education.close()
    }
    val context = LocalContext.current
    val openCheckStateStore = remember { OpenCheckStateStore(context) }
    // Snapshot-restored so a pending timed session survives Bodha being killed (#75).
    val openCheck = remember { OpenCheckEngine(openCheckStateStore.load()) }
    val syncOpenCheck = { openCheckStateStore.save(openCheck.snapshot()) }
    val usage = remember { UsageReader(context) }
    val bypass = remember { BypassClassifier(context) }
    val records = remember { IntentRecordStore(context) }
    // Saveable so an in-flight check survives rotation; otherwise a displayed
    // check would vanish with no outcome logged, skewing the return rate (#25).
    var checkFor by rememberSaveable(stateSaver = homeActionSaver) { mutableStateOf<HomeAction?>(null) }
    var sessionEndDue by remember { mutableStateOf<TimedSessionEnd?>(null) }
    // The session-end moment appears only where policy allows Bodha on screen:
    // if the launcher was elsewhere at expiry, it shows on the next visibility,
    // and the phrase (computed at render) owns the time that actually elapsed.
    // No overlay, no forcing another app away — never claimed (#75).
    LaunchedEffect(Unit) {
        while (true) {
            if (sessionEndDue == null) openCheck.advanceTo(Instant.now())?.let { sessionEndDue = it }
            delay(1_000)
        }
    }
    // At most one pause per opening (#77): the prompt engine is told while a
    // check is on screen, and a launch while the prompt shows skips the check.
    SideEffect { intentPrompt.openCheckShowing = checkFor != null || sessionEndDue != null }
    // The single opening path (#8): every surface's launch flows through here.
    val openApp: (HomeAction) -> Unit = { action ->
        val rule = openCheckStore.ruleFor(action.id)
        val localNow = LocalDateTime.now()
        val launchContext = OpenCheckContext(
            // Sampled only when the rule needs it, read on demand, never stored (ADR 0009).
            usedTodayMillis = if (rule?.mode == OpenCheckMode.DailyThreshold) {
                val dayStartMillis = dayStart(localNow).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                catalog.primaryPackage(action.id)?.let { usage.usedSince(dayStartMillis)?.get(it) }
            } else null,
            minuteOfDay = localNow.hour * 60 + localNow.minute,
            // Bypass is classification only, never a lock on an explicitly ruled app (#77).
            bypass = rule == null && bypass.bypasses(catalog.primaryPackage(action.id)),
        )
        val decision =
            if (intentPrompt.promptDue.value != null) OpenCheckDecision.Proceed
            else openCheck.onLaunchAttempt(action.id, rule, Instant.now(), launchContext)
        when (decision) {
            is OpenCheckDecision.Proceed -> {
                events.log(EventType.AppLaunched)
                catalog.launch(action)
            }
            is OpenCheckDecision.ShowCheck -> {
                // Type and timestamp only — the event never carries the app (#25).
                if (decision.repeatedOpen) events.log(EventType.RepeatedOpenDetected)
                events.log(EventType.OpenCheckDisplayed)
                checkFor = action
            }
        }
        syncOpenCheck()
    }

    sessionEndDue?.let { due ->
        // Phrased at render time so a moment shown late owns the elapsed truth.
        val overBy = (System.currentTimeMillis() - due.timedSession.endsAt.toEpochMilli()).coerceAtLeast(0)
        val settle = { sessionEndDue = null; syncOpenCheck() }
        // Reopening resolves through the catalog; an uninstalled app just settles.
        val reopen = { catalog.resolve(listOf(due.timedSession.appId)).firstOrNull()?.let(openApp) }
        SessionEndSheet(
            phrase = sessionEndPhrase(due.timedSession.plannedMinutes, overBy),
            onClose = { openCheck.onSessionEndClose(); settle() },
            onAddFive = {
                openCheck.onSessionEndAddFive(Instant.now())
                settle()
                reopen()
            },
            onContinue = {
                openCheck.onSessionEndContinue(Instant.now())
                settle()
                reopen()
            },
            // Nothing fights the user closing the moment: dismissing is closing.
            onDismiss = { openCheck.onSessionEndClose(); settle() },
        )
    }

    checkFor?.let { app ->
        val usageGranted = education.granted(Capability.UsageAccess)
        // Read at check time, never stored (ADR 0009). The 4am boundary is engine
        // math. Work-profile ids read as "no data" — see AppCatalog.primaryPackage.
        val lines = remember(app.id, education.resumeTick) {
            val pkg = catalog.primaryPackage(app.id)
            val dayStartMillis = dayStart(LocalDateTime.now())
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            resolveOpenCheckLines(
                lastOpenedEpochMillis = pkg?.let { usage.lastUsed()?.get(it) },
                usedTodayMillis = pkg?.let { usage.usedSince(dayStartMillis)?.get(it) },
                nowEpochMillis = System.currentTimeMillis(),
            )
        }
        OpenCheckSheet(
            app = app,
            icon = remember(app.id, catalog.version.intValue) { catalog.icon(app.id) },
            lines = lines,
            // The note is an explicit ask — education precedes any system screen (#18).
            onContextNoteTap = if (usageGranted) null else {
                { education.ask(Capability.UsageAccess, EducationEntry.UserRequest) }
            },
            onOpen = { typed ->
                typed?.let(records::appendOpenCheckIntention)
                events.log(EventType.OpenCheckProceeded)
                openCheck.onProceeded(app.id, Instant.now())
                checkFor = null
                // Back through the same path; the grant it holds covers this launch.
                openApp(app)
            },
            onOpenFor = { minutes, typed ->
                typed?.let(records::appendOpenCheckIntention)
                events.log(EventType.OpenCheckProceeded)
                openCheck.onProceededFor(app.id, Instant.now(), minutes)
                checkFor = null
                openApp(app)
            },
            onDismiss = {
                events.log(EventType.OpenCheckTurnedBack)
                openCheck.onTurnedBack(app.id)
                checkFor = null
                syncOpenCheck()
            },
        )
    }

    // Above every surface, because the Open Check sheet and the Library both ask
    // from it; the surfaces themselves only ever call `education.ask` (#157).
    CapabilityEducationHost(education)

    // Always enabled, and a no-op on root. An enabled callback that does nothing is
    // what keeps back from finishing the activity and having the system relaunch it,
    // which would read as a flicker on the surface you are most often standing on.
    val back: () -> Unit = { resolveBack(place, focusRunning)?.let { place = it } }
    BackHandler(onBack = back)

    when (place.surface) {
        // Falls through to Home's own content below.
        Surface.Home -> Unit
        Surface.Library -> {
            LibrarySurface(
                pinStore = pinStore,
                libraryStore = libraryStore,
                groupStore = groupStore,
                openCheckStore = openCheckStore,
                entitlementStore = entitlementStore,
                catalog = catalog,
                usage = usage,
                bypass = bypass,
                education = education,
                openApp = openApp,
                onPause = { place = Place(Surface.Focus) },
                onBack = back,
            )
            return
        }
        else -> {
            PlaceholderSurface(title = place.surface.title, onBack = back)
            return
        }
    }

    // Once per arrival on Home, not per recomposition (#25).
    LaunchedEffect(Unit) { events.log(EventType.HomeRendered) }

    // Ticks each minute so the intention drops at the 4am boundary (ADR 0003)
    // even when Home sits on screen with nothing else changing.
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay((60 - LocalDateTime.now().second) * 1000L)
            value = LocalDateTime.now()
        }
    }

    // Remaining inputs fill in as their features ship (suggestions #6, digest #10, …).
    val state = resolveHome(
        HomeInputs(
            dailyIntention = intention?.textOn(now),
            pinned = pinned,
            hidden = hidden,
            sessionIntent = sessionIntent,
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            state = state,
            onAction = openApp,
            onActionLongPress = { optionsFor = it },
            onAddAction = { pickerOpen = true },
            onEditIntention = { editingIntention = true },
            iconFor = { catalog.icon(it.id) },
            iconKey = catalog.version.intValue,
            // Labels name the destination, so they stay true when ADR 0011's
            // reassignment lands and a swipe points somewhere else.
            gestures = HomeGestures(
                swipeDown = openSurface(Surface.Search) { place = Place(it) },
                swipeUp = openSurface(Surface.Library) { place = Place(it) },
                swipeLeft = openSurface(Surface.Awareness) { place = Place(it) },
                swipeRight = openSurface(Surface.Today) { place = Place(it) },
                // Lock mechanism is settled in the permissions spec (#18); stub until
                // then, so it stays unannounced rather than offering an action that
                // only reports its own absence.
                doubleTapEmpty = GestureAction(label = null) {
                    Toast.makeText(context, "Lock — mechanism pending", Toast.LENGTH_SHORT).show()
                },
                longPressEmpty = GestureAction("Edit layout") { editingHome = true },
            ),
            onSearch = { place = Place(Surface.Search) },
        )

        val due by intentPrompt.promptDue
        if (due != null) {
            LaunchedEffect(due) { events.log(EventType.IntentPromptShown) }
            IntentPromptSheet(
                onSelect = { category, text ->
                    events.log(EventType.IntentPromptAnswered)
                    intentPrompt.select(category, text)
                    // The intent flows straight into the action.
                    if (category == IntentCategory.FindSomething) place = Place(Surface.Search)
                },
                onDismiss = {
                    events.log(EventType.IntentPromptDismissed)
                    intentPrompt.dismiss()
                },
            )
        }
    }

    if (pickerOpen) {
        AppPickerDialog(
            apps = allApps.filter { it.id !in pinnedIds },
            onPick = { pinStore.pin(it.id); pickerOpen = false },
            onDismiss = { pickerOpen = false },
        )
    }
    if (editingHome) {
        EditHomeDialog(onAddPin = { pickerOpen = true }, onDismiss = { editingHome = false })
    }
    if (editingIntention) {
        IntentionEditorDialog(
            current = intention?.textOn(now),
            onSave = { intentionStore.set(it, LocalDateTime.now()) },
            onClear = intentionStore::clear,
            onDismiss = { editingIntention = false },
        )
    }
    optionsFor?.let { action ->
        ActionOptionsDialog(
            action = action,
            isPinned = action.id in pinnedIds,
            onPin = { pinStore.pin(it.id) },
            onUnpin = { pinStore.unpin(it.id) },
            onHide = { pinStore.hide(it.id) },
            onDismiss = { optionsFor = null },
        )
    }
}
