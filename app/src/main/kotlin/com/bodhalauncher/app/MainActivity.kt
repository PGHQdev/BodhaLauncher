package com.bodhalauncher.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.GroupStore
import com.bodhalauncher.app.home.IntentionStore
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.home.UsageReader
import com.bodhalauncher.app.capability.CapabilityEdge
import com.bodhalauncher.app.capability.EducationStateStore
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
import com.bodhalauncher.app.ui.AppActionsSheet
import com.bodhalauncher.app.ui.AppPickerDialog
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.EditHomeDialog
import com.bodhalauncher.app.ui.GroupPickerDialog
import com.bodhalauncher.app.ui.GestureAction
import com.bodhalauncher.app.ui.HomeGestures
import com.bodhalauncher.app.ui.HomeScreen
import com.bodhalauncher.app.ui.IntentPromptSheet
import com.bodhalauncher.app.ui.IntentionEditorDialog
import com.bodhalauncher.app.ui.EducationSheet
import com.bodhalauncher.app.ui.LibraryScreen
import com.bodhalauncher.app.ui.OpenCheckRuleDialog
import com.bodhalauncher.app.ui.OpenCheckSheet
import com.bodhalauncher.app.ui.ProBoundaryDialog
import com.bodhalauncher.app.ui.PlaceholderSurface
import com.bodhalauncher.app.ui.SessionEndSheet
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.CapabilityResolution
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.EducationScreen
import com.bodhalauncher.engine.EventType
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.HomeInputs
import com.bodhalauncher.engine.IntentCategory
import com.bodhalauncher.engine.LibraryInputs
import com.bodhalauncher.engine.GateDecision
import com.bodhalauncher.engine.OpenCheckContext
import com.bodhalauncher.engine.OpenCheckDecision
import com.bodhalauncher.engine.OpenCheckEngine
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.ProBoundary
import com.bodhalauncher.engine.TimedSessionEnd
import com.bodhalauncher.engine.dayStart
import com.bodhalauncher.engine.resolveCapability
import com.bodhalauncher.engine.sessionEndPhrase
import com.bodhalauncher.engine.resolveOpenCheckLines
import com.bodhalauncher.engine.resolveOpenCheckRuleWrite
import com.bodhalauncher.engine.resolveHome
import com.bodhalauncher.engine.resolveLibrary
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private lateinit var catalog: AppCatalog

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
        setContent {
            BodhaTheme {
                HomeRoot(pinStore, intentionStore, libraryStore, groupStore, openCheckStore, entitlementStore, catalog, app.intentPrompt, app.events)
            }
        }
    }

    override fun onDestroy() {
        catalog.stopWatching()
        super.onDestroy()
    }
}

/** The surfaces Home's swipes fan out to; all but Home are placeholders for now. */
private enum class HomeSurface(val title: String) {
    Home("Home"),
    Search("Search"),
    Library("App Library"),
    Awareness("Awareness"),
    Today("Today"),
    Focus("Focus"),
}

private val homeActionSaver = listSaver<HomeAction?, String>(
    save = { it?.let { action -> listOf(action.id, action.label) } ?: emptyList() },
    restore = { if (it.isEmpty()) null else HomeAction(it[0], it[1]) },
)

