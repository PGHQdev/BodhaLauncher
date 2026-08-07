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
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.SearchState

/**
 * Search: a field and whatever the query found, and nothing else.
 *
 * **It opens empty** — no recents, no suggestions, no pins (ADR 0014). The one
 * line beneath the field is [SEARCH_NOTHING_FOUND], and it appears only for a
 * query that matched nothing; an untyped field says nothing, because nothing has
 * failed yet.
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
    iconFor: (HomeAction) -> ImageBitmap?,
    /** Changes when any package changes, so cached icons refresh with their apps. */
    iconKey: Any,
    onOpen: (HomeAction) -> Unit,
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
            // Said once, over the whole search: with one domain shipped there is
            // no section for a per-section empty state to belong to (ADR 0014).
            Text(
                text = SEARCH_NOTHING_FOUND,
                color = colors.inkMuted,
                style = BodhaType.body,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(count = state.rows.size, key = { state.rows[it].id }) { i ->
                val app = state.rows[i]
                val icon = remember(app.id, iconKey) { iconFor(app) }
                // No long-press: hide and pin belong to the Library, which is
                // where an app's actions are (#61, #62).
                ListRow(
                    title = app.label,
                    onClick = { onOpen(app) },
                    // The app's own mark, so bare rather than chipped (rule 5).
                    leading = if (icon != null) ({ AppMark(icon) }) else null,
                )
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
                        Text(text = "Search apps", color = colors.inkMuted, style = BodhaType.body)
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
