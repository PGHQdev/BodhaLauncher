package com.bodhalauncher.engine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The entitlement window (#177, ADR 0005, ADR 0013): retention governs what
 * exists and this governs what renders, over the same list every view already
 * had in hand.
 */
class AwarenessWindowTest {

    private val now = LocalDateTime.of(2026, 8, 7, 14, 0)
    private val today: LocalDate = dayKey(now)

    private val free = EntitlementSnapshot(proActive = false, fetchedAt = Instant.parse("2026-08-07T09:00:00Z"))
    private val pro = EntitlementSnapshot(proActive = true, fetchedAt = Instant.parse("2026-08-07T09:00:00Z"))

    /** The gate's own sentence, so a copy edit there lands here with no second edit. */
    private val gateCopy = (resolveEntitlement(free, GatedRequest.AwarenessHistory) as GateDecision.Capped).boundary

    private fun session(id: Long, day: LocalDate, running: Boolean = false) = SessionRecord(
        id = id,
        start = day.atTime(9, 0),
        end = if (running) null else day.atTime(9, 20),
        day = day,
    )

    private fun open(day: LocalDate, hour: Int = 9) =
        LaunchRecord("atlas", day.atTime(hour, 0), session = null)

    @Test
    fun `a free window is the seven day keys ending today`() {
        val window = resolveAwarenessWindow(free, now)

        assertEquals(today.minusDays(6), window.from)
        assertEquals(FREE_AWARENESS_DAYS, window.cap)
        assertEquals(gateCopy, window.boundary)
        // Seven day keys, the oldest of them the window's floor.
        assertEquals(7, window.days((0L..6L).map { today.minusDays(it) }).records.size)
    }

    @Test
    fun `a free render drops a record older than the window and keeps the oldest day inside it`() {
        val window = resolveAwarenessWindow(free, now)
        val records = listOf(
            session(1, today.minusDays(7)),
            session(2, today.minusDays(6)),
            session(3, today),
        )

        val render = window.sessions(records)

        assertEquals(listOf(2L, 3L), render.records.map { it.id })
        assertEquals(gateCopy, render.boundary)
    }

    @Test
    fun `a free render drops a launch older than the window`() {
        val window = resolveAwarenessWindow(free, now)
        val launches = listOf(open(today.minusDays(9)), open(today.minusDays(6)), open(today))

        val render = window.launches(launches)

        assertEquals(2, render.records.size)
        assertEquals(gateCopy, render.boundary)
    }

    @Test
    fun `a withheld day is dropped from a week rather than rendered as empty`() {
        // A cap narrower than the Week's seven, which today's numbers never
        // produce — the day list is dropped whole rather than kept as a row
        // saying a day held nothing when it held records this tier cannot draw.
        val window = AwarenessWindow(from = today.minusDays(2), boundary = gateCopy, cap = 3)

        val render = window.days((0L..6L).map { today.minusDays(it) }.reversed())

        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), render.records)
        assertEquals(gateCopy, render.boundary)
    }

    /** The 4am boundary decides both, from the two places each day is known (ADR 0003). */
    @Test
    fun `a session is placed by the day it started and a launch by the day it happened`() {
        val window = resolveAwarenessWindow(free, now)
        // 3:30am on the oldest rendered day is the previous day's key, so both
        // of these fall outside a window their wall-clock date sits inside.
        val edge = today.minusDays(6)
        val spanning = SessionRecord(
            id = 1,
            start = edge.plusDays(1).atTime(3, 30),
            end = edge.plusDays(1).atTime(4, 30),
            day = dayKey(edge.plusDays(1).atTime(3, 30)),
        )
        val lateOpen = LaunchRecord("atlas", edge.atTime(3, 30), session = null)

        assertEquals(listOf(spanning), window.sessions(listOf(spanning)).records)
        assertEquals(emptyList<LaunchRecord>(), window.launches(listOf(lateOpen)).records)
    }

    @Test
    fun `a running session older than the window is withheld, and one inside it is kept`() {
        val window = resolveAwarenessWindow(free, now)
        val stale = session(1, today.minusDays(20), running = true)
        val live = session(2, today, running = true)

        assertEquals(listOf(2L), window.sessions(listOf(stale, live)).records.map { it.id })
    }

    @Test
    fun `a session outside the window is not in the list, so its id opens nothing`() {
        val window = resolveAwarenessWindow(free, now)
        val records = listOf(session(1, today.minusDays(30)), session(2, today))

        val rendered = resolveAwarenessSessions(window.sessions(records).records, emptyList())

        assertNull(rendered.firstOrNull { it.record.id == 1L })
    }

    @Test
    fun `a Pro window keeps every record and hands back the same list`() {
        val window = resolveAwarenessWindow(pro, now)
        val records = listOf(session(1, today.minusDays(400)), session(2, today))

        val render = window.sessions(records)

        assertNull(window.from)
        assertNull(window.cap)
        // The same list object, not a copy that happens to be equal: a Pro render
        // is this filter removed rather than a second path that agrees.
        assertSame(records, render.records)
        assertNull(render.boundary)
    }

    @Test
    fun `a Pro render states no boundary however old the records are`() {
        val window = resolveAwarenessWindow(pro, now)

        assertNull(window.launches(listOf(open(today.minusDays(900)))).boundary)
        assertNull(window.days(listOf(today.minusDays(900))).boundary)
    }

    /**
     * The window is a floor on age, so upgrading needs no backfill: the same
     * list, resolved against a wider window, is the whole of the change.
     */
    @Test
    fun `flipping the snapshot to Pro renders the older records from the same list`() {
        val records = listOf(session(1, today.minusDays(30)), session(2, today))

        val clamped = resolveAwarenessWindow(free, now).sessions(records)
        val opened = resolveAwarenessWindow(pro, now).sessions(records)

        assertEquals(listOf(2L), clamped.records.map { it.id })
        assertEquals(listOf(1L, 2L), opened.records.map { it.id })
    }

    @Test
    fun `the boundary is the gate's own copy, and only where a record was withheld`() {
        val window = resolveAwarenessWindow(free, now)

        assertEquals(gateCopy, window.sessions(listOf(session(1, today.minusDays(8)))).boundary)
        assertEquals(
            "The past seven days come with Bodha. Your full history is part of Pro.",
            window.boundary?.explanation,
        )
    }

    @Test
    fun `no boundary renders when the gate withheld nothing`() {
        val window = resolveAwarenessWindow(free, now)

        assertNull(window.sessions(listOf(session(1, today))).boundary)
        assertNull(window.sessions(emptyList()).boundary)
        assertNull(window.launches(listOf(open(today.minusDays(3)))).boundary)
    }

    /**
     * A billing outage must never narrow a window the reader is already looking
     * through — the cached snapshot's own contract (#22), not a rule this ticket
     * adds. A fetch that failed and a fetch that never happened resolve the same.
     */
    @Test
    fun `a stale or never-fetched snapshot resolves the same window as a fresh one`() {
        val neverFetched = resolveAwarenessWindow(EntitlementSnapshot(proActive = false), now)
        val stale = resolveAwarenessWindow(
            EntitlementSnapshot(proActive = false, fetchedAt = Instant.EPOCH),
            now,
        )

        assertEquals(resolveAwarenessWindow(free, now), neverFetched)
        assertEquals(resolveAwarenessWindow(free, now), stale)
        // And a Pro snapshot fetched at the dawn of time is still Pro.
        assertNull(
            resolveAwarenessWindow(
                EntitlementSnapshot(proActive = true, fetchedAt = Instant.EPOCH),
                now,
            ).from,
        )
    }

    /**
     * The window governs records Bodha kept. A reading taken from Android's
     * usage statistics is a measurement of the device, held for the length of one
     * composition (ADR 0009) — so both period rates render at every tier, and the
     * window has no method that takes one.
     */
    @Test
    fun `the window governs records and states nothing about a usage reading`() {
        val window = resolveAwarenessWindow(free, now)
        val week = resolveAwarenessWeek(
            records = window.sessions(listOf(session(1, today.minusDays(30)), session(2, today))).records,
            signals = emptyList(),
            foregroundMillis = 78_120_000,
            previousForegroundMillis = 85_680_000,
            usage = AwarenessUsage.Live,
            now = now,
        )

        assertEquals(AwarenessDuration.Span(78_120_000L / AWARENESS_WEEK_DAYS), week.rate)
        assertEquals(AwarenessDuration.Span(85_680_000L / AWARENESS_WEEK_DAYS), week.previousRate)
        assertEquals("This week 3.1h/day · last week 3.4h/day", awarenessWeekRateLine(week))
    }

    /** The Week's rolling seven sits inside the free cap, so the clamp is a no-op. */
    @Test
    fun `the free window is exactly the week the Week view draws`() {
        val window = resolveAwarenessWindow(free, now)
        val weekDays = (AWARENESS_WEEK_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }

        val render = window.days(weekDays)

        assertEquals(weekDays, render.records)
        assertNull(render.boundary)
    }

    @Test
    fun `the terminus says what renders rather than what is lost`() {
        assertEquals("7 days render free", awarenessWindowTerminusLine(FREE_AWARENESS_DAYS))
        assertEquals("1 day renders free", awarenessWindowTerminusLine(1))
        assertEquals("Today renders free", awarenessWindowTerminusLine(null))
        assertEquals("Today renders free", awarenessWindowTerminusLine(0))
        // The authored sentence is the dialog's; this one names the machinery.
        assertTrue(awarenessWindowTerminusLine(FREE_AWARENESS_DAYS) != gateCopy.explanation)
    }
}
