package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bodhalauncher.app.capability.CapabilityEducation
import com.bodhalauncher.app.entitlement.EntitlementStore
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.GroupStore
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.home.UsageReader
import com.bodhalauncher.app.opencheck.BypassClassifier
import com.bodhalauncher.app.opencheck.OpenCheckRuleStore
import com.bodhalauncher.app.ui.AppActionsSheet
import com.bodhalauncher.app.ui.GroupPickerDialog
import com.bodhalauncher.app.ui.LibraryScreen
import com.bodhalauncher.app.ui.OpenCheckRuleDialog
import com.bodhalauncher.app.ui.ProBoundaryDialog
import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.GateDecision
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.LibraryInputs
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.ProBoundary
import com.bodhalauncher.engine.resolveLibrary
import com.bodhalauncher.engine.resolveOpenCheckRuleWrite

/**
 * The App Library as a surface of its own, lifted out of the host so root stops
 * holding another surface inline (#132). Opening an app leaves through [openApp],
 * the single opening path every surface shares (#8).
 */
@Composable
fun LibrarySurface(
    pinStore: PinStore,
    libraryStore: LibraryStore,
    groupStore: GroupStore,
    openCheckStore: OpenCheckRuleStore,
    entitlementStore: EntitlementStore,
    catalog: AppCatalog,
    usage: UsageReader,
    bypass: BypassClassifier,
    education: CapabilityEducation,
    sheets: SheetSlot,
    openApp: (HomeAction) -> Unit,
    onPause: () -> Unit,
    onBack: () -> Unit,
) {
    val pinnedIds by pinStore.pinned
    val hidden by pinStore.hidden
    val allApps by catalog.apps
    var query by remember { mutableStateOf("") }
    var groupsFor by remember { mutableStateOf<HomeAction?>(null) }
    var openCheckFor by remember { mutableStateOf<HomeAction?>(null) }
    var boundary by remember { mutableStateOf<ProBoundary?>(null) }
    val hiddenSearchable by libraryStore.hiddenSearchable
    val layout by libraryStore.layout
    val categories by catalog.categories
    val groups by groupStore.groups
    // Re-read on every resume, so granting access in settings shows up on return;
    // read on demand and never stored (ADR 0009).
    val lastUsed = remember(allApps, education.resumeTick) { usage.lastUsed() }
    // The actions sheet is about an app on this surface, so it leaves with the
    // surface — the system Home button landing on root (#132) must not leave one
    // parked in the app-wide slot to reappear the next time the Library opens.
    DisposableEffect(Unit) {
        onDispose { sheets.showing<Sheet.AppActions>()?.let(sheets::close) }
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
        onLayoutNoteTap = { education.ask(Capability.UsageAccess, EducationEntry.UserRequest) },
        iconFor = { catalog.icon(it.id) },
        iconKey = catalog.version.intValue,
        onOpen = openApp,
        onLongPress = { sheets.open(Sheet.AppActions(it)) },
        onPin = { pinStore.pin(it.id) },
        onHide = { pinStore.hide(it.id) },
        onUnhide = { pinStore.unhide(it.id) },
        hiddenSearchable = hiddenSearchable,
        onHiddenSearchableChange = libraryStore::setHiddenSearchable,
        groupNames = groups.map { it.name },
        onCreateGroup = groupStore::create,
        onRenameGroup = groupStore::rename,
        onDeleteGroup = groupStore::delete,
        onBack = onBack,
    )
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
                        if (rule.mode == OpenCheckMode.DailyThreshold) {
                            education.ask(Capability.UsageAccess, EducationEntry.UserRequest)
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
    sheets.showing<Sheet.AppActions>()?.let { sheet ->
        val app = sheet.app
        val dismiss = { sheets.close(sheet) }
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
            onPause = { dismiss(); onPause() },
            onOpenCheck = { dismiss(); openCheckFor = app },
            onAppInfo = { dismiss(); catalog.openAppInfo(app.id) },
            onDismiss = dismiss,
        )
    }
}
