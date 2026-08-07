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
    daySlot: DaySlot,
    onEventTap: (DayEvent) -> Unit,
    onDayTurnOn: () -> Unit,
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
        // The device locale's medium date form; Settings' date-format control
        // (#141) reads into this header when it lands.
        Text(
            text = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
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
        Spacer(Modifier.height(16.dp))
        DaySlotContent(daySlot, onEventTap, onDayTurnOn)
    }
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
        DaySlot.Empty -> SlotNote("Nothing left on the calendar today.")
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
