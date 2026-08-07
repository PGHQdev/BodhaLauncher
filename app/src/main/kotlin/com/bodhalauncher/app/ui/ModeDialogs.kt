package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
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
import com.bodhalauncher.engine.ContextMode
import com.bodhalauncher.engine.ModeNameError
import com.bodhalauncher.engine.ScheduleWindow

/**
 * Context modes (#155, #156, ADR 0016). Management follows the built edit-mode
 * idiom — a plain Dialog, like Edit Home and the Open Check rule editor —
 * because a list with create, rename, reorder and delete is not ADR 0011's
 * one-decision sheet.
 */

/** Why a name was refused, named on the spot. */
internal fun modeNameMessage(error: ModeNameError): String = when (error) {
    ModeNameError.Blank -> "A mode needs a name"
    ModeNameError.TooLong -> "Names cap at 24 characters"
    ModeNameError.Duplicate -> "That name is taken"
}

/** A mode's window as its row's second line, or what its absence means (#156). */
internal fun modeWindowLine(window: ScheduleWindow?): String =
    window?.let(::scheduleWindowLine) ?: "No time window"

/**
 * The selector, from Home's mode label: the default arrangement, every mode,
 * and manage. The current choice takes the tinted fill; modes are hairline
 * rows (ADR 0025 rules 1 and 2).
 *
 * The rows say nothing about how the current mode became current: a manual
 * switch and a scheduled one look the same, and nothing announces when a switch
 * will lapse (#156).
 */
@Composable
fun ModeSelectorDialog(
    modes: List<ContextMode>,
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
            items(modes, key = { it.name }) { mode ->
                ListRow(
                    title = mode.name,
                    subtitle = mode.window?.let(::scheduleWindowLine),
                    onClick = { onPick(mode.name); onDismiss() },
                    tinted = mode.name == current,
                )
            }
            item {
                ListRow(title = "Manage modes", onClick = { onManage() }, trailing = { TrailingChevron() })
            }
        }
    }
}

/**
 * Manage: the mode list in the order that breaks window ties, each row opening
 * its own editor, and create beneath.
 */
@Composable
fun ModeManageDialog(
    modes: List<ContextMode>,
    onCreate: (String) -> ModeNameError?,
    onRename: (String, String) -> ModeNameError?,
    onDelete: (String) -> Unit,
    onSetWindow: (String, ScheduleWindow?) -> Unit,
    /** Negative moves the mode earlier, and earlier is what wins an overlap (#156). */
    onMove: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
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
            items(modes, key = { it.name }) { mode ->
                ListRow(
                    title = mode.name,
                    subtitle = modeWindowLine(mode.window),
                    onClick = { editing = mode.name },
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
    editing?.let { name ->
        // Read from the list rather than captured, so a rename or a move made
        // inside the editor is what the editor goes on showing.
        val mode = modes.firstOrNull { it.name == name }
        if (mode == null) {
            editing = null
            return@let
        }
        ModeEditorDialog(
            mode = mode,
            first = modes.first().name == name,
            last = modes.last().name == name,
            onRename = { onRename(name, it) },
            onSetWindow = { onSetWindow(name, it) },
            onMove = { by -> onMove(name, by); editing = null },
            onDelete = { onDelete(name); editing = null },
            onDismiss = { editing = null },
        )
    }
}

/**
 * One mode, whole: its name, the window it takes over in, where it sits in the
 * order, and delete.
 *
 * The content is separated from the dialog for the reason `OpenCheckSheetContent`
 * and `FocusSetupSheetContent` are: a fixture or a test can render it. Here the
 * split is load-bearing rather than tidy — a `BasicTextField` inside a `Dialog`
 * never lets Compose reach idle under Robolectric, so the keys can only be
 * injected into the content standing on its own.
 */
@Composable
private fun ModeEditorDialog(
    mode: ContextMode,
    first: Boolean,
    last: Boolean,
    onRename: (String) -> ModeNameError?,
    onSetWindow: (ScheduleWindow?) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .padding(24.dp),
        ) {
            ModeEditorContent(mode, first, last, onRename, onSetWindow, onMove, onDelete, onDismiss)
        }
    }
}

