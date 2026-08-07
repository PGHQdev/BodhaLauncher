package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.FOCUS_DURATION_MINUTES
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.focusStartEnabled

/**
 * Focus setup (#166, ADR 0011): one decision — label, duration, allowed apps —
 * dismissible with nothing started, one footer action. The durations are the
 * fixed three; an empty allowed list is a valid choice, a blank label is not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSetupSheet(
    apps: List<HomeAction>,
    onStart: (label: String, minutes: Long, allowedAppIds: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.ground) {
        FocusSetupSheetContent(
            apps = apps,
            onStart = onStart,
            modifier = Modifier.escapeDismisses(onDismiss).focusOnOpenWithIme(),
        )
    }
}

/** The sheet's face, separate so the screenshot gate can photograph it (#26). */
@Composable
internal fun FocusSetupSheetContent(
    apps: List<HomeAction>,
    onStart: (label: String, minutes: Long, allowedAppIds: Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    initialLabel: String = "",
    initialMinutes: Long = 30,
    initialAllowed: List<String> = emptyList(),
) {
    val colors = LocalBodhaColors.current
    var label by remember { mutableStateOf(initialLabel) }
    var minutes by remember { mutableStateOf(initialMinutes) }
    val allowed = remember { initialAllowed.toMutableStateList() }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Text(
            // Bodha's ask is voice; the field beneath takes the user's word (ADR 0021).
            text = "What are you focusing on?",
            color = colors.ink,
            style = BodhaType.voiceTitle,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        BodhaField {
            BasicTextField(
                value = label,
                onValueChange = { label = it },
                // Single-line keeps Tab a traversal key rather than a character (ADR 0022).
                singleLine = true,
                textStyle = BodhaType.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                modifier = modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Activity" }
                    .touchTargetFloor(),
            )
        }
        Spacer(Modifier.height(BodhaSpacing.l))
        Row(horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.s)) {
            FOCUS_DURATION_MINUTES.forEach { option ->
                BodhaPill(
                    label = "$option min",
                    onClick = { minutes = option },
                    // Tinted marks the current choice (ADR 0025), never a second ink.
                    emphasis = if (minutes == option) Emphasis.Tinted else Emphasis.Plain,
                )
            }
        }
        Spacer(Modifier.height(BodhaSpacing.l))
        Text(text = "ALLOWED APPS", color = colors.inkMuted, style = BodhaType.overline)
        // The shared multi-select row (#137), scrolling inside the sheet; leaving
        // every row unpicked is the everything-is-checked session (#166).
        LazyColumn(Modifier.heightIn(max = 240.dp)) {
            items(apps, key = { it.id }) { app ->
                MultiSelectRow(
                    title = app.label,
                    picked = app.id in allowed,
                    onToggle = { if (app.id in allowed) allowed.remove(app.id) else allowed.add(app.id) },
                )
            }
        }
        Spacer(Modifier.height(BodhaSpacing.l))
        BodhaPill(
            label = "Start",
            onClick = { onStart(label, minutes, allowed.toSet()) },
            emphasis = Emphasis.Solid,
            // The end moment and the record have nothing to name without a label.
            enabled = focusStartEnabled(label),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The end moment (#170, ADR 0012): what they focused on, how long, one neutral
 * line for reaching elsewhere, with extend and done. It waited for root, so the
 * duration line owns however late it is. Nothing here fights the user:
 * dismissing is closing, and the moment never returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusEndSheet(
    label: String,
    durationLine: String,
    reachLine: String,
    onExtend: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.ground) {
        Box(Modifier.escapeDismisses(onDismiss).focusOnOpen()) {
            FocusEndSheetContent(
                label = label,
                durationLine = durationLine,
                reachLine = reachLine,
                onExtend = onExtend,
                onDone = onDone,
            )
        }
    }
}

/** The sheet's face, separate so the screenshot gate can photograph it (#26). */
@Composable
internal fun FocusEndSheetContent(
    label: String,
    durationLine: String,
    reachLine: String,
    onExtend: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        // The activity label is the user's word — the sans (ADR 0021).
        Text(
            text = label,
            color = colors.ink,
            style = BodhaType.title,
            modifier = Modifier.padding(top = 12.dp),
        )
        // Bodha's two lines are the serif: the span, then the one neutral fact.
        Text(
            text = durationLine,
            color = colors.ink,
            style = BodhaType.voiceLine,
            modifier = Modifier.padding(top = BodhaSpacing.s),
        )
        Text(
            text = reachLine,
            color = colors.inkMuted,
            style = BodhaType.voiceLine,
            modifier = Modifier.padding(top = BodhaSpacing.xs, bottom = 12.dp),
        )
        SheetRow("Add 10 minutes", onExtend)
        SheetRow("Done", onDone)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Spacer(Modifier.height(24.dp))
    }
}
