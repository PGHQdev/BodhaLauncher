package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bodhalauncher.engine.HomeAction

/** Minimal picker over all launchable apps, until the App Library (#7) exists. */
@Composable
fun AppPickerDialog(
    apps: List<HomeAction>,
    onPick: (HomeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Dialog(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .heightIn(max = 480.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            items(apps, key = { it.id }) { app ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = app.label,
                        color = colors.ink,
                        style = BodhaType.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .touchTargetFloor()
                            .clickable { onPick(app) }
                            .padding(vertical = 14.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                }
            }
        }
    }
}

/** Long-press options for an action: unpin a pin; pin or hide a suggestion. */
@Composable
fun ActionOptionsDialog(
    action: HomeAction,
    isPinned: Boolean,
    onPin: (HomeAction) -> Unit,
    onUnpin: (HomeAction) -> Unit,
    onHide: (HomeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = action.label,
                color = colors.inkMuted,
                style = BodhaType.overline,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (isPinned) {
                OptionRow("Unpin") { onUnpin(action); onDismiss() }
            } else {
                OptionRow("Pin") { onPin(action); onDismiss() }
                OptionRow("Hide") { onHide(action); onDismiss() }
            }
            // Stub until the recommendation engine (#6) has anything to explain.
            Text(
                text = "Explain — coming with suggestions",
                color = colors.inkMuted,
                style = BodhaType.body,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            )
        }
    }
}

/** Edit-mode entry point (#54); Context modes joined it in #155, Settings in #140. */
@Composable
fun EditHomeDialog(
    onAddPin: () -> Unit,
    onContextModes: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Edit Home",
                color = colors.inkMuted,
                style = BodhaType.overline,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OptionRow("Add pin") { onAddPin(); onDismiss() }
            OptionRow("Context modes") { onContextModes(); onDismiss() }
            // Settings hangs off edit mode and nowhere else (ADR 0011, ADR 0019):
            // Home's arrangement is already edited here, and Settings is the only
            // route back to the home role after a decline (ADR 0018).
            OptionRow("Settings") { onSettings(); onDismiss() }
            Text(
                text = "More editing — coming",
                color = colors.inkMuted,
                style = BodhaType.body,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun OptionRow(label: String, onClick: () -> Unit) {
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
