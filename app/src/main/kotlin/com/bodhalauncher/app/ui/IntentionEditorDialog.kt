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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Temporary intention editor on Home; Today (#5) becomes the real editor. */
@Composable
fun IntentionEditorDialog(
    current: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    var text by remember { mutableStateOf(current.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .padding(24.dp),
        ) {
            Text(text = "Today", color = colors.inkMuted, style = BodhaType.overline)
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                // The daily intention reads back on Home in this same role, so
                // writing it and living with it look like one thing (ADR 0021).
                textStyle = BodhaType.voiceLine.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(20.dp))
            Row {
                if (current != null) {
                    Text(
                        text = "Clear",
                        color = colors.inkMuted,
                        style = BodhaType.action,
                        modifier = Modifier.touchTargetFloor()
                            .clickable { onClear(); onDismiss() },
                    )
                    Spacer(Modifier.width(28.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Save",
                    color = colors.accent,
                    style = BodhaType.action,
                    modifier = Modifier.touchTargetFloor().clickable(enabled = text.isNotBlank()) {
                        onSave(text.trim())
                        onDismiss()
                    },
                )
            }
        }
    }
}
