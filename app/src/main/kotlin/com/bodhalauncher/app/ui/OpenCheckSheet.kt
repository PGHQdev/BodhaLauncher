package com.bodhalauncher.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.OpenCheckEngine
import com.bodhalauncher.engine.OpenCheckLines

/**
 * The Open Check (#8): a gentle pause before a checked app, never a wall.
 * Serif question (the voice), sans rows (the machinery) — ADR 0010. No
 * countdown, no guilt copy, no red, nothing pre-focused; swipe-down,
 * tap-outside and the back gesture all turn back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenCheckSheet(
    app: HomeAction,
    icon: ImageBitmap?,
    lines: OpenCheckLines,
    /** Explicit ask for usage access when the context lines are off; null hides the note (#18). */
    onContextNoteTap: (() -> Unit)?,
    onOpen: () -> Unit,
    /** Open for a chosen number of minutes — the timed session (#75). */
    onOpenFor: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.ground) {
        OpenCheckSheetContent(
            appLabel = app.label,
            icon = icon,
            lines = lines,
            onContextNoteTap = onContextNoteTap,
            onOpen = onOpen,
            onOpenFor = onOpenFor,
            onGoBack = onDismiss,
        )
    }
}

/** The sheet's face, separate so the screenshot gate can photograph it (#26). */
@Composable
fun OpenCheckSheetContent(
    appLabel: String,
    icon: ImageBitmap?,
    lines: OpenCheckLines = OpenCheckLines(null, null),
    onContextNoteTap: (() -> Unit)? = null,
    onOpen: () -> Unit,
    onOpenFor: (Long) -> Unit = {},
    onGoBack: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = appLabel,
                color = colors.ink,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
            )
        }
        lines.lastOpened?.let { ContextLine(it) }
        lines.usedToday?.let { ContextLine(it) }
        if (onContextNoteTap != null) {
            Text(
                text = "Context needs usage access",
                color = colors.inkMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(onClick = onContextNoteTap)
                    .padding(bottom = 8.dp),
            )
        }
        Text(
            text = "Still want to open it?",
            color = colors.inkMuted,
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SheetRow("Open", onOpen)
        OpenCheckEngine.TIMED_SESSION_MINUTES.forEach { minutes ->
            SheetRow("Open for $minutes minutes") { onOpenFor(minutes) }
        }
        SheetRow("Go back", onGoBack)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ContextLine(text: String) {
    val colors = LocalBodhaColors.current
    Text(
        text = text,
        color = colors.inkMuted,
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
