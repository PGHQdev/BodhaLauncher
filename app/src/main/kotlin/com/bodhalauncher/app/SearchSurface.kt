package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.PinStore
import androidx.compose.runtime.DisposableEffect
import com.bodhalauncher.app.ui.ResultActionsSheet
import com.bodhalauncher.app.ui.SearchScreen
import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.engine.ActionResult
import com.bodhalauncher.engine.AppResult
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.SearchInputs
import com.bodhalauncher.engine.SearchShortcut
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.ShortcutResult
import com.bodhalauncher.engine.Surface
import com.bodhalauncher.engine.SurfaceResult
import com.bodhalauncher.engine.resolveSearch

/**
 * Search as a surface of its own, the shape [LibrarySurface] set (#132). Opening a
 * result leaves through [openApp], the single opening path every surface shares (#8).
 *
 * Apps are the one domain of ADR 0014's seven that this slice ships; the rest join
 * [SearchInputs] as their providers do. Hiding is the Library's (#62) and is only
 * read here: a hidden app matches when the Library's toggle says it may.
 *
 * There is no `onBack`, and that is the point — back and Escape are the navigation
 * model's, so Search binds neither (#180).
 */
@Composable
fun SearchSurface(
    pinStore: PinStore,
    libraryStore: LibraryStore,
    catalog: AppCatalog,
    /** The running session, or null once none is; see [query]'s scope below. */
    session: SessionId?,
    /** The surfaces the host renders; an unbuilt one never appears as a result (#189). */
    surfaces: List<Surface>,
    sheets: SheetSlot,
    openApp: (HomeAction) -> Unit,
    /** The navigation model's entry point, not a route of Search's own (#189). */
    openSurface: (Surface) -> Unit,
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
    // The sheet is about a result on this surface, so it leaves with the surface
    // — the reason the Library's does (#132).
    DisposableEffect(Unit) {
        onDispose { sheets.showing<Sheet.ResultActions>()?.let(sheets::close) }
    }
    SearchScreen(
        state = resolveSearch(
            SearchInputs(
                apps = allApps,
                shortcuts = shortcuts.map { SearchShortcut(id = it.shortcutId, appId = it.appId, label = it.label) },
                query = query,
                actions = settingsScreens.map { it.searchAction() },
                surfaces = surfaces,
                hidden = hidden,
                pinned = pinned.toSet(),
                hiddenSearchable = hiddenSearchable,
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
                is ActionResult -> {
                    val screen = settingsScreens.firstOrNull { it.searchAction().id == result.action.id }
                    if (screen != null) openSettingsScreen(context, screen)
                }
            }
        },
        // Replaces whatever sheet is open rather than stacking on it — the
        // slot's own rule (#133).
        onAppActions = { sheets.open(Sheet.ResultActions(it)) },
    )
    sheets.showing<Sheet.ResultActions>()?.let { sheet ->
        val app = sheet.app
        // Told to the slot as well as used here, so a session ending over this
        // sheet dismisses it the way its own footer does (ADR 0011, #134).
        val dismiss = sheets.dismissedBy(sheet) { sheets.close(sheet) }
        ResultActionsSheet(
            app = app,
            isPinned = app.id in pinned,
            isHidden = app.id in hidden,
            onOpen = { dismiss(); openApp(app) },
            onPin = { dismiss(); pinStore.pin(app.id) },
            onUnpin = { dismiss(); pinStore.unpin(app.id) },
            onHide = { dismiss(); pinStore.hide(app.id) },
            onUnhide = { dismiss(); pinStore.unhide(app.id) },
            onDismiss = dismiss,
        )
    }
}
