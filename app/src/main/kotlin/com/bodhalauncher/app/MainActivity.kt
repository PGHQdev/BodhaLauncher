package com.bodhalauncher.app

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bodhalauncher.app.home.ActionsKeyStore
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.GroupStore
import com.bodhalauncher.app.home.IntentionStore
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.ModeStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.home.SearchDefaultStore
import com.bodhalauncher.app.home.UsageReader
import com.bodhalauncher.app.capability.CapabilityEducationHost
import com.bodhalauncher.app.capability.rememberCapabilityEducation
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.app.intent.IntentPromptRuntime
import com.bodhalauncher.app.contacts.ContactsReader
import com.bodhalauncher.app.inbox.MuteStore
import com.bodhalauncher.app.onboarding.OnboardingStore
import com.bodhalauncher.app.onboarding.commitEssentials
import com.bodhalauncher.app.onboarding.commitFriction
import com.bodhalauncher.app.onboarding.commitFirstIntention
import com.bodhalauncher.engine.OnboardingStep
import com.bodhalauncher.app.intent.IntentRecordStore
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.app.session.applySessionBoundary
import com.bodhalauncher.app.today.CalendarReader
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.entitlement.EntitlementStore
import com.bodhalauncher.app.focus.FocusStore
import com.bodhalauncher.app.opencheck.BypassClassifier
import com.bodhalauncher.app.opencheck.OpenCheckRuleStore
import com.bodhalauncher.app.opencheck.OpenCheckStateStore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.ui.ActionOptionsDialog
import com.bodhalauncher.app.ui.ActionsKeyHint
import com.bodhalauncher.app.ui.AppPickerDialog
import com.bodhalauncher.app.ui.BodhaFormats
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.EditHomeDialog
import com.bodhalauncher.app.ui.isDark
import com.bodhalauncher.app.ui.ModeManageDialog
import com.bodhalauncher.app.ui.minuteNow
import com.bodhalauncher.app.ui.ModeSelectorDialog
import com.bodhalauncher.app.ui.GestureAction
import com.bodhalauncher.app.ui.HomeGestures
import com.bodhalauncher.app.ui.HomeScreen
import com.bodhalauncher.app.ui.IntentPromptSheet
import com.bodhalauncher.app.ui.LocalActionsKeyHint
import com.bodhalauncher.app.ui.OnboardingFlow
import com.bodhalauncher.app.ui.escapeIsBack
import com.bodhalauncher.app.ui.FocusEndSheet
import com.bodhalauncher.app.ui.OpenCheckSheet
import com.bodhalauncher.app.ui.PlaceholderSurface
import com.bodhalauncher.app.ui.SessionEndSheet
import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.rememberSheetSlot
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.EventType
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.HomeInputs
import com.bodhalauncher.engine.Place
import com.bodhalauncher.engine.SettingsRowId
import com.bodhalauncher.engine.Surface
import com.bodhalauncher.engine.Transition
import com.bodhalauncher.engine.resolveBack
import com.bodhalauncher.engine.resolveRoot
import com.bodhalauncher.engine.IntentCategory
import com.bodhalauncher.engine.OpenCheckContext
import com.bodhalauncher.engine.OpenCheckDecision
import com.bodhalauncher.engine.OpenCheckEngine
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.dayStart
import com.bodhalauncher.engine.focusCheckDue
import com.bodhalauncher.engine.focusDurationLine
import com.bodhalauncher.engine.focusReachLine
import com.bodhalauncher.engine.sessionEndPhrase
import com.bodhalauncher.engine.resolveOpenCheckLines
import com.bodhalauncher.engine.resolveHome
import com.bodhalauncher.engine.resolveArrangement
import com.bodhalauncher.engine.resolveOnboardingStep
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private lateinit var catalog: AppCatalog
    private lateinit var onboardingStore: OnboardingStore

    /**
     * Which step the flow shows, locally: forward is store-driven (advance moves
     * the marker), but back must revisit a passed step, which the monotonic
     * marker cannot express. Null means the flow has nothing to show. Recreation
     * re-resolves from the store, so a killed process resumes at the first step
     * not passed, with everything written intact (ADR 0018).
     */
    private val shownStep = mutableStateOf<OnboardingStep?>(null)

    /**
     * Whether Bodha holds the home role; re-read on resume (#136), so acquiring
     * or losing it later flips Home's line without a relaunch.
     */
    private val homeRoleHeld = mutableStateOf(true)

    /**
     * The one grant Bodha asks for (ADR 0018). Granted or declined — including
     * an OEM that routes to a settings screen instead of a dialog — the result
     * lands here and the flow completes. Declining is an answer: no retry, no
     * re-prompt, and the route back is Settings' home-role row (#140).
     */
    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            settleRoleRequest()
        }

    /**
     * Granted, declined, or nothing left to ask: the role is re-read, and the
     * flow advances only if the flow is what asked.
     *
     * Which asked is read from [shownStep] rather than remembered in a field,
     * because a field would be gone by the time the result arrives after a
     * process death mid-dialog, while `onCreate` re-resolves the step from the
     * store — so the flow still advances and Settings still does not.
     */
    private fun settleRoleRequest() {
        homeRoleHeld.value = readHomeRole()
        if (shownStep.value == OnboardingStep.BecomeHome) advanceStep(OnboardingStep.BecomeHome)
    }

    /** Every advance — answer or skip — passes through here, from any step. */
    private fun advanceStep(step: OnboardingStep) {
        // Type and timestamp only — never step identity (ADR 0009).
        (application as BodhaApp).events.log(EventType.OnboardingStepCompleted)
        onboardingStore.advance(step)
        shownStep.value = OnboardingStep.entries.getOrNull(step.ordinal + 1)
    }

    private fun readHomeRole(): Boolean =
        getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_HOME) == true

    private fun requestHomeRole() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            // Already held, or no request to make: settled without a dialog.
            settleRoleRequest()
        }
    }


    /**
     * Bumped each time the system Home button re-delivers us. The activity is
     * `singleTask`, so pressing Home while a surface is open arrives here rather
     * than recreating anything — which makes this the only place root can be
     * restored, and the reason the press used to leave the Library showing (#132).
     */
    private val homeIntents = mutableIntStateOf(0)

    /**
     * Whether Bodha is actually on screen. The session-end moment reads it (#75):
     * opened behind another app the sheet would be dismissed unseen by the phone
     * session ending under it (ADR 0011, #134), and the moment lost for good.
     */
    private val visible = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeIntents.intValue++
    }

    override fun onResume() {
        super.onResume()
        visible.value = true
        homeRoleHeld.value = readHomeRole()
        (application as BodhaApp).intentPrompt.onLauncherVisible()
    }

    override fun onPause() {
        super.onPause()
        visible.value = false
        (application as BodhaApp).intentPrompt.onLauncherHidden()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BodhaApp
        val pinStore = PinStore(this)
        val modeStore = ModeStore(this, pinStore)
        // The switch and the schedules both survive process death: resolved
        // before anything composes, against the clock as it is now.
        pinStore.setActive(
            resolveArrangement(modeStore.modes.value, modeStore.switch.value, LocalDateTime.now())
                ?: PinStore.DEFAULT_ARRANGEMENT
        )
        val intentionStore = IntentionStore(this)
        val libraryStore = LibraryStore(this)
        val defaultStore = SearchDefaultStore(this)
        val groupStore = GroupStore(this)
        val openCheckStore = OpenCheckRuleStore(this)
        val entitlementStore = EntitlementStore(this)
        // Read before anything composes, so the first frame is already in the
        // chosen theme — there is no previous palette for a return to Home to
        // flash back to (#141).
        val appearanceStore = AppearanceStore(this)
        catalog = AppCatalog(this)
        catalog.onAppsRemoved = { ids ->
            // Every arrangement's pins, not only the active one's (#155).
            pinStore.removeApps(ids)
            ids.forEach { pinStore.unhide(it); openCheckStore.remove(it) }
            groupStore.removeApps(ids)
        }
        catalog.startWatching()
        val actionsKeyStore = ActionsKeyStore(this)
        onboardingStore = OnboardingStore(this)
        // Before any surface composes, something decides between the flow and
        // Home (#135, ADR 0018): a tested reducer over the completion flag and
        // the progress marker, and nothing else.
        shownStep.value =
            resolveOnboardingStep(onboardingStore.complete.value, onboardingStore.furthestPassed.intValue)
        setContent {
            BodhaTheme(
                darkTheme = appearanceStore.theme.value.isDark(),
                formats = BodhaFormats(
                    clock = appearanceStore.clock.value,
                    date = appearanceStore.date.value,
                ),
            ) {
                val onboardingStep = shownStep.value
                if (onboardingStep != null) {
                    OnboardingFlow(
                        step = onboardingStep,
                        apps = remember(catalog.apps.value) {
                            catalog.apps.value.sortedBy { it.label.lowercase() }
                        },
                        // A revisited picker shows what it already committed (its
                        // commit reconciles), so back never lands on a lying screen.
                        pinnedIds = pinStore.pinned.value,
                        ruledIds = openCheckStore.rules.value.keys,
                        // Back steps to the previous step; on the first it leaves the app.
                        onBack = {
                            if (onboardingStep.ordinal == 0) finish()
                            else shownStep.value = OnboardingStep.entries[onboardingStep.ordinal - 1]
                        },
                        onSkip = ::advanceStep,
                        onContinuePromise = { advanceStep(OnboardingStep.Promise) },
                        onEssentials = { picks ->
                            commitEssentials(pinStore, picks)
                            advanceStep(OnboardingStep.Essentials)
                        },
                        onFriction = { picks ->
                            commitFriction(openCheckStore, picks)
                            advanceStep(OnboardingStep.Friction)
                        },
                        onIntention = { text ->
                            commitFirstIntention(intentionStore, text, LocalDateTime.now())
                            advanceStep(OnboardingStep.FirstIntention)
                        },
                        onRequestHomeRole = ::requestHomeRole,
                    )
                    return@BodhaTheme
                }
                // The marker passed the last built step: the flow resolved, the
                // flag is written, and the radial model begins here (ADR 0018).
                if (!onboardingStore.complete.value) SideEffect { onboardingStore.finish() }
                // One Escape binding for every surface, and one answer to whether
                // a focused row still teaches the actions key (ADR 0022, 0023).
                CompositionLocalProvider(
                    LocalActionsKeyHint provides ActionsKeyHint(
                        shown = !actionsKeyStore.retired.value,
                        onKeyUsed = actionsKeyStore::retire,
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize().escapeIsBack()) {
                        BodhaHost(pinStore, modeStore, intentionStore, libraryStore, defaultStore, groupStore, openCheckStore, entitlementStore, appearanceStore, catalog, app.sessions, app.intentPrompt, app.events, homeIntents.intValue, visible.value, homeRoleHeld.value, ::requestHomeRole)
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
/**
 * The surfaces the `when` below renders as themselves rather than as a
 * placeholder — what Search may offer by name (#189). Search is left out: a row
 * that navigates to where you already stand goes nowhere. Grows as the
 * placeholder arms of the `when` are replaced.
 */
private val BUILT_SURFACES = listOf(
    Surface.Home, Surface.Library, Surface.Awareness, Surface.Today, Surface.Inbox,
    Surface.Focus, Surface.Settings,
)

private fun openSurface(target: Surface, go: (Surface) -> Unit) =
    GestureAction("Open ${target.title}") { go(target) }

/**
 * The one host every surface hangs off. It owns where you are, the single opening
 * path, and the sheets that may appear over any surface; each surface owns its own
 * content. Back and root come from the engine (#132), so the rules are asserted in
 * unit tests rather than by driving this.
 */
@Composable
private fun BodhaHost(
    pinStore: PinStore,
    modeStore: ModeStore,
    intentionStore: IntentionStore,
    libraryStore: LibraryStore,
    defaultStore: SearchDefaultStore,
    groupStore: GroupStore,
    openCheckStore: OpenCheckRuleStore,
    entitlementStore: EntitlementStore,
    appearanceStore: AppearanceStore,
    catalog: AppCatalog,
    sessions: SessionRuntime,
    intentPrompt: IntentPromptRuntime,
    events: EventLogger,
    homeIntents: Int,
    launcherVisible: Boolean,
    homeRoleHeld: Boolean,
    requestHomeRole: () -> Unit,
) {
    val pinnedIds by pinStore.pinned
    val hidden by pinStore.hidden
    val allApps by catalog.apps
    val intention by intentionStore.intention
    val sessionIntent by intentPrompt.sessionIntent
    val pinned = remember(pinnedIds, allApps) { catalog.resolve(pinnedIds) }
    var pickerOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<HomeAction?>(null) }
    var editingHome by remember { mutableStateOf(false) }
    var modeSelectorOpen by remember { mutableStateOf(false) }
    var modeManageOpen by remember { mutableStateOf(false) }
    // Ticks each minute, so the intention drops at the 4am boundary (ADR 0003)
    // and a mode's window opens (#156) without a relaunch — one producer, above
    // the surfaces, because a schedule turns over wherever the user is standing.
    val now = minuteNow()
    // The active arrangement is a pure resolution over the mode list, the manual
    // switch and the clock (#155, #156); the UI holds none of it. A deleted
    // active mode resolves to the default here, with no intermediate empty state.
    val modes by modeStore.modes
    val modeSwitch by modeStore.switch
    val activeMode = resolveArrangement(modes, modeSwitch, now)
    LaunchedEffect(activeMode) { pinStore.setActive(activeMode ?: PinStore.DEFAULT_ARRANGEMENT) }
    // The resolve above already ignores a lapsed switch; this is what stops it
    // coming back to life when the window it lapsed against is edited away.
    LaunchedEffect(now, modes, modeSwitch) { modeStore.expireSwitch(now) }
    val context = LocalContext.current
    // The Focus session's home (#166); a duration that elapsed while the process
    // was dead is detected before root is first resolved, so a cold start after
    // an expiry lands on Home with the moment owed rather than on a dead Focus.
    val focusStore = remember {
        FocusStore(context, BodhaDatabase.get(context).focusRecords(), events)
            .also { it.resolveEnd(Instant.now()) }
    }
    val focusRunning = focusStore.active.value != null
    // The one sheet in the app (ADR 0011, #133). Every surface that opens one
    // reaches this, so the rule holds across surfaces rather than within each.
    // Saveable, so an in-flight Open Check survives rotation with its outcome
    // still owed; nothing else in it was durable before, and nothing else is now.
    val sheets = rememberSheetSlot { sessions.currentSession }
    // Every capability Bodha asks for, from any surface, enters here (#157).
    val education = rememberCapabilityEducation(events, sheets)
    var place by remember { mutableStateOf(Place(resolveRoot(focusRunning))) }
    // Which Settings row this arrival is about (#191). It describes the way in
    // rather than the surface, so every route that opens Settings says what it
    // means: Search's row result names one, and the two routes that open Settings
    // as a whole clear it.
    var settingsTarget by remember { mutableStateOf<SettingsRowId?>(null) }
    // The system Home button lands on root from wherever you were, and takes the
    // education sheet the departed surface opened with it — it belongs to that ask.
    LaunchedEffect(homeIntents) {
        place = Place(resolveRoot(focusRunning))
        education.close()
    }
    // A session starting makes Focus root and ending reverts root to Home,
    // dismissing whatever sheet was open through the single slot (#167, #169).
    // Only a user standing on Focus is moved: the end draws nothing and
    // interrupts nothing (#169), so someone mid-Search at the boundary stays
    // where they are — back now lands on Home, and the moment waits there.
    // The initial composition is not a boundary, so a cold start restores where
    // the stored state says root is without dismissing anything.
    var focusWasRunning by remember { mutableStateOf(focusRunning) }
    LaunchedEffect(focusRunning) {
        if (focusRunning == focusWasRunning) return@LaunchedEffect
        focusWasRunning = focusRunning
        if (!focusRunning) {
            sheets.dismissCurrent()
            if (place.surface == Surface.Focus) place = Place(resolveRoot(focusRunning))
        } else {
            place = Place(resolveRoot(focusRunning))
        }
    }
    // The phone session's own boundary, read from the engine's transitions and
    // nowhere else (ADR 0011, #134). Transitions arrive on the main thread, from
    // the broadcast receiver or the merge-window callback.
    DisposableEffect(sessions) {
        val listener: (Transition) -> Unit = { transition ->
            // Read live from the store: this listener outlives any one composition.
            applySessionBoundary(transition, sheets) {
                place = Place(resolveRoot(focusStore.active.value != null))
            }
        }
        sessions.addTransitionListener(listener)
        onDispose { sessions.removeTransitionListener(listener) }
    }
    val openCheckStateStore = remember { OpenCheckStateStore(context) }
    // Snapshot-restored so a pending timed session survives Bodha being killed (#75).
    val openCheck = remember { OpenCheckEngine(openCheckStateStore.load()) }
    val syncOpenCheck = { openCheckStateStore.save(openCheck.snapshot()) }
    val usage = remember { UsageReader(context) }
    val bypass = remember { BypassClassifier(context) }
    val records = remember { IntentRecordStore(context) }
    // The session-end moment appears only where policy allows Bodha on screen:
    // if the launcher was elsewhere at expiry, it shows on the next visibility,
    // and the phrase (computed at render) owns the time that actually elapsed.
    // No overlay, no forcing another app away — never claimed (#75).
    val settleSessionEnd = { openCheck.onSessionEndClose(); syncOpenCheck() }
    // Polled only while Bodha is on screen, so the moment opens at the visibility
    // it will be seen at. Opened behind another app it would be dismissed unseen
    // by the phone session ending under it (#134); the engine holds the expiry
    // until something settles it, so waiting costs nothing.
    LaunchedEffect(launcherVisible) {
        while (launcherVisible) {
            // The engine keeps returning the same expiry until it is settled, so
            // this both opens the moment and stops it re-opening over whatever
            // replaced it — replacement settles it, exactly as closing does.
            if (sheets.showing<Sheet.SessionEnd>() == null) {
                openCheck.advanceTo(Instant.now())?.let {
                    sheets.open(Sheet.SessionEnd(it), onReplaced = settleSessionEnd)
                }
            }
            // The Focus duration is watched the same way (#169): detection waits
            // for Bodha to be on screen — nothing interrupts the app the user is
            // inside, and the end itself draws nothing.
            focusStore.resolveEnd(Instant.now())
            delay(1_000)
        }
    }
    // The end moment waits for root (#170): the next arrival at Home with a
    // moment owed shows it, and it is settled — consumed, never to return — by
    // whatever answers it: done, extend, a dismissal, or the sheet that
    // replaces it. Settling on an answer rather than at open is what survives
    // a rotation or process death mid-moment: still owed, it simply reopens.
    // Ending early passes through the same gate, because the root reversion
    // has already landed the user on Home.
    val pendingFocusEnd = focusStore.pending.value
    LaunchedEffect(launcherVisible, place, pendingFocusEnd) {
        if (launcherVisible && place.surface == Surface.Home && pendingFocusEnd != null &&
            sheets.showing<Sheet.FocusEnd>() == null
        ) {
            sheets.open(
                Sheet.FocusEnd(pendingFocusEnd),
                onReplaced = { focusStore.consumePending() },
            )
        }
    }
    // At most one pause per opening (#77): the prompt engine is told while a
    // check is on screen, and a launch while the prompt shows skips the check.
    SideEffect {
        intentPrompt.openCheckShowing =
            sheets.current is Sheet.OpenCheck || sheets.current is Sheet.SessionEnd
    }
    // The single opening path (#8): every surface's launch flows through here.
    val openApp: (HomeAction) -> Unit = { action ->
        // An uninstalled app leaves the allowed list before the list decides
        // anything — treated as not allowed from then on, reinstall or not (#168).
        focusStore.active.value?.let { session ->
            focusStore.retainAllowed(
                catalog.resolve(session.allowedAppIds.toList()).mapTo(mutableSetOf()) { it.id }
            )
        }
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
            // The session's allowed list decides from the session alone — the
            // rule above plays no part in it (#168, ADR 0012).
            focusActive = focusStore.active.value != null,
            focusCheckDue = focusCheckDue(focusStore.active.value, action.id),
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
                // A focus-raised check is a reach, counted against the running
                // session and persisted at once (#168).
                if (decision.raisedByFocus) focusStore.countReach()
                sheets.open(Sheet.OpenCheck(action, raisedByFocus = decision.raisedByFocus))
            }
        }
        syncOpenCheck()
    }

    sheets.showing<Sheet.SessionEnd>()?.let { sheet ->
        val due = sheet.end
        // Phrased at render time so a moment shown late owns the elapsed truth.
        val overBy = (System.currentTimeMillis() - due.timedSession.endsAt.toEpochMilli()).coerceAtLeast(0)
        val settle = { sheets.close(sheet); syncOpenCheck() }
        // Reopening resolves through the catalog; an uninstalled app just settles.
        val reopen = { catalog.resolve(listOf(due.timedSession.appId)).firstOrNull()?.let(openApp) }
        // Nothing fights the user closing the moment: dismissing is closing, and
        // a phone session ending under it closes it the same way (#134).
        val close = sheets.dismissedBy(sheet) { openCheck.onSessionEndClose(); settle() }
        SessionEndSheet(
            phrase = sessionEndPhrase(due.timedSession.plannedMinutes, overBy),
            onClose = close,
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
            onDismiss = close,
        )
    }

    sheets.showing<Sheet.OpenCheck>()?.let { sheet ->
        val app = sheet.app
        // Back, Escape, the scrim, "Go back" and the phone session ending under
        // the check all land here — dismissal is not a bypass, so the launch
        // simply does not happen, and the turn-back is recorded once (#8, #133, #134).
        val turnBack = sheets.dismissedBy(sheet) {
            events.log(EventType.OpenCheckTurnedBack)
            openCheck.onTurnedBack(app.id)
            sheets.close(sheet)
            syncOpenCheck()
        }
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
                // The second of ADR 0013's three intent signals, and only when
                // something was actually written (#172) — proceeding alone
                // states nothing.
                typed?.let {
                    records.appendOpenCheckIntention(it)
                    events.log(EventType.OpenCheckIntentionWritten)
                }
                events.log(EventType.OpenCheckProceeded)
                // The session's count, only for the check it raised (#168).
                if (sheet.raisedByFocus) focusStore.countProceed()
                openCheck.onProceeded(app.id, Instant.now())
                sheets.close(sheet)
                // Back through the same path; the grant it holds covers this launch.
                openApp(app)
            },
            onOpenFor = { minutes, typed ->
                typed?.let {
                    records.appendOpenCheckIntention(it)
                    events.log(EventType.OpenCheckIntentionWritten)
                }
                events.log(EventType.OpenCheckProceeded)
                if (sheet.raisedByFocus) focusStore.countProceed()
                openCheck.onProceededFor(app.id, Instant.now(), minutes)
                sheets.close(sheet)
                openApp(app)
            },
            onDismiss = turnBack,
        )
    }

    sheets.showing<Sheet.FocusEnd>()?.let { sheet ->
        val moment = sheet.moment
        // Phrased at render time so a moment shown late owns the elapsed truth —
        // an early end just measures near-zero and reads calm.
        val overBy = (System.currentTimeMillis() - moment.record.endedAt.toEpochMilli()).coerceAtLeast(0)
        val settle = { focusStore.consumePending(); sheets.close(sheet) }
        // Dismissing is an answer too: the moment settles and never returns.
        val dismiss = sheets.dismissedBy(sheet) { settle() }
        FocusEndSheet(
            label = moment.record.label,
            durationLine = focusDurationLine(moment.record, overBy),
            reachLine = focusReachLine(moment.record.reaches),
            onExtend = {
                // The same session, ten more minutes; root returns to Focus
                // through the boundary effect above (#170).
                settle()
                focusStore.extend(moment, Instant.now())
            },
            onDone = settle,
            onDismiss = dismiss,
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

    // The four-swipe fan-out both roots carry (#167): one site, so ADR 0011's
    // reassignment edits it once. Home adds its own empty-area taps below;
    // Focus has none, so the unlabelled pair draws no node and announces nothing.
    val surfaceFanOut = HomeGestures(
        swipeDown = openSurface(Surface.Search) { place = Place(it) },
        swipeUp = openSurface(Surface.Library) { place = Place(it) },
        swipeLeft = openSurface(Surface.Awareness) { place = Place(it) },
        swipeRight = openSurface(Surface.Today) { place = Place(it) },
        doubleTapEmpty = GestureAction(label = null) {},
        longPressEmpty = GestureAction(label = null) {},
    )

    when (place.surface) {
        // Falls through to Home's own content below.
        Surface.Home -> Unit
        Surface.Search -> {
            SearchSurface(
                pinStore = pinStore,
                libraryStore = libraryStore,
                defaultStore = defaultStore,
                catalog = catalog,
                education = education,
                calendar = remember { CalendarReader(context) },
                contacts = remember { ContactsReader(context) },
                focusStore = focusStore,
                session = sessions.currentSession,
                surfaces = BUILT_SURFACES,
                sheets = sheets,
                openApp = openApp,
                openSurface = { settingsTarget = null; place = Place(it) },
                openSettingsRow = { settingsTarget = it; place = Place(Surface.Settings) },
            )
            return
        }
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
                sheets = sheets,
                openApp = openApp,
                onPause = { place = Place(Surface.Focus) },
                onBack = back,
            )
            return
        }
        Surface.Today -> {
            TodaySurface(
                intentionStore = intentionStore,
                sheets = sheets,
                education = education,
                // The inbox lives inside Today and opens as its own surface;
                // back from there returns Home (ADR 0011).
                openInbox = { place = Place(Surface.Inbox) },
            )
            return
        }
        Surface.Awareness -> {
            AwarenessSurface(sessions = sessions, onBack = back)
            return
        }
        Surface.Focus -> {
            FocusSurface(
                focusStore = focusStore,
                catalog = catalog,
                sheets = sheets,
                // The shared fan-out, whole (#167): every surface stays a swipe
                // away, and back from any of them returns here while the
                // session runs, because Focus is root.
                gestures = surfaceFanOut,
            )
            return
        }
        Surface.Inbox -> {
            InboxSurface(
                education = education,
                sheets = sheets,
                muteStore = remember { MuteStore(context) },
                depth = place.depth,
                openMutedSources = { place = Place(Surface.Inbox, depth = 1) },
                onBack = back,
            )
            return
        }
        Surface.Settings -> {
            SettingsSurface(
                homeRoleHeld = homeRoleHeld,
                onRequestHomeRole = requestHomeRole,
                appearance = AppearanceChoices(
                    theme = appearanceStore.theme.value,
                    onTheme = appearanceStore::set,
                    clock = appearanceStore.clock.value,
                    onClock = appearanceStore::set,
                    date = appearanceStore.date.value,
                    onDate = appearanceStore::set,
                ),
                target = settingsTarget,
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

    // Remaining inputs fill in as their features ship (suggestions #6, digest #10, …).
    val state = resolveHome(
        HomeInputs(
            dailyIntention = intention?.textOn(now),
            // Only a non-default mode labels Home; the spare Home stays spare.
            contextLabel = activeMode,
            pinned = pinned,
            hidden = hidden,
            // Fed for the first time (#166): the indicator stands while a
            // session runs — Home stays reachable by name through Search — and
            // drops the moment none does.
            focusActive = focusRunning,
            sessionIntent = sessionIntent,
            homeRoleHeld = homeRoleHeld,
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            state = state,
            onAction = openApp,
            onActionLongPress = { optionsFor = it },
            onAddAction = { pickerOpen = true },
            onOpenToday = { place = Place(Surface.Today) },
            onContextLabelTap = { modeSelectorOpen = true },
            iconFor = { catalog.icon(it.id) },
            iconKey = catalog.version.intValue,
            // Labels name the destination, so they stay true when ADR 0011's
            // reassignment lands and a swipe points somewhere else.
            gestures = surfaceFanOut.copy(
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

        // The prompt is due from the runtime and shows through the one slot; if
        // something else takes the slot, the runtime is told it is gone, so the
        // decision stops being pending rather than suppressing later checks.
        val due by intentPrompt.promptDue
        LaunchedEffect(due) {
            val decision = due ?: return@LaunchedEffect
            events.log(EventType.IntentPromptShown)
            sheets.open(Sheet.IntentPrompt(decision), onReplaced = intentPrompt::withdraw)
        }
        sheets.showing<Sheet.IntentPrompt>()?.let { sheet ->
            // A prompt the session ended under went unanswered exactly as a
            // swipe-down leaves it, and rests on the same cooldown (#134). The
            // decision comes from the sheet, so the record is written on that
            // path too — by then the runtime has already dropped the pending one.
            val dismiss = sheets.dismissedBy(sheet) {
                events.log(EventType.IntentPromptDismissed)
                intentPrompt.dismiss(sheet.decision)
                sheets.close(sheet)
            }
            IntentPromptSheet(
                onSelect = { category, text ->
                    events.log(EventType.IntentPromptAnswered)
                    intentPrompt.select(category, text)
                    sheets.close(sheet)
                    // The intent flows straight into the action.
                    if (category == IntentCategory.FindSomething) place = Place(Surface.Search)
                },
                onDismiss = dismiss,
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
        EditHomeDialog(
            onAddPin = { pickerOpen = true },
            onContextModes = { modeManageOpen = true },
            onSettings = { settingsTarget = null; place = Place(Surface.Settings) },
            onDismiss = { editingHome = false },
        )
    }
    if (modeSelectorOpen) {
        ModeSelectorDialog(
            modes = modes,
            current = activeMode,
            // Stamped at the moment of the choice, which is what the expiry at
            // the next window boundary is measured from (#156).
            onPick = { modeStore.select(it, LocalDateTime.now()) },
            onManage = { modeManageOpen = true; modeSelectorOpen = false },
            onDismiss = { modeSelectorOpen = false },
        )
    }
    if (modeManageOpen) {
        ModeManageDialog(
            modes = modes,
            onCreate = modeStore::create,
            onRename = modeStore::rename,
            onDelete = modeStore::delete,
            onSetWindow = modeStore::setWindow,
            onMove = modeStore::move,
            onDismiss = { modeManageOpen = false },
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
