package com.bodhalauncher.engine

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyIntentionTest {

    private fun at(text: String): LocalDateTime = LocalDateTime.parse(text)

    @Test
    fun `a day runs from 4am to 4am`() {
        assertEquals(LocalDate.parse("2026-08-04"), dayKey(at("2026-08-04T10:00:00")))
        assertEquals(LocalDate.parse("2026-08-04"), dayKey(at("2026-08-04T23:59:00")))
        // Late-night use before 4am belongs to the previous day.
        assertEquals(LocalDate.parse("2026-08-04"), dayKey(at("2026-08-05T01:00:00")))
        assertEquals(LocalDate.parse("2026-08-04"), dayKey(at("2026-08-05T03:59:59")))
        assertEquals(LocalDate.parse("2026-08-05"), dayKey(at("2026-08-05T04:00:00")))
    }

    @Test
    fun `an intention set at 1am is still valid until the next 4am`() {
        val intention = DailyIntention(text = "Finish Bodha prototype", dayKey = dayKey(at("2026-08-05T01:00:00")))

        assertEquals("Finish Bodha prototype", intention.textOn(at("2026-08-05T03:30:00")))
        assertNull(intention.textOn(at("2026-08-05T04:00:00")))
    }

    @Test
    fun `an intention set during the day expires at the next 4am`() {
        val intention = DailyIntention(text = "Call Mom", dayKey = dayKey(at("2026-08-04T10:00:00")))

        assertEquals("Call Mom", intention.textOn(at("2026-08-04T23:00:00")))
        assertEquals("Call Mom", intention.textOn(at("2026-08-05T03:59:00")))
        assertNull(intention.textOn(at("2026-08-05T09:00:00")))
    }
}
