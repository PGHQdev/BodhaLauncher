package com.bodhalauncher.engine

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AwarenessTest {

    private val now = LocalDateTime.of(2026, 8, 7, 14, 0)

    private fun record(id: Long, start: LocalDateTime, end: LocalDateTime?) =
        SessionRecord(id = id, start = start, end = end)

    @Test
    fun `no records resolves to the named absence, not zero`() {
        assertEquals(AwarenessToday.None, resolveAwarenessToday(emptyList(), now))
        assertEquals("No sessions yet today", awarenessTodayLine(AwarenessToday.None))
    }

    @Test
    fun `closed sessions today are counted`() {
        val records = (1L..6L).map {
            record(it, now.minusHours(it), now.minusHours(it).plusMinutes(10))
        }
        assertEquals(
            AwarenessToday.Sessions(finished = 6, running = false),
            resolveAwarenessToday(records, now),
        )
        assertEquals("6 sessions today", awarenessTodayLine(AwarenessToday.Sessions(6, running = false)))
        assertEquals("1 session today", awarenessTodayLine(AwarenessToday.Sessions(1, running = false)))
    }

    @Test
    fun `a running session shows as running, never as a zero-length one`() {
        val records = listOf(record(1, now.minusMinutes(5), end = null))
        assertEquals(
            AwarenessToday.Sessions(finished = 0, running = true),
            resolveAwarenessToday(records, now),
        )
        assertEquals("A session is running now", awarenessTodayLine(AwarenessToday.Sessions(0, running = true)))
        assertEquals(
            "2 sessions today · one running now",
            awarenessTodayLine(AwarenessToday.Sessions(2, running = true)),
        )
    }

    @Test
    fun `a session crossing 4am belongs to the day it started`() {
        // Started 3:30am on the 7th: under the 4am boundary that is still the 6th.
        val beforeBoundary = record(
            1,
            start = LocalDateTime.of(2026, 8, 7, 3, 30),
            end = LocalDateTime.of(2026, 8, 7, 4, 30),
        )
        assertEquals(AwarenessToday.None, resolveAwarenessToday(listOf(beforeBoundary), now))

        // Started 4:30am on the 7th: today's.
        val afterBoundary = record(
            2,
            start = LocalDateTime.of(2026, 8, 7, 4, 30),
            end = LocalDateTime.of(2026, 8, 7, 5, 0),
        )
        assertEquals(
            AwarenessToday.Sessions(finished = 1, running = false),
            resolveAwarenessToday(listOf(afterBoundary), now),
        )
    }

    @Test
    fun `a session still open from before the boundary still shows as running`() {
        val openSinceYesterday = record(1, start = LocalDateTime.of(2026, 8, 7, 3, 30), end = null)
        assertEquals(
            AwarenessToday.Sessions(finished = 0, running = true),
            resolveAwarenessToday(listOf(openSinceYesterday), now),
        )
    }

    @Test
    fun `no rendered line carries a delta, a direction word or a ranking`() {
        val lines = listOf(
            awarenessTodayLine(AwarenessToday.None),
            awarenessTodayLine(AwarenessToday.Sessions(1, running = false)),
            awarenessTodayLine(AwarenessToday.Sessions(6, running = true)),
        )
        val forbidden = listOf("+", "-", "more", "less", "up", "down", "better", "worse", "most", "least")
        for (line in lines) {
            for (word in forbidden) {
                assert(!line.lowercase().split(" ", "·").contains(word)) {
                    "\"$line\" carries \"$word\" (ADR 0013)"
                }
            }
        }
    }
}
