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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bodhalauncher.app.home.AppShortcut
import com.bodhalauncher.engine.HomeAction

/**
 * The app actions sheet: serif app name (the voice), sans hairline rows (the
 * machinery) — ADR 0010. Every app-level operation lives here; Pause and
 * Set Open Check only route onward (Focus #9, Open Check #8 own the behavior).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    app: HomeAction,
    shortcuts: List<AppShortcut>,
    isPinned: Boolean,
    isHidden: Boolean,
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
            Text(
                text = app.label,
                color = colors.ink,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            ActionEntry("Open", onOpen)
            shortcuts.forEach { shortcut ->
                ActionEntry("· ${shortcut.label}") { onShortcut(shortcut) }
            }
            if (isPinned) ActionEntry("Unpin", onUnpin) else ActionEntry("Pin", onPin)
            if (isHidden) ActionEntry("Unhide", onUnhide) else ActionEntry("Hide", onHide)
            ActionEntry("Groups", onGroups)
            ActionEntry("Pause", onPause)
            ActionEntry("Set Open Check", onOpenCheck)
            ActionEntry("App info", onAppInfo)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionEntry(label: String, onClick: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text(
            text = label,
            color = colors.ink,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        )
    }
}
