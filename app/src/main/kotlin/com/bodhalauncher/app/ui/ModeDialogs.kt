package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bodhalauncher.engine.ModeNameError

/**
 * Context modes (#155, ADR 0016). Management follows the built edit-mode idiom
 * — a plain Dialog, like Edit Home and the Open Check rule editor — because a
 * list with create, rename and delete is not ADR 0011's one-decision sheet.
 */

/** Why a name was refused, named on the spot. */
internal fun modeNameMessage(error: ModeNameError): String = when (error) {
    ModeNameError.Blank -> "A mode needs a name"
    ModeNameError.TooLong -> "Names cap at 24 characters"
    ModeNameError.Duplicate -> "That name is taken"
}

/**
 * The selector, from Home's mode label: the default arrangement, every mode,
 * and manage. The current choice takes the tinted fill; modes are hairline
 * rows (ADR 0025 rules 1 and 2).
 */
@Composable
fun ModeSelectorDialog(
    modes: List<String>,
    /** Null while the default arrangement is the choice. */
    current: String?,
    onPick: (String?) -> Unit,
    onManage: () -> Unit,
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
            item {
                Text(
                    text = "Context modes",
                    color = colors.inkMuted,
                    style = BodhaType.overline,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                ListRow(
                    title = "Default",
                    onClick = { onPick(null); onDismiss() },
                    tinted = current == null,
                )
            }
            items(modes, key = { it }) { mode ->
                ListRow(
                    title = mode,
                    onClick = { onPick(mode); onDismiss() },
                    tinted = mode == current,
                )
            }
            item {
                ListRow(title = "Manage modes", onClick = { onManage() }, trailing = { TrailingChevron() })
            }
        }
    }
}

/**
 * Manage: the mode list with rename and delete a tap away, and create beneath.
 */
@Composable
fun ModeManageDialog(
    modes: List<String>,
    onCreate: (String) -> ModeNameError?,
    onRename: (String, String) -> ModeNameError?,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
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
            item {
                Text(
                    text = "Context modes",
                    color = colors.inkMuted,
                    style = BodhaType.overline,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            items(modes, key = { it }) { mode ->
                ListRow(
                    title = mode,
                    onClick = { renaming = mode },
                    trailing = { TrailingChevron() },
                )
            }
            item {
                ListRow(title = "New mode", onClick = { creating = true })
            }
        }
    }
    if (creating) {
        ModeNameDialog(
            title = "New mode",
            initial = "",
            onSubmit = onCreate,
            onDismiss = { creating = false },
        )
    }
    renaming?.let { mode ->
        ModeNameDialog(
            title = "Rename mode",
            initial = mode,
            onSubmit = { onRename(mode, it) },
            onDelete = { onDelete(mode); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

/**
 * One name, asked for at create and rename. [onSubmit] returns the refusal, if
 * any, and it is named in place rather than the dialog closing over it.
 */
@Composable
private fun ModeNameDialog(
    title: String,
    initial: String,
    onSubmit: (String) -> ModeNameError?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colors = LocalBodhaColors.current
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<ModeNameError?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .padding(24.dp),
        ) {
            Text(text = title, color = colors.inkMuted, style = BodhaType.overline)
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it; error = null },
                textStyle = BodhaType.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Mode name" }
                    .touchTargetFloor(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = modeNameMessage(it), color = colors.error, style = BodhaType.caption)
            }
            Spacer(Modifier.height(20.dp))
            Row {
                if (onDelete != null) {
                    Text(
                        text = "Delete",
                        color = colors.error,
                        style = BodhaType.action,
                        modifier = Modifier.touchTargetFloor().clickable(onClick = onDelete),
                    )
                    Spacer(Modifier.width(28.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Save",
                    color = colors.accent,
                    style = BodhaType.action,
                    modifier = Modifier.touchTargetFloor().clickable {
                        val refusal = onSubmit(text)
                        if (refusal == null) onDismiss() else error = refusal
                    },
                )
            }
        }
    }
}
