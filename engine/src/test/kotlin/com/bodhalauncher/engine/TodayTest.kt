package com.bodhalauncher.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Today's header and the intention slot both read [dayKey], so at every hour —
 * 4am boundary included — they name the same day (#158).
 */
class TodayTest {

    @Test
    fun `header and intention agree at every hour of the day`() {
        for (hour in 0..23) {
            val now = LocalDateTime.of(2026, 8, 5, hour, 30)
            val headerDay = dayKey(now)
            val intention = DailyIntention("write", dayKey = headerDay)
            assertEquals("write", intention.textOn(now), "at $hour:30")
            assertEquals(headerDay, intention.dayKey)
        }
    }

    @Test
    fun `header and intention agree across a DST transition`() {
        // Europe/London springs forward 29 Mar 2026, 01:00 → 02:00.
        val zone = ZoneId.of("Europe/London")
        var instant = ZonedDateTime.of(2026, 3, 28, 20, 0, 0, 0, zone).toInstant()
        val end = ZonedDateTime.of(2026, 3, 29, 12, 0, 0, 0, zone).toInstant()
        while (instant <= end) {
            val now = LocalDateTime.ofInstant(instant, zone)
            val intention = DailyIntention("rest", dayKey = dayKey(now))
            assertEquals("rest", intention.textOn(now), "at $now")
            instant = instant.plusSeconds(30 * 60)
        }
    }

    @Test
    fun `yesterday's intention is stale today but its text survives for the suggestion`() {
        val yesterday = DailyIntention("read", dayKey = LocalDate.of(2026, 8, 4))
        val now = LocalDateTime.of(2026, 8, 5, 9, 0)
        assertNull(yesterday.textOn(now))
        assertEquals("read", yesterday.text)
        assertEquals(dayKey(now).minusDays(1), yesterday.dayKey)
    }
}
