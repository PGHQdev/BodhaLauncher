package com.bodhalauncher.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.ScheduleWindow

/**
 * One daily window, typed as two clock times; end before start crosses midnight
 * (#74).
 *
 * Lifted out of the Open Check rule dialog when context modes became its second
 * caller (#156, ADR 0016): a mode's window is the same `ScheduleWindow` and the
 * same question, so a copy would be two editors drifting apart over one value.
 * The prompt is the caller's, because that is the only part that differs.
 */
@Composable
fun WindowEditor(current: ScheduleWindow?, prompt: String, onSave: (ScheduleWindow) -> Unit) {
    val colors = LocalBodhaColors.current
    var start by remember { mutableStateOf(current?.startMinute?.let(::clockText).orEmpty()) }
    var end by remember { mutableStateOf(current?.endMinute?.let(::clockText).orEmpty()) }
    val startMinute = parseClock(start)
    val endMinute = parseClock(end)
    Text(
        text = prompt,
        color = colors.inkMuted,
        style = BodhaType.caption,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimeField(start, "Start time", "21:00", { start = it }, Modifier.weight(1f))
        Spacer(Modifier.width(BodhaSpacing.m))
        TimeField(end, "End time", "23:30", { end = it }, Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = BodhaSpacing.l)) {
        Spacer(Modifier.weight(1f))
        BodhaPill(
            label = "Save",
            // Equal times would be an empty window — a silently inert rule; Always is the mode for that.
            enabled = startMinute != null && endMinute != null && startMinute != endMinute,
            emphasis = Emphasis.Solid,
            onClick = { onSave(ScheduleWindow(startMinute!!, endMinute!!)) },
        )
    }
}

/**
 * A clock time, in the vocabulary's field (ADR 0025 rule 4) — so the floor, the
 * hairline and the focus ring all come from the shared component rather than
 * from an outline drawn here (ADR 0026).
 */
@Composable
private fun TimeField(
    value: String,
    /** What this field edits, for a reader that cannot see which of the two it is. */
    label: String,
    placeholder: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalBodhaColors.current
    BodhaField(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = BodhaType.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { field ->
                if (value.isEmpty()) Text(placeholder, color = colors.inkMuted, style = BodhaType.body)
                field()
            },
            // The name belongs on the field's own node, not on BodhaField's Row —
            // see BodhaField. Stays last: ADR 0020's caveat in BodhaTheme.kt.
            modifier = Modifier
                .semantics { contentDescription = label }
                .touchTargetFloor(),
        )
    }
}

/** A window as one readable span — what a row shows when it is not being edited. */
fun scheduleWindowLine(window: ScheduleWindow): String =
    "${clockText(window.startMinute)} to ${clockText(window.endMinute)}"

internal fun clockText(minuteOfDay: Int): String = "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

internal fun parseClock(text: String): Int? {
    val (h, m) = text.trim().split(':').takeIf { it.size == 2 } ?: return null
    val hour = h.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = m.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return hour * 60 + minute
}
