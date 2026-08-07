package com.bodhalauncher.engine

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FocusTest {

    private val start: Instant = Instant.parse("2026-08-07T10:00:00Z")

    private fun session(minutes: Long = 30, allowed: Set<String> = emptySet()) =
        startFocusSession("Deep work", minutes, allowed, start)

    @Test
    fun `the sheet offers exactly fifteen, thirty and sixty minutes`() {
        assertEquals(listOf(15L, 30L, 60L), FOCUS_DURATION_MINUTES)
    }

    @Test
    fun `start is unavailable with a blank label and available with an empty allowed list`() {
        assertFalse(focusStartEnabled(""))
        assertFalse(focusStartEnabled("   "))
        assertTrue(focusStartEnabled("Deep work"))
    }

    @Test
    fun `a started session carries label, end instant and allowed apps, and nothing runs a counter`() {
        val s = startFocusSession("  Deep work ", 30, setOf("a"), start)
        assertEquals("Deep work", s.label)
        assertEquals(start, s.startedAt)
        assertEquals(start.plusSeconds(30 * 60), s.endsAt)
        assertEquals(setOf("a"), s.allowedAppIds)
    }

    @Test
    fun `end resolution is pure - not due, due at the boundary, late across process death`() {
        val s = session(minutes = 30)
        assertNull(focusLateBy(s.endsAt, start.plusSeconds(29 * 60)))
        assertEquals(Duration.ZERO, focusLateBy(s.endsAt, s.endsAt))
        // The process was dead for three hours past the end: the lateness is the gap.
        assertEquals(Duration.ofHours(3), focusLateBy(s.endsAt, s.endsAt.plus(Duration.ofHours(3))))
    }

    @Test
    fun `a check is due exactly when the app is off the list - empty list checks everything`() {
        assertFalse(focusCheckDue(null, "any"))
        val listed = session(allowed = setOf("com.a"))
        assertFalse(focusCheckDue(listed, "com.a"))
        assertTrue(focusCheckDue(listed, "com.b"))
        assertTrue(focusCheckDue(session(allowed = emptySet()), "com.a"))
    }

    @Test
    fun `a full-duration end keeps the boundary instant, however late it is detected`() {
        val s = session(minutes = 30).copy(reaches = 3, proceeds = 1)
        val record = endFocusSession(s, s.endsAt.plus(Duration.ofHours(2)))
        assertEquals(s.endsAt, record.endedAt)
        assertFalse(record.endedEarly)
        assertEquals(3, record.reaches)
        assertEquals(1, record.proceeds)
    }

    @Test
    fun `an early end takes the moment of the choice`() {
        val s = session(minutes = 30)
        val at = start.plusSeconds(10 * 60)
        val record = endFocusSession(s, at)
        assertEquals(at, record.endedAt)
        assertTrue(record.endedEarly)
    }

    @Test
    fun `extend is the same session, ten more minutes from the choice`() {
        val s = session(minutes = 30).copy(reaches = 2, proceeds = 2)
        val late = s.endsAt.plus(Duration.ofHours(1))
        val extended = extendFocusSession(endFocusSession(s, late), setOf("com.a"), late)
        assertEquals(s.label, extended.label)
        assertEquals(s.startedAt, extended.startedAt)
        assertEquals(late.plus(Duration.ofMinutes(10)), extended.endsAt)
        assertEquals(2, extended.reaches)
        assertEquals(2, extended.proceeds)
    }

    @Test
    fun `the remaining phrase derives from the end instant`() {
        val s = session(minutes = 30)
        assertEquals("30 minutes left", focusRemainingPhrase(s.endsAt, start))
        assertEquals("1 minute left", focusRemainingPhrase(s.endsAt, start.plusSeconds(29 * 60)))
        assertEquals("Under a minute left", focusRemainingPhrase(s.endsAt, start.plusSeconds(29 * 60 + 30)))
    }

    @Test
    fun `the duration line owns the elapsed truth when shown late`() {
        val record = endFocusSession(session(minutes = 30), start.plusSeconds(30 * 60))
        assertEquals("You focused for 30 minutes.", focusDurationLine(record, overByMillis = 0))
        assertEquals(
            "You focused for 30 minutes — it ended 2 hours ago.",
            focusDurationLine(record, overByMillis = 2 * 60 * 60 * 1000L),
        )
    }

    @Test
    fun `the reach line is one neutral fact - no ratio, no praise`() {
        assertEquals("You didn't reach for anything else.", focusReachLine(0))
        assertEquals("You reached for something else once.", focusReachLine(1))
        assertEquals("You reached for something else 4 times.", focusReachLine(4))
    }
}
