package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.bodhalauncher.app.home.AppShortcut
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.OpenCheckMode

/**
 * The app actions sheet: the app's own name in the sans, since a name Bodha did
 * not write is machinery however large it is (ADR 0021), over sans hairline rows. Every app-level operation lives here; Pause only
 * routes onward (Focus #9 owns the behavior).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    app: HomeAction,
    shortcuts: List<AppShortcut>,
    isPinned: Boolean,
    isHidden: Boolean,
    openCheckMode: OpenCheckMode?,
    /** False for classified emergency/utility apps with no rule (#77). */
    openCheckOffered: Boolean,
    onOpen: () -> Unit,
    onShortcut: (AppShortcut) -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    /** Opens the group membership picker (user groups, #67). */
    onGroups: () -> Unit,
    onPause: () -> Unit,
    onOpenCheck: () -> Unit,
    onAppInfo: () -> Unit,
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
            shortcuts.forEach { shortcut ->
                SheetRow("· ${shortcut.label}") { onShortcut(shortcut) }
            }
            if (isPinned) SheetRow("Unpin", onUnpin) else SheetRow("Pin", onPin)
            if (isHidden) SheetRow("Unhide", onUnhide) else SheetRow("Hide", onHide)
            SheetRow("Groups", onGroups)
            SheetRow("Pause", onPause)
            // Wording over glyphs (ADR 0010); the rule dialog holds edit and remove.
            if (openCheckOffered) SheetRow(
                openCheckMode?.let { "Open Check · ${openCheckModeLabel(it).lowercase()}" } ?: "Open Check",
                onOpenCheck,
            )
            SheetRow("App info", onAppInfo)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A hairline-topped tappable row — the sheets' shared machinery (ADR 0010). */
@Composable
internal fun SheetRow(label: String, onClick: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text(
            text = label,
            color = colors.ink,
            style = BodhaType.body,
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        )
    }
}
