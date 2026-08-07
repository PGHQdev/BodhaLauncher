package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * What an Awareness row offers beyond opening it (#178): taking the thing it
 * names out of every view and every figure, reversibly.
 *
 * **One sheet for both rows**, which is why it takes a title rather than a
 * session or an app. The two rows differ in what names them — a clock time and
 * a span for a session, an app's own name for an app — and in nothing else, and
 * a second composable would be the same `ModalBottomSheet` with a different way
 * of arriving at one string.
 *
 * There is no Include row. An excluded item renders nowhere, so no row exists to
 * open this sheet on one; undoing happens in the exclusions list, which is where
 * the things that are no longer on screen still are.
 *
 * The title is machinery in every case — an app's own name, or a time and a
 * duration Bodha measured (ADR 0021) — so it takes the sans like every other
 * sheet's title, however large it is on the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwarenessActionsSheet(
    title: String,
    onExclude: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.ground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Escape dismisses in the sheet's own window, because a sheet
                // composes into a root of its own and the activity's binding
                // never sees the key (ADR 0022).
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .padding(horizontal = 28.dp),
        ) {
            Text(
                text = title,
                color = colors.ink,
                style = BodhaType.title,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            // The shared row, not a private copy: `EducationSheet`'s hand-rolled
            // one is an existing inconsistency and propagating it would make it
            // a convention.
            SheetRow("Exclude", onExclude)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(24.dp))
        }
    }
}
