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
import com.bodhalauncher.engine.HomeAction

/**
 * A search result's actions (#184): open, and the Library's own hide and pin.
 * Deliberately thinner than [AppActionsSheet] — Search is where you find a thing,
 * not where you manage it; groups, pause and Open Check stay the Library's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultActionsSheet(
    app: HomeAction,
    isPinned: Boolean,
    isHidden: Boolean,
    /** The query as typed, for the default rows' own wording (#185). */
    query: String,
    isDefault: Boolean,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onSetDefault: () -> Unit,
    onClearDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.ground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .padding(horizontal = 28.dp),
        ) {
            Text(
                // An app's own name is machinery, not Bodha's voice (ADR 0021).
                text = app.label,
                color = colors.ink,
                style = BodhaType.title,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            SheetRow("Open", onOpen)
            if (isPinned) SheetRow("Unpin", onUnpin) else SheetRow("Pin", onPin)
            if (isHidden) SheetRow("Unhide", onUnhide) else SheetRow("Hide", onHide)
            // "When I type this, this one first" — and its taking back (#185).
            if (isDefault) {
                SheetRow("Clear default for “$query”", onClearDefault)
            } else {
                SheetRow("Put first for “$query”", onSetDefault)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(24.dp))
        }
    }
}
