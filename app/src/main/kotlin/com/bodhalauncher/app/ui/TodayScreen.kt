package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.DayEvent
import com.bodhalauncher.engine.DaySlot
import com.bodhalauncher.engine.DigestSection
import com.bodhalauncher.engine.DigestSlot
import com.bodhalauncher.engine.digestLine
import com.bodhalauncher.engine.formatDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Today (#158, #159, ADR 0017): the day key's date as a header, then the
 * intention slot, then the day slot. Renders what it is given and nothing else;
 * the day key comes from the engine, so the header and the slots cannot
 * disagree about what day it is.
 */
@Composable
fun TodayScreen(
    day: LocalDate,
    intention: String?,
    onEditIntention: () -> Unit,
    /** Null only while the provider is still being read. */
    daySlot: DaySlot?,
    onEventTap: (DayEvent) -> Unit,
    onDayTurnOn: () -> Unit,
    /** Null only while the day key's counts are still being read. */
    digestSlot: DigestSlot?,
    onDigestTap: () -> Unit,
    onDigestTurnOn: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            // The day has no cap and no horizon (ADR 0017); an overflowing one scrolls.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        // The day key in the chosen date form (#141) — the same form Home's
        // clock writes its date in, so the two never disagree about how a date
        // is spelled.
        Text(
            text = formatDate(day, LocalBodhaFormats.current.date),
            color = colors.ink,
            style = BodhaType.title,
        )
        Spacer(Modifier.height(36.dp))
        // The slot is a block acted on once (ADR 0025); tinted when it holds
        // the current thing, plain in its named empty state.
        CardRow(
            title = intention ?: "Set today's intention",
            onClick = onEditIntention,
            emphasis = if (intention != null) Emphasis.Tinted else Emphasis.Plain,
            // Focus on arrival gives the surface a back key (ADR 0022).
            modifier = Modifier.focusOnOpen(),
        )
        daySlot?.let {
            Spacer(Modifier.height(BodhaSpacing.l))
            DaySlotContent(it, onEventTap, onDayTurnOn)
        }
        digestSlot?.let {
            Spacer(Modifier.height(BodhaSpacing.l))
            DigestSlotContent(it, onDigestTap, onDigestTurnOn)
        }
    }
}

/**
 * The digest slot (#161, ADR 0015): counts for the day, no app names, no
 * previews, and a named cause for every absence. A card with a chevron because
 * tapping it navigates to the inbox (ADR 0025); the counts stay when the
 * listener drops or the grant is revoked, and the subtitle says why.
 */
@Composable
private fun DigestSlotContent(
    slot: DigestSlot,
    onTap: () -> Unit,
    onTurnOn: () -> Unit,
) {
    when (slot) {
        is DigestSlot.Ungranted ->
            if (slot.offersTurnOn) {
                CardRow(
                    title = "Today's notifications, counted",
                    subtitle = "Turn on notification access",
                    onClick = onTurnOn,
                )
            } else {
                SlotNote("Notification access is off.")
            }
        DigestSlot.Empty -> DigestCard(subtitle = "Nothing waiting today.", onTap = onTap)
        is DigestSlot.Counts -> DigestCard(subtitle = digestLine(slot.counts), onTap = onTap)
        is DigestSlot.Disconnected -> DigestCard(
            subtitle = withCause(slot.counts, "the listener is disconnected"),
            onTap = onTap,
        )
        is DigestSlot.Revoked -> DigestCard(
            subtitle = withCause(slot.counts, "access was turned off"),
            onTap = onTap,
        )
    }
}

private fun withCause(counts: Map<DigestSection, Int>, cause: String): String =
    if (counts.isEmpty()) cause.replaceFirstChar(Char::uppercase) + "."
    else "${digestLine(counts)} — $cause."

@Composable
fun DigestCard(subtitle: String, onTap: () -> Unit) {
    CardRow(
        title = "Notifications",
        subtitle = subtitle,
        onClick = onTap,
        trailing = { TrailingChevron() },
    )
}

/**
 * The day slot (#159): what is left of the day, or the named cause of its
 * absence — never a blank, never a zero (ADR 0017).
 */
@Composable
private fun DaySlotContent(
    slot: DaySlot,
    onEventTap: (DayEvent) -> Unit,
    onTurnOn: () -> Unit,
) {
    when (slot) {
        is DaySlot.Ungranted ->
            if (slot.offersTurnOn) {
                CardRow(
                    title = "See what's left of your day",
                    subtitle = "Turn on calendar access",
                    onClick = onTurnOn,
                )
            } else {
                SlotNote("Calendar access is off.")
            }
        DaySlot.NoCalendars -> SlotNote("No calendars are set up on this phone.")
        is DaySlot.Empty -> {
            SlotNote("Nothing left on the calendar today.")
            // The bounded peek (#160): with the day spent, tomorrow's first
            // event under its own label; with tomorrow empty too, nothing.
            slot.tomorrowFirst?.let { peek ->
                Spacer(Modifier.height(BodhaSpacing.l))
                SectionOverline("Tomorrow")
                DayEventRow(event = peek, onClick = { onEventTap(peek) })
            }
        }
        is DaySlot.Events -> Column(Modifier.fillMaxWidth()) {
            for (event in slot.events) {
                DayEventRow(event = event, onClick = { onEventTap(event) })
            }
        }
    }
}

/** One remaining event; tapping opens it in the calendar app (#159). */
@Composable
fun DayEventRow(event: DayEvent, onClick: () -> Unit) {
    ListRow(
        title = event.title,
        subtitle = if (event.allDay) "All day" else {
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            "${event.begin.format(time)} – ${event.end.format(time)}"
        },
        onClick = onClick,
    )
}

/** A named absence: a plain sentence in the slot's place, deliberately not actionable. */
@Composable
fun SlotNote(text: String) {
    val colors = LocalBodhaColors.current
    BodhaCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = BodhaType.body,
            color = colors.inkMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BodhaSpacing.m, vertical = BodhaSpacing.m),
        )
    }
}
