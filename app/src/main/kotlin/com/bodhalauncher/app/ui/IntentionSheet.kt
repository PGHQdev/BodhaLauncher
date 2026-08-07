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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Today's intention editor (#158, ADR 0011): one decision, dismissible, at most
 * two footer actions. Dismissing leaves the intention as it was; only Save and
 * Clear change it. [suggestion] is the previous day's text, offered one-tap
 * when setting a new intention (ADR 0003).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentionSheet(
    current: String?,
    suggestion: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.ground,
    ) {
        IntentionSheetContent(
            current = current,
            suggestion = suggestion,
            onSave = onSave,
            onClear = onClear,
            modifier = Modifier.escapeDismisses(onDismiss).focusOnOpenWithIme(),
        )
    }
}

@Composable
internal fun IntentionSheetContent(
    current: String?,
    suggestion: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBodhaColors.current
    var text by remember { mutableStateOf(current.orEmpty()) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Text(
            // The intention is voice; this label is machinery (ADR 0021).
            text = if (current == null) "Set today's intention" else "Today's intention",
            color = colors.inkMuted,
            style = BodhaType.overline,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            // Writing the intention and living with it look like one thing (ADR 0021).
            textStyle = BodhaType.voiceLine.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            modifier = modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Today's intention" }
                .touchTargetFloor(),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        if (current == null && suggestion != null) {
            Spacer(Modifier.height(20.dp))
            BodhaPill(
                label = "Yesterday: $suggestion",
                onClick = { onSave(suggestion) },
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            if (current != null) {
                Text(
                    text = "Clear",
                    color = colors.inkMuted,
                    style = BodhaType.action,
                    modifier = Modifier.touchTargetFloor().clickable(onClick = onClear),
                )
                Spacer(Modifier.width(28.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Save",
                color = colors.accent,
                style = BodhaType.action,
                modifier = Modifier.touchTargetFloor()
                    .clickable(enabled = text.isNotBlank()) { onSave(text.trim()) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
