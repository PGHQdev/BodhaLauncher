package com.bodhalauncher.engine

import java.time.LocalDateTime

/**
 * One expanded instance as the calendar provider reports it (#159). The edge
 * only reads and converts times; whether a row shows is decided here, so
 * declined and hidden-calendar filtering is testable without a device.
 */
data class ProviderInstance(
    val eventId: Long,
    val title: String,
    val allDay: Boolean,
    val begin: LocalDateTime,
    val end: LocalDateTime,
    val calendarVisible: Boolean,
    val selfDeclined: Boolean,
)

/** A row the day slot shows: enough to render and to open the event in the calendar app. */
data class DayEvent(
    val eventId: Long,
    val title: String,
    val allDay: Boolean,
    val begin: LocalDateTime,
    val end: LocalDateTime,
)

/**
 * The day slot's state (#159, ADR 0017). Every absence is a named cause, never
 * a blank: the slot always knows why it shows what it shows.
 */
sealed interface DaySlot {
    /**
     * No calendar grant. [offersTurnOn] follows the capability rule: a first
     * feature touch may educate; once educated and declined, the slot rests on
     * its named state with no inert control — the way back is Settings (#149).
     */
    data class Ungranted(val offersTurnOn: Boolean) : DaySlot

    /** The provider reports no calendar rows at all — Bodha has nothing to read. */
    data object NoCalendars : DaySlot

    /** Granted, calendars exist, and nothing of the day is left. */
    data object Empty : DaySlot

    /** What is left of the day, earliest first, all-day rows above timed ones. */
    data class Events(val events: List<DayEvent>) : DaySlot
}

/** All-day rows sort above timed ones; within each, earliest first (ADR 0017). */
private val dayOrder = compareByDescending<DayEvent> { it.allDay }
    .thenBy { it.begin }
    .thenBy { it.end }
    .thenBy { it.title }

/**
 * Resolves the day slot (#159): a pure reducer over provider rows. Declined
 * instances and hidden calendars drop; an instance whose end has passed is
 * gone; one in progress — including one that began before the current day
 * key — stays until it ends. Nothing is cached: [instances] is a live read.
 */
fun resolveDaySlot(
    granted: Boolean,
    educationShown: Boolean,
    hasCalendars: Boolean,
    instances: List<ProviderInstance>,
    now: LocalDateTime,
): DaySlot {
    if (!granted) return DaySlot.Ungranted(offersTurnOn = !educationShown)
    if (!hasCalendars) return DaySlot.NoCalendars

    val remaining = instances.asSequence()
        .filter { it.calendarVisible && !it.selfDeclined }
        // An all-day event's exclusive end is a midnight; it belongs to the day
        // key until the key rolls at 4am, not until the clock strikes twelve.
        .filter { if (it.allDay) it.end.toLocalDate() > dayKey(now) else it.end > now }
        .filter { it.begin < dayStart(now).plusDays(1) }
        .map { DayEvent(it.eventId, it.title, it.allDay, it.begin, it.end) }
        .sortedWith(dayOrder)
        .toList()

    return if (remaining.isEmpty()) DaySlot.Empty else DaySlot.Events(remaining)
}
