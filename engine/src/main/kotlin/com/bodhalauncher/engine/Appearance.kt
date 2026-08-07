package com.bodhalauncher.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Light, Dark or System (ADR 0010), free at every tier. Dark is the designed
 * warm-charcoal counterpart the theme already carries, never an inversion, so
 * this enum decides which palette is asked for and no palette values at all.
 */
enum class ThemeChoice { Light, Dark, System }

/**
 * How Bodha writes a wall-clock time (ADR 0010) — a content setting rather than
 * identity, which is what lets it be offered at all (ADR 0019's first test).
 *
 * NATO is the four-digit military form, `0941`. Twenty-four hour is left
 * unpadded because that is what Home already drew, and the two are then
 * distinguishable at every hour rather than only before ten — a format someone
 * chose has to look chosen.
 */
enum class ClockFormat(internal val pattern: String) {
    TwelveHour("h:mm a"),
    TwentyFourHour("H:mm"),
    Nato("HHmm"),
}

/**
 * How Bodha writes a date (#141). ADR 0010 asks for "configurable date/day
 * formats" and names none; these three are the ticket's own, picked so the set
 * spans the three reasons anyone wants one — a form that reads aloud, a compact
 * one, and a sortable one — rather than three spellings of the same idea.
 *
 * Patterns, not `FormatStyle`: a localised style is the locale's choice and this
 * setting exists because it is the user's. Month and weekday *names* still come
 * from the locale, which is the part that was never Bodha's to decide.
 */
enum class DateFormat(internal val pattern: String) {
    WeekdayAndMonth("EEEE, d MMMM"),
    Short("d MMM yyyy"),
    Numeric("yyyy-MM-dd"),
}

/**
 * The formatter is built per call rather than held as a constant: a formatter
 * captures the locale it was made with, and a launcher process outlives a
 * language change. At minute cadence the cost is nothing.
 */
fun formatClock(time: LocalTime, format: ClockFormat): String =
    time.format(DateTimeFormatter.ofPattern(format.pattern, Locale.getDefault()))

fun formatDate(date: LocalDate, format: DateFormat): String =
    date.format(DateTimeFormatter.ofPattern(format.pattern, Locale.getDefault()))
