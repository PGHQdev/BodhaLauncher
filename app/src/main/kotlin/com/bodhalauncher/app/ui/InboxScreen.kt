package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.InboxRow
import com.bodhalauncher.engine.InboxState

/**
 * The inbox (#162, ADR 0015): the live shade, grouped under the digest's four
 * sections in their fixed order. Rows describe now — a row is one live
 * notification, gone when it goes — while the digest keeps counting the day.
 * Renders what it is given; nothing here reads or stores anything.
 *
 * Back and Escape return Home through the navigation model's single binding
 * (#132); this screen binds neither.
 */
@Composable
fun InboxScreen(
    /** Null only while the first resolution is still being read. */
    state: InboxState?,
    /** A row's spoken and shown name — never empty; the surface falls back to the app's label. */
    titleFor: (InboxRow) -> String,
    lineFor: (InboxRow) -> String?,
    iconFor: (InboxRow) -> ImageBitmap?,
    onOpen: (InboxRow) -> Unit,
    onBack: () -> Unit,
) {
    when (state) {
        null -> Unit
        // The three named absences (#162): never a blank list. Like the
        // placeholder surfaces, the whole surface is one named node so arrival
        // focuses something and Escape has a chain to travel up (ADR 0022).
        InboxState.AccessOff -> InboxNote("Notification access is off.", onBack)
        InboxState.Disconnected -> InboxNote("Not connected to notifications right now.", onBack)
        InboxState.Empty -> InboxNote("Nothing waiting.", onBack)
        is InboxState.Sections -> InboxSections(state, titleFor, lineFor, iconFor, onOpen)
    }
}

@Composable
private fun InboxSections(
    state: InboxState.Sections,
    titleFor: (InboxRow) -> String,
    lineFor: (InboxRow) -> String?,
    iconFor: (InboxRow) -> ImageBitmap?,
    onOpen: (InboxRow) -> Unit,
) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = BodhaSpacing.l),
    ) {
        // A surface name is a label, not a spoken line (ADR 0021).
        Text("Notifications", color = colors.ink, style = BodhaType.title)
        Spacer(Modifier.height(BodhaSpacing.l))
        var first = true
        state.sections.forEach { section ->
            SectionOverline(section.section.label)
            section.rows.forEach { row ->
                NotificationRow(
                    title = titleFor(row),
                    line = lineFor(row),
                    icon = iconFor(row),
                    onOpen = { onOpen(row) },
                    // Focus on arrival gives the surface a back key (ADR 0022).
                    modifier = if (first) Modifier.focusOnOpen() else Modifier,
                )
                first = false
            }
            Spacer(Modifier.height(BodhaSpacing.l))
        }
    }
}

/**
 * One live notification: the vocabulary's hairline row because the inbox
 * scrolls, the source app's own mark bare rather than in a chip (ADR 0025).
 * Tapping fires the notification's original content intent directly.
 */
@Composable
fun NotificationRow(
    title: String,
    line: String?,
    icon: ImageBitmap?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        title = title,
        subtitle = line,
        onClick = onOpen,
        leading = icon?.let { { AppMark(it) } },
        modifier = modifier,
    )
}

/** A named absence filling the surface, tappable to leave — the placeholder's own pattern. */
@Composable
private fun InboxNote(text: String, onBack: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .semantics { contentDescription = "$text Tap to go back." }
            .focusOnOpen()
            .clickable(onClick = onBack),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Notifications", color = colors.ink, style = BodhaType.title)
        Spacer(Modifier.height(BodhaSpacing.m))
        Text(text, color = colors.inkMuted, style = BodhaType.body)
    }
}
