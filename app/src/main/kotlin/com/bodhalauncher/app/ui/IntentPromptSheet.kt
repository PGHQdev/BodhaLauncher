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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.IntentCategory

/**
 * The Intent Prompt: a dismissible bottom sheet, never a full-screen block.
 * Serif question (the voice), sans options (the machinery) — ADR 0010.
 * "Just looking" is a selection; swipe-down and tap-outside are dismissals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentPromptSheet(
    onSelect: (IntentCategory?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    var freeText by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.ground,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
            Text(
                text = "What are you here for?",
                color = colors.ink,
                style = BodhaType.voiceTitle,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            options.forEach { (category, label) ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                    Text(
                        text = label,
                        color = colors.ink,
                        style = BodhaType.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .touchTargetFloor()
                            // Any typed text rides along — the record keeps category + optional text.
                            .clickable { onSelect(category, freeText.trim().takeIf { it.isNotEmpty() }) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = freeText,
                    onValueChange = { freeText = it },
                    textStyle = BodhaType.voiceInput.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accent),
                    decorationBox = { field ->
                        if (freeText.isEmpty()) {
                            Text(
                                text = "Something else…",
                                color = colors.inkMuted,
                                style = BodhaType.voiceInput,
                            )
                        }
                        field()
                    },
                    modifier = Modifier.weight(1f),
                )
                if (freeText.isNotBlank()) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Go",
                        color = colors.accent,
                        style = BodhaType.action,
                        modifier = Modifier.touchTargetFloor()
                            .clickable { onSelect(null, freeText.trim()) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val options = listOf(
    IntentCategory.ContinueSomething to "Continue something",
    IntentCategory.Communicate to "Communicate",
    IntentCategory.Capture to "Capture",
    IntentCategory.FindSomething to "Find something",
    IntentCategory.Browse to "Browse",
    IntentCategory.JustLooking to "Just looking",
)
