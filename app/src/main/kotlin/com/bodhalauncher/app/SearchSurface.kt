package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.ui.SearchScreen
import com.bodhalauncher.engine.AppResult
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.SearchInputs
import com.bodhalauncher.engine.SearchShortcut
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.ShortcutResult
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
    openApp: (HomeAction) -> Unit,
) {
    val allApps by catalog.apps
    val hidden by pinStore.hidden
    val hiddenSearchable by libraryStore.hiddenSearchable
    // Re-read when any package changes: a shortcut's lifetime is its app's.
    val shortcuts = remember(catalog.version.intValue) { catalog.allShortcuts() }
    // Search opens empty (ADR 0014), and a session is what "opens" is measured
    // against: leaving the surface drops this composition, and a screen-off that
    // outlasts the merge window changes the key, so an unlock never lands on the
    // previous session's query. Where an unlock leaves you is the navigation
    // model's question (#132), not Search's — this only makes sure the field is
    // empty when you get here.
    var query by remember(session) { mutableStateOf("") }
    SearchScreen(
        state = resolveSearch(
            SearchInputs(
                apps = allApps,
                shortcuts = shortcuts.map { SearchShortcut(id = it.shortcutId, appId = it.appId, label = it.label) },
                query = query,
                hidden = hidden,
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
            }
        },
    )
}
