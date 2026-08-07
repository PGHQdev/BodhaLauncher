package com.bodhalauncher.engine

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The formats a user may choose (#141, ADR 0010). Month and weekday names come
 * from the locale, so the suite pins one — otherwise these assertions would be
 * about the machine running them rather than about the formats.
 */
class AppearanceTest {

    private lateinit var original: Locale

    @BeforeTest
    fun pinLocale() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    private val morning = LocalTime.of(9, 41)
    private val evening = LocalTime.of(21, 5)
    private val day = LocalDate.of(2026, 8, 5)

    @Test
    fun `twelve-hour names the half of the day`() {
        assertEquals("9:41 am", formatClock(morning, ClockFormat.TwelveHour).lowercase())
        assertEquals("9:05 pm", formatClock(evening, ClockFormat.TwelveHour).lowercase())
    }

    @Test
    fun `twenty-four hour counts past noon and does not pad the hour`() {
        assertEquals("9:41", formatClock(morning, ClockFormat.TwentyFourHour))
        assertEquals("21:05", formatClock(evening, ClockFormat.TwentyFourHour))
    }

    @Test
    fun `NATO is four digits, so it never reads as twenty-four hour`() {
        assertEquals("0941", formatClock(morning, ClockFormat.Nato))
        assertEquals("2105", formatClock(evening, ClockFormat.Nato))
        // The acceptance criterion stated as an assertion: a chosen format has
        // to look chosen, at every hour and not only before ten.
        ClockFormat.entries.forEach { format ->
            if (format == ClockFormat.Nato) return@forEach
            (0..23).forEach { hour ->
                val at = LocalTime.of(hour, 41)
                assertNotEquals(formatClock(at, format), formatClock(at, ClockFormat.Nato))
            }
        }
    }

    @Test
    fun `the three date formats render as the ticket names them`() {
        assertEquals("Wednesday, 5 August", formatDate(day, DateFormat.WeekdayAndMonth))
        assertEquals("5 Aug 2026", formatDate(day, DateFormat.Short))
        assertEquals("2026-08-05", formatDate(day, DateFormat.Numeric))
    }

    @Test
    fun `only numeric is sortable, which is the reason it is offered`() {
        val days = listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 3))

        val rendered = days.map { formatDate(it, DateFormat.Numeric) }

        assertEquals(rendered, rendered.sorted())
    }

    @Test
    fun `every row of the Appearance section is in the catalogue`() {
        val appearance = SETTINGS_ROWS.filter { it.section == SettingsSection.Appearance }

        assertEquals(
            listOf(SettingsRowId.Theme, SettingsRowId.ClockFormat, SettingsRowId.DateFormat),
            appearance.map { it.id },
        )
    }
}
