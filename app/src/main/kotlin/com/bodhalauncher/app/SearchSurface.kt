package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.awareness.LaunchRecordDao
import com.bodhalauncher.app.awareness.toRecord
import com.bodhalauncher.app.capability.CapabilityEducation
import com.bodhalauncher.app.contacts.ContactsReader
import com.bodhalauncher.app.focus.FocusStore
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.home.SearchDefaultStore
import com.bodhalauncher.app.today.CalendarReader
import com.bodhalauncher.engine.canonicalQuery
import androidx.compose.runtime.DisposableEffect
import com.bodhalauncher.app.ui.ContactActionsSheet
import com.bodhalauncher.app.ui.ResultActionsSheet
import com.bodhalauncher.app.ui.SearchScreen
import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.engine.ActionResult
import com.bodhalauncher.engine.AppResult
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.ContactResult
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.EventResult
import com.bodhalauncher.engine.FocusActionResult
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.LaunchTally
import com.bodhalauncher.engine.ProviderInstance
import com.bodhalauncher.engine.SETTINGS_ROWS
import com.bodhalauncher.engine.SearchContact
import com.bodhalauncher.engine.SearchInputs
import com.bodhalauncher.engine.SearchShortcut
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.SettingsRowId
import com.bodhalauncher.engine.SettingsRowResult
import com.bodhalauncher.engine.ShortcutResult
import com.bodhalauncher.engine.Surface
import com.bodhalauncher.engine.SurfaceResult
import com.bodhalauncher.engine.UngrantedResult
import com.bodhalauncher.engine.isBlankQuery
import com.bodhalauncher.engine.resolveLaunchTallies
import com.bodhalauncher.engine.resolveSearch
import com.bodhalauncher.engine.searchCalendarWindow
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Search as a surface of its own, the shape [LibrarySurface] set (#132). Opening a
 * result leaves through [openApp], the single opening path every surface shares (#8).
 *
 * Contacts and calendar (#186, #187) read their providers here — the calendar
 * through Today's own reader — and their grants are observed **per query**: the
 * granted flags are remembered against the query text, so a grant landing
 * mid-session shows on the next keystroke rather than retroactively re-rendering
 * what is already drawn. Hiding is the Library's (#62) and is only read here: a
 * hidden app matches when the Library's toggle says it may.
 *
 * There is no `onBack`, and that is the point — back and Escape are the navigation
 * model's, so Search binds neither (#180).
 */
@Composable
fun SearchSurface(
    pinStore: PinStore,
    libraryStore: LibraryStore,
    defaultStore: SearchDefaultStore,
    /** Bodha's own launch log (#173), read here as the last ranking tier's input (#183). */
    launchRecords: LaunchRecordDao,
    catalog: AppCatalog,
    /** The single capability entry point (#157): grants are read and asked through it. */
    education: CapabilityEducation,
    /** Today's own calendar edge (#159), reused rather than a second reader (#187). */
    calendar: CalendarReader,
    contacts: ContactsReader,
    /** Setups to offer and the start path — the same lifecycle as starting anywhere (#190). */
    focusStore: FocusStore,
    /** The running session, or null once none is; see [query]'s scope below. */
    session: SessionId?,
    /** Whether Bodha is on screen; the launch tally is re-read on the way back (#183). */
    launcherVisible: Boolean,
    /** The surfaces the host renders; an unbuilt one never appears as a result (#189). */
    surfaces: List<Surface>,
    sheets: SheetSlot,
    openApp: (HomeAction) -> Unit,
    /** The navigation model's entry point, not a route of Search's own (#189). */
    openSurface: (Surface) -> Unit,
    /** Settings, standing on the row that matched (#191); the host owns where that row lives. */
    openSettingsRow: (SettingsRowId) -> Unit,
) {
    val allApps by catalog.apps
    val hidden by pinStore.hidden
    val pinned by pinStore.pinned
    val hiddenSearchable by libraryStore.hiddenSearchable
    // Re-read when any package changes: a shortcut's lifetime is its app's.
    val shortcuts = remember(catalog.version.intValue) { catalog.allShortcuts() }
    val context = LocalContext.current
    // Resolved once per composition of the surface: which screens a device
    // honours changes with system updates, not keystrokes.
    val settingsScreens = remember { resolvedSettingsScreens(context) }
    // Search opens empty (ADR 0014), and a session is what "opens" is measured
    // against: leaving the surface drops this composition, and a screen-off that
    // outlasts the merge window changes the key, so an unlock never lands on the
    // previous session's query. Where an unlock leaves you is the navigation
    // model's question (#132), not Search's — this only makes sure the field is
    // empty when you get here.
    var query by remember(session) { mutableStateOf("") }
    // Grants observed at the query, not at the resume tick: granting while
    // Search is open brings a section back on the next query, with no
    // retroactive re-render of what this query already drew (#186, #187).
    val contactsGranted = remember(session, query) { education.grantedNow(Capability.Contacts) }
    val calendarGranted = remember(session, query) { education.grantedNow(Capability.Calendar) }
    // The whole address book's names, read once per grant observation;
    // matching is the engine's. Nothing is stored (ADR 0009).
    val allContacts by produceState(emptyList<SearchContact>(), contactsGranted) {
        value = if (contactsGranted) withContext(Dispatchers.IO) { contacts.contacts() } else emptyList()
    }
    // The provider read is slower than a keystroke, so the calendar section
    // fills in beneath the already-drawn sections when this lands — null keeps
    // it absent rather than empty while the read is in flight (#187).
    val calendarInstances by produceState<List<ProviderInstance>?>(null, query, calendarGranted) {
        value = null
        if (calendarGranted && !isBlankQuery(query)) {
            value = withContext(Dispatchers.IO) {
                val (from, until) = searchCalendarWindow(LocalDateTime.now())
                calendar.instances(from, until)
            }
        }
    }
    // The launch log folded per app, for the last ranking tier (#183). Read once
    // per key rather than per keystroke: the whole table is scanned, and a tally
    // does not change while you type. The key takes [launcherVisible] as well as
    // the session because the launch that ought to move a row is precisely the one
    // that happened while Bodha was off screen — coming back from an app is when
    // this is stale, and a session outlives that round trip.
    //
    // A failed read retains the previous tally rather than resolving to empty, so
    // a transient database error holds the last good ranking instead of dropping
    // the tier out from under a query mid-typing.
    val launches by produceState(emptyMap<String, LaunchTally>(), session, launcherVisible) {
        value = withContext(Dispatchers.IO) {
            runCatching { resolveLaunchTallies(launchRecords.all().map { it.toRecord() }) }.getOrNull()
        } ?: value
    }
    // The sheet is about a result on this surface, so it leaves with the surface
    // — the reason the Library's does (#132).
    DisposableEffect(Unit) {
        onDispose {
            sheets.showing<Sheet.ResultActions>()?.let(sheets::close)
            sheets.showing<Sheet.ContactActions>()?.let(sheets::close)
        }
    }
    SearchScreen(
        state = resolveSearch(
            SearchInputs(
                apps = allApps,
                shortcuts = shortcuts.map { SearchShortcut(id = it.shortcutId, appId = it.appId, label = it.label) },
                query = query,
                actions = settingsScreens.map { it.searchAction() },
                surfaces = surfaces,
                // The catalogue itself (ADR 0019): Settings renders exactly these,
                // so what is findable and what is drawn are one list rather than
                // two agreeing. The first row to render conditionally — a Pro
                // control, an account action — owes the same condition here, which
                // is what taking the rows as an input keeps possible (#191).
                settingsRows = SETTINGS_ROWS,
                hidden = hidden,
                pinned = pinned.toSet(),
                defaults = defaultStore.defaults.value,
                launches = launches,
                hiddenSearchable = hiddenSearchable,
                contactsGranted = contactsGranted,
                contacts = allContacts,
                calendarGranted = calendarGranted,
                calendarInstances = calendarInstances,
                // One session at a time is structural (#166): while one runs the
                // store's start would refuse silently, so nothing is offered.
                focusSetups = if (focusStore.active.value == null) focusStore.setups.value else emptyList(),
            )
        ),
        query = query,
        onQueryChange = { query = it },
        iconFor = catalog::icon,
        iconKey = catalog.version.intValue,
        onOpen = { result ->
            when (result) {
                is AppResult -> openApp(result.app)
                // Launched directly, as the Library's shortcut route does —
                // [openApp] resolves a main activity, which a shortcut is not.
                is ShortcutResult -> {
                    val match = shortcuts.firstOrNull {
                        it.appId == result.shortcut.appId && it.shortcutId == result.shortcut.id
                    }
                    if (match != null) catalog.launchShortcut(match)
                }
                is SurfaceResult -> openSurface(result.surface)
                is SettingsRowResult -> openSettingsRow(result.row.id)
                is ActionResult -> {
                    val screen = settingsScreens.firstOrNull { it.searchAction().id == result.action.id }
                    if (screen != null) openSettingsScreen(context, screen)
                }
                // The tap is the non-destructive open (#186); call and message
                // sit on the Actions node below.
                is ContactResult -> contacts.open(result.contact)
                is EventResult -> calendar.open(result.event)
                // The same session lifecycle as starting anywhere else (#190):
                // the store's start flips root to Focus through the host.
                is FocusActionResult -> focusStore.start(result.setup, Instant.now())
                // Activating a named state is an explicit ask, so the education
                // always shows and continues into the runtime request (#157).
                is UngrantedResult -> education.ask(result.capability, EducationEntry.UserRequest)
            }
        },
        // Replaces whatever sheet is open rather than stacking on it — the
        // slot's own rule (#133).
        onAppActions = { sheets.open(Sheet.ResultActions(it)) },
        onContactActions = { sheets.open(Sheet.ContactActions(it)) },
    )
    sheets.showing<Sheet.ResultActions>()?.let { sheet ->
        val app = sheet.app
        // Told to the slot as well as used here, so a session ending over this
        // sheet dismisses it the way its own footer does (ADR 0011, #134).
        val dismiss = sheets.dismissedBy(sheet) { sheets.close(sheet) }
        // The default is recorded against the canonical query, so retyping it
        // in any case or spacing finds it (#185).
        val canonical = canonicalQuery(query)
        ResultActionsSheet(
            app = app,
            isPinned = app.id in pinned,
            isHidden = app.id in hidden,
            query = query.trim(),
            isDefault = defaultStore.defaults.value[canonical] == app.id,
            onOpen = { dismiss(); openApp(app) },
            onPin = { dismiss(); pinStore.pin(app.id) },
            onUnpin = { dismiss(); pinStore.unpin(app.id) },
            onHide = { dismiss(); pinStore.hide(app.id) },
            onUnhide = { dismiss(); pinStore.unhide(app.id) },
            onSetDefault = { dismiss(); defaultStore.set(canonical, app.id) },
            onClearDefault = { dismiss(); defaultStore.clear(canonical) },
            onDismiss = dismiss,
        )
    }
    sheets.showing<Sheet.ContactActions>()?.let { sheet ->
        val contact = sheet.contact
        val dismiss = sheets.dismissedBy(sheet) { sheets.close(sheet) }
        // One provider row, read when the sheet opens; without a number the
        // call and message rows simply do not exist (#186).
        val number by produceState<String?>(null, contact) {
            value = withContext(Dispatchers.IO) { contacts.phoneNumber(contact) }
        }
        ContactActionsSheet(
            contact = contact,
            phoneNumber = number,
            onOpen = { dismiss(); contacts.open(contact) },
            onCall = { number?.let { dismiss(); contacts.dial(it) } },
            onMessage = { number?.let { dismiss(); contacts.message(it) } },
            onDismiss = dismiss,
        )
    }
}
