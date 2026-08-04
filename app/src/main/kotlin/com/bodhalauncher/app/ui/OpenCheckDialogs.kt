package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.ProBoundary

/**
 * UI copy per trigger mode, decoupled from the enum names the store persists —
 * the exhaustive when forces every future mode to bring its own words.
 */
internal fun openCheckModeLabel(mode: OpenCheckMode): String = when (mode) {
    OpenCheckMode.Always -> "Always"
    OpenCheckMode.Never -> "Never"
}

/**
 * Edits an app's Open Check rule: pick a trigger mode, or remove the rule.
 * [current] is null when no rule exists yet — picking a mode then creates one
 * (the caller gates creation). The remaining trigger modes (#8) join
 * [OpenCheckMode] with their own tickets and appear here for free.
 */
@Composable
fun OpenCheckRuleDialog(
    app: HomeAction,
    current: OpenCheckMode?,
    onSelect: (OpenCheckMode) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Open Check — ${app.label}",
                color = colors.inkMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OpenCheckMode.entries.forEach { mode ->
                val chosen = mode == current
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                    Text(
                        // Wording over glyphs: ADR 0010 keeps marks off the machinery rows.
                        text = openCheckModeLabel(mode).let { if (chosen) "$it · chosen" else it },
                        color = if (chosen) colors.ink else colors.inkMuted,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode); onDismiss() }
                            .padding(vertical = 14.dp),
                    )
                }
            }
            if (current != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                    Text(
                        text = "Remove rule",
                        color = colors.ink,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRemove(); onDismiss() }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
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
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            lineHeight = 28.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .clickable(onClick = onDismiss)
                .padding(28.dp),
        )
    }
}