@Composable
private fun HomeRoot(
    pinStore: PinStore,
    intentionStore: IntentionStore,
    libraryStore: LibraryStore,
    groupStore: GroupStore,
    openCheckStore: OpenCheckRuleStore,
    entitlementStore: EntitlementStore,
    catalog: AppCatalog,
    intentPrompt: IntentPromptRuntime,
    events: EventLogger,
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
    var surface by remember { mutableStateOf(HomeSurface.Home) }
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
        val capabilityEdge = remember { CapabilityEdge(context) }
        val educationStore = remember { EducationStateStore(context) }
        var educationFor by remember { mutableStateOf<EducationScreen?>(null) }
        // Re-reads on resume so a grant made from this sheet's education path
        // shows up on return from system settings (#18).
        val lifecycleOwner = LocalLifecycleOwner.current
        var usageTick by remember { mutableIntStateOf(0) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) usageTick++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val usageGranted = remember(app.id, usageTick) { capabilityEdge.granted(Capability.UsageAccess) }
        // Read at check time, never stored (ADR 0009). The 4am boundary is engine
        // math. Work-profile ids read as "no data" — see AppCatalog.primaryPackage.
        val lines = remember(app.id, usageTick) {
            val pkg = catalog.primaryPackage(app.id)
            val dayStartMillis = dayStart(LocalDateTime.now())
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            resolveOpenCheckLines(
                lastOpenedEpochMillis = pkg?.let { usage.lastUsed()?.get(it) },
                usedTodayMillis = pkg?.let { usage.usedSince(dayStartMillis)?.get(it) },
                nowEpochMillis = System.currentTimeMillis(),
            )
        }
        // The grant becomes observable only on return from system settings (#18); log it once (#25).
        LaunchedEffect(usageGranted) {
            if (usageGranted &&
                educationStore.shown(Capability.UsageAccess) &&
                !educationStore.grantLogged(Capability.UsageAccess)
            ) {
                events.log(EventType.PermissionEnabled)
                educationStore.markGrantLogged(Capability.UsageAccess)
            }
        }
        OpenCheckSheet(
            app = app,
            icon = remember(app.id, catalog.version.intValue) { catalog.icon(app.id) },
            lines = lines,
            // The note is an explicit ask — education precedes any system screen (#18).
            onContextNoteTap = if (usageGranted) null else {
                {
                    val resolution = resolveCapability(
                        capability = Capability.UsageAccess,
                        granted = false,
                        educationShown = educationStore.shown(Capability.UsageAccess),
                        entry = EducationEntry.UserRequest,
                    )
                    if (resolution is CapabilityResolution.Educate) {
                        educationFor = resolution.screen
                        educationStore.markShown(Capability.UsageAccess)
                    }
                }
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
        educationFor?.let { screen ->
            EducationSheet(
                screen = screen,
                // The grant isn't known until observed on return; only the skip is terminal here.
                onContinue = {
                    capabilityEdge.openSystemScreen(screen.capability)
                    educationFor = null
                },
                onDismiss = {
                    events.log(EventType.PermissionSkipped)
                    educationFor = null
                },
            )
        }
    }

    if (surface != HomeSurface.Home) {
        val back = { surface = HomeSurface.Home }
        BackHandler(onBack = back)
        if (surface == HomeSurface.Library) {
            var query by remember { mutableStateOf("") }
            var actionsFor by remember { mutableStateOf<HomeAction?>(null) }
            var groupsFor by remember { mutableStateOf<HomeAction?>(null) }
            var openCheckFor by remember { mutableStateOf<HomeAction?>(null) }
            var boundary by remember { mutableStateOf<ProBoundary?>(null) }
            var educationFor by remember { mutableStateOf<EducationScreen?>(null) }
            val capabilityEdge = remember { CapabilityEdge(context) }
            val educationStore = remember { EducationStateStore(context) }
            val hiddenSearchable by libraryStore.hiddenSearchable
            val layout by libraryStore.layout
            val categories by catalog.categories
            val groups by groupStore.groups
            // Re-reads on every resume, so granting access in settings shows up on return;
            // read on demand and never stored (ADR 0009).
            val lifecycleOwner = LocalLifecycleOwner.current
            var usageTick by remember { mutableIntStateOf(0) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) usageTick++
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val lastUsed = remember(allApps, usageTick) { usage.lastUsed() }
            // The grant becomes observable only on return from system settings (#18); log it once (#25).
            LaunchedEffect(lastUsed != null) {
                if (lastUsed != null &&
                    educationStore.shown(Capability.UsageAccess) &&
                    !educationStore.grantLogged(Capability.UsageAccess)
                ) {
                    events.log(EventType.PermissionEnabled)
                    educationStore.markGrantLogged(Capability.UsageAccess)
                }
            }
            LibraryScreen(
                state = resolveLibrary(
                    LibraryInputs(
                        apps = allApps,
                        layout = layout,
                        categories = categories,
                        groups = groups,
                        query = query,
                        hidden = hidden,
                        hiddenSearchable = hiddenSearchable,
                        lastUsed = lastUsed,
                        now = System.currentTimeMillis(),
                    )
                ),
                query = query,
                onQueryChange = { query = it },
                onLayoutChange = libraryStore::setLayout,
                // The layout note is an explicit ask — education precedes any system screen (#18).
                onLayoutNoteTap = {
                    val resolution = resolveCapability(
                        capability = Capability.UsageAccess,
                        granted = capabilityEdge.granted(Capability.UsageAccess),
                        educationShown = educationStore.shown(Capability.UsageAccess),
                        entry = EducationEntry.UserRequest,
                    )
                    if (resolution is CapabilityResolution.Educate) {
                        educationFor = resolution.screen
                        educationStore.markShown(Capability.UsageAccess)
                    }
                },
                iconFor = { catalog.icon(it.id) },
                iconKey = catalog.version.intValue,
                onOpen = openApp,
                onLongPress = { actionsFor = it },
                onPin = { pinStore.pin(it.id) },
                onHide = { pinStore.hide(it.id) },
                onUnhide = { pinStore.unhide(it.id) },
                hiddenSearchable = hiddenSearchable,
                onHiddenSearchableChange = libraryStore::setHiddenSearchable,
                groupNames = groups.map { it.name },
                onCreateGroup = groupStore::create,
                onRenameGroup = groupStore::rename,
                onDeleteGroup = groupStore::delete,
                onBack = back,
            )
            educationFor?.let { screen ->
                EducationSheet(
                    screen = screen,
                    // The grant isn't known until observed on return; only the skip is terminal here.
                    onContinue = {
                        capabilityEdge.openSystemScreen(screen.capability)
                        educationFor = null
                    },
                    onDismiss = {
                        events.log(EventType.PermissionSkipped)
                        educationFor = null
                    },
                )
            }
            groupsFor?.let { app ->
                GroupPickerDialog(
                    app = app,
                    groups = groups,
                    onToggle = { groupStore.toggle(it.name, app.id) },
                    onDismiss = { groupsFor = null },
                )
            }
            openCheckFor?.let { app ->
                val openCheckRules by openCheckStore.rules
                OpenCheckRuleDialog(
                    app = app,
                    current = openCheckRules[app.id],
                    onSave = { rule ->
                        val decision = resolveOpenCheckRuleWrite(
                            entitlementStore.snapshot.value,
                            existingRules = openCheckRules.size,
                            creating = app.id !in openCheckRules,
                        )
                        when (decision) {
                            GateDecision.Allowed -> {
                                openCheckStore.set(app.id, rule)
                                // The threshold trigger wants usage access; the rule is
                                // saved either way and stays inert until granted (#73).
                                if (rule.mode == OpenCheckMode.DailyThreshold &&
                                    !capabilityEdge.granted(Capability.UsageAccess)
                                ) {
                                    val resolution = resolveCapability(
                                        capability = Capability.UsageAccess,
                                        granted = false,
                                        educationShown = educationStore.shown(Capability.UsageAccess),
                                        entry = EducationEntry.UserRequest,
                                    )
                                    if (resolution is CapabilityResolution.Educate) {
                                        educationFor = resolution.screen
                                        educationStore.markShown(Capability.UsageAccess)
                                    }
                                }
                            }
                            is GateDecision.Capped -> boundary = decision.boundary
                            is GateDecision.Locked -> boundary = decision.boundary
                        }
                    },
                    onRemove = { openCheckStore.remove(app.id) },
                    onDismiss = { openCheckFor = null },
                )
            }
            boundary?.let { ProBoundaryDialog(boundary = it, onDismiss = { boundary = null }) }
            actionsFor?.let { app ->
                val dismiss = { actionsFor = null }
                val openCheckRules by openCheckStore.rules
                AppActionsSheet(
                    app = app,
                    shortcuts = remember(app.id) { catalog.shortcuts(app.id) },
                    isPinned = app.id in pinnedIds,
                    isHidden = app.id in hidden,
                    openCheckMode = openCheckRules[app.id]?.mode,
                    // Emergency/utility apps don't offer friction unless already ruled (#77).
                    openCheckOffered = app.id in openCheckRules ||
                        !bypass.bypasses(catalog.primaryPackage(app.id)),
                    onOpen = { dismiss(); openApp(app) },
                    onShortcut = { dismiss(); catalog.launchShortcut(it) },
                    onPin = { dismiss(); pinStore.pin(app.id) },
                    onUnpin = { dismiss(); pinStore.unpin(app.id) },
                    onHide = { dismiss(); pinStore.hide(app.id) },
                    onUnhide = { dismiss(); pinStore.unhide(app.id) },
                    onGroups = { dismiss(); groupsFor = app },
                    onPause = { dismiss(); surface = HomeSurface.Focus },
                    onOpenCheck = { dismiss(); openCheckFor = app },
                    onAppInfo = { dismiss(); catalog.openAppInfo(app.id) },
                    onDismiss = dismiss,
                )
            }
        } else {
            PlaceholderSurface(title = surface.title, onBack = back)
        }
        return
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
            // Labels name the destination, so they stay true when ADR 0011's
            // reassignment lands and a swipe points somewhere else.
            gestures = HomeGestures(
                swipeDown = GestureAction("Open Search") { surface = HomeSurface.Search },
                swipeUp = GestureAction("Open App Library") { surface = HomeSurface.Library },
                swipeLeft = GestureAction("Open Awareness") { surface = HomeSurface.Awareness },
                swipeRight = GestureAction("Open Today") { surface = HomeSurface.Today },
                // Lock mechanism is settled in the permissions spec (#18); stub until then.
                doubleTapEmpty = GestureAction("Lock screen") {
                    Toast.makeText(context, "Lock — mechanism pending", Toast.LENGTH_SHORT).show()
                },
                longPressEmpty = GestureAction("Edit layout") { editingHome = true },
            ),
            onSearch = { surface = HomeSurface.Search },
        )

        val due by intentPrompt.promptDue
        if (due != null) {
            LaunchedEffect(due) { events.log(EventType.IntentPromptShown) }
            IntentPromptSheet(
                onSelect = { category, text ->
                    events.log(EventType.IntentPromptAnswered)
                    intentPrompt.select(category, text)
                    // The intent flows straight into the action.
                    if (category == IntentCategory.FindSomething) surface = HomeSurface.Search
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
