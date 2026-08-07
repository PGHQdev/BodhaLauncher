package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.OpenCheckRule
import com.bodhalauncher.engine.ProBoundary
import com.bodhalauncher.engine.ScheduleWindow
import java.time.Duration

/**
 * UI copy per trigger mode, decoupled from the enum names the store persists —
 * the exhaustive when forces every future mode to bring its own words.
 */
internal fun openCheckModeLabel(mode: OpenCheckMode): String = when (mode) {
    OpenCheckMode.Always -> "Always"
    OpenCheckMode.RepeatedOpening -> "Repeated opening"
    OpenCheckMode.DailyThreshold -> "After daily use"
    OpenCheckMode.Schedule -> "On a schedule"
    OpenCheckMode.Never -> "Never"
}

/** The fixed v1 daily allowances (#73); Settings defaults later, per ADR 0004's precedent. */
private val THRESHOLD_CHOICES = listOf(
    Duration.ofMinutes(15) to "15 minutes",
    Duration.ofMinutes(30) to "30 minutes",
    Duration.ofHours(1) to "1 hour",
    Duration.ofHours(2) to "2 hours",
)

/**
 * Edits an app's Open Check rule: pick a trigger mode, or remove the rule.
 * [current] is null when no rule exists yet — picking a mode then creates one
 * (the caller gates creation). Threshold and schedule modes ask for their
 * config in a second step before saving.
 */
@Composable
fun OpenCheckRuleDialog(
    app: HomeAction,
    current: OpenCheckRule?,
    onSave: (OpenCheckRule) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    var configuring by remember { mutableStateOf<OpenCheckMode?>(null) }
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
                text = "Open Check — ${app.label}",
                color = colors.inkMuted,
                style = BodhaType.overline,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            when (configuring) {
                OpenCheckMode.DailyThreshold -> ThresholdChoices { threshold ->
                    onSave(OpenCheckRule(OpenCheckMode.DailyThreshold, dailyThreshold = threshold))
                    onDismiss()
                }
                OpenCheckMode.Schedule -> WindowEditor(
                    current = current?.window,
                    prompt = "Check between these times",
                ) { window ->
                    onSave(OpenCheckRule(OpenCheckMode.Schedule, window = window))
                    onDismiss()
                }
                else -> ModeChoices(
                    current = current,
                    onPick = { mode ->
                        when (mode) {
                            OpenCheckMode.DailyThreshold, OpenCheckMode.Schedule -> configuring = mode
                            else -> { onSave(OpenCheckRule(mode)); onDismiss() }
                        }
                    },
                    onRemove = if (current != null) ({ onRemove(); onDismiss() }) else null,
                )
            }
        }
    }
}

@Composable
private fun ModeChoices(
    current: OpenCheckRule?,
    onPick: (OpenCheckMode) -> Unit,
    onRemove: (() -> Unit)?,
) {
    OpenCheckMode.entries.forEach { mode ->
        val chosen = mode == current?.mode
        // Wording over glyphs: ADR 0010 keeps marks off the machinery rows.
        DialogRow(
            label = openCheckModeLabel(mode).let { if (chosen) "$it · chosen" else it },
            muted = !chosen,
        ) { onPick(mode) }
    }
    if (onRemove != null) DialogRow("Remove rule", muted = false, onClick = onRemove)
}

@Composable
private fun ThresholdChoices(onPick: (Duration) -> Unit) {
    val colors = LocalBodhaColors.current
    Text(
        text = "Check after this much use today",
        color = colors.inkMuted,
        style = BodhaType.caption,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    THRESHOLD_CHOICES.forEach { (threshold, label) ->
        DialogRow(label, muted = false) { onPick(threshold) }
    }
}

@Composable
private fun DialogRow(label: String, muted: Boolean, onClick: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text(
            text = label,
            color = if (muted) colors.inkMuted else colors.ink,
            style = BodhaType.body,
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        )
    }
}

/**
 * States a Pro boundary in the launcher's voice (#22): the calm explanation,
 * serif, nothing else — no urgency, no upsell button until billing lands.
 */
@Composable
fun ProBoundaryDialog(boundary: ProBoundary, onDismiss: () -> Unit) {
    val colors = LocalBodhaColors.current
    Dialog(onDismissRequest = onDismiss) {
        Text(
            text = boundary.explanation,
            color = colors.ink,
            style = BodhaType.voicePassage,
            modifier = Modifier
                .fillMaxWidth()
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .touchTargetFloor()
                .clickable(onClick = onDismiss)
                .padding(28.dp),
        )
    }
}
