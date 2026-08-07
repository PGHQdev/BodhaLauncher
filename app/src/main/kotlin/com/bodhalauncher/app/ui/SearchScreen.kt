package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.AppResult
import com.bodhalauncher.engine.SearchResult
import com.bodhalauncher.engine.SearchState
import com.bodhalauncher.engine.ShortcutResult

/**
 * Search: a field and whatever the query found, and nothing else.
 *
 * **It opens empty** — no recents, no suggestions, no pins (ADR 0014). The one
 * line beneath the field is [SEARCH_NOTHING_FOUND], and it appears only for a
 * query that matched nothing; an untyped field says nothing, because nothing has
 * failed yet.
 *
 * Sections draw in the engine's fixed order under their overlines, only those
 * with matches (#181). A shortcut row wears its owning app's mark: the label
 * says what it does, the mark says whose it is.
 *
 * Back and Escape are the navigation model's (#132) and appear nowhere here: back
 * is the host's `BackHandler` and Escape reaches the root binding along the focus
 * chain, which the field's arrival focus keeps live from the first frame.
 */
@Composable
fun SearchScreen(
    state: SearchState,
    query: String,
    onQueryChange: (String) -> Unit,
    /** The launcher icon for an app id — a result's own, or a shortcut's owner's. */
    iconFor: (String) -> ImageBitmap?,
    /** Changes when any package changes, so cached icons refresh with their apps. */
    iconKey: Any,
    onOpen: (SearchResult) -> Unit,
) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        SearchField(query, onQueryChange)
        if (state.nothingFound) {
            // Said once, over the whole search: an absent section is not a
            // failure, so no section owes its own empty state (ADR 0014).
            Text(
                text = SEARCH_NOTHING_FOUND,
                color = colors.inkMuted,
                style = BodhaType.body,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            state.sections.forEach { (section, rows) ->
                // The overline is plain text, not an item boundary a key would
                // protect: sections never reorder, the engine fixes that.
                item(key = "overline:$section") { SectionOverline(section.heading) }
                items(count = rows.size, key = { rows[it].result.key }) { i ->
                    val row = rows[i]
                    val result = row.result
                    val ownerId = when (result) {
                        is AppResult -> result.app.id
                        is ShortcutResult -> result.shortcut.appId
                    }
                    val icon = remember(ownerId, iconKey) { iconFor(ownerId) }
                    // No long-press yet: result actions are #184's slice. The
                    // reason line rides as the subtitle — plain text, no tab
                    // stop of its own (#182).
                    ListRow(
                        title = result.label,
                        subtitle = row.reason,
                        onClick = { onOpen(result) },
                        // The app's own mark, so bare rather than chipped (rule 5).
                        leading = if (icon != null) ({ AppMark(icon) }) else null,
                    )
                }
            }
        }
    }
}

/** The field's spoken name, and what a docked user's first Tab would land on. */
internal const val SEARCH_FIELD_LABEL = "Search"

/** The whole of what a search that found nothing says. */
internal const val SEARCH_NOTHING_FOUND = "Nothing matches"

/**
 * Search's own field, in the pill shape rule 4 gives a field.
 *
 * It is [LibrarySearchField]'s twin rather than a shared component: the two
 * differ in the only thing a field's arrival decides — the Library suppresses the
 * IME under ADR 0022's general rule, and Search shows it under ADR 0014's
 * zero-query state (#180). The shape both draw is the shared thing, and that is
 * [BodhaField], which is what the gallery holds a specimen of.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalBodhaColors.current
    BodhaField {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = BodhaType.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) {
                        Text(text = "Search", color = colors.inkMuted, style = BodhaType.body)
                    }
                    field()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusOnOpenWithIme()
                .downEntersList()
                .semantics { contentDescription = SEARCH_FIELD_LABEL }
                // Stays last: ADR 0020's caveat in BodhaTheme.kt.
                .touchTargetFloor(),
        )
    }
}