/**
 * Reordering is **move up and move down**, each a named row of its own — a drag
 * handle has no keyboard route (ADR 0022) and would owe a `// reachable:` marker
 * under ADR 0024's guard. A move closes the editor, because the row it was
 * opened from has moved beneath it.
 */
@Composable
internal fun ModeEditorContent(
    mode: ContextMode,
    first: Boolean,
    last: Boolean,
    onRename: (String) -> ModeNameError?,
    onSetWindow: (ScheduleWindow?) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    var editingWindow by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(mode.name) }
    var error by remember { mutableStateOf<ModeNameError?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (editingWindow) {
            WindowEditor(
                current = mode.window,
                // Its own words: the same window means "check here" for a rule
                // and "switch Home here" for a mode.
                prompt = "Switch to ${mode.name} between these times",
            ) { window ->
                onSetWindow(window)
                editingWindow = false
            }
            return@Column
        }
        Text(text = "Edit mode", color = colors.inkMuted, style = BodhaType.overline)
        Spacer(Modifier.height(16.dp))
        NameField(text, onChange = { text = it; error = null })
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = modeNameMessage(it), color = colors.error, style = BodhaType.caption)
        }
        Spacer(Modifier.height(8.dp))
        ListRow(
            title = "Time window",
            subtitle = modeWindowLine(mode.window),
            onClick = { editingWindow = true },
            trailing = { TrailingChevron() },
        )
        if (mode.window != null) {
            ListRow(title = "Remove time window", onClick = { onSetWindow(null) })
        }
        // Named for the mode, so a reader hears which one is moving; the row is
        // absent at the end it cannot move towards, rather than present and inert.
        if (!first) ListRow(title = "Move ${mode.name} up", onClick = { onMove(-1) })
        if (!last) ListRow(title = "Move ${mode.name} down", onClick = { onMove(1) })
        Spacer(Modifier.height(20.dp))
        Row {
            BodhaPill(label = "Delete", onClick = onDelete, destructive = true)
            Spacer(Modifier.weight(1f))
            BodhaPill(
                label = "Save",
                onClick = {
                    val refusal = onRename(text)
                    if (refusal == null) onDismiss() else error = refusal
                },
                emphasis = Emphasis.Solid,
            )
        }
    }
}

/**
 * One name, asked for at create. [onSubmit] returns the refusal, if any, and it
 * is named in place rather than the dialog closing over it.
 */
@Composable
private fun ModeNameDialog(
    title: String,
    initial: String,
    onSubmit: (String) -> ModeNameError?,
    onDismiss: () -> Unit,
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
            NameField(text, onChange = { text = it; error = null })
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = modeNameMessage(it), color = colors.error, style = BodhaType.caption)
            }
            Spacer(Modifier.height(20.dp))
            // Pills, so the floor and the focus ring come from the shared
            // component rather than a local outline (ADR 0025, 0026).
            Row {
                Spacer(Modifier.weight(1f))
                BodhaPill(
                    label = "Save",
                    onClick = {
                        val refusal = onSubmit(text)
                        if (refusal == null) onDismiss() else error = refusal
                    },
                    emphasis = Emphasis.Solid,
                )
            }
        }
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    val colors = LocalBodhaColors.current
    Column {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            // A multi-line field takes Tab as a tab character, which traps a
            // docked user in it and leaves every control below unreachable
            // (ADR 0022). A mode name is one line and capped at 24 anyway.
            singleLine = true,
            textStyle = BodhaType.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Mode name" }
                // Stays last: ADR 0020's caveat in BodhaTheme.kt.
                .touchTargetFloor(),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    }
}
