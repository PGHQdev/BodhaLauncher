package com.bodhalauncher.engine

import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextModesTest {

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 8, 7, hour, minute)

    private fun window(fromHour: Int, toHour: Int) = ScheduleWindow(fromHour * 60, toHour * 60)

    private fun mode(name: String, fromHour: Int? = null, toHour: Int? = null) =
        ContextMode(name, if (fromHour != null && toHour != null) window(fromHour, toHour) else null)

    @Test
    fun `no switch and no window resolves to the default arrangement`() {
        assertNull(resolveArrangement(listOf(mode("Work"), mode("Rest")), null, at(14)))
    }

    @Test
    fun `a switch that exists resolves to itself`() {
        val switch = ManualSwitch("Work", at(13))
        assertEquals("Work", resolveArrangement(listOf(mode("Work"), mode("Rest")), switch, at(14)))
    }

    @Test
    fun `a deleted mode falls back to the default with no intermediate state`() {
        assertNull(resolveArrangement(listOf(mode("Rest")), ManualSwitch("Work", at(13)), at(14)))
        assertNull(resolveArrangement(emptyList(), ManualSwitch("Work", at(13)), at(14)))
    }

    @Test
    fun `a mode whose window is open becomes active with no user action`() {
        val modes = listOf(mode("Evening", 18, 22))

        assertNull(resolveArrangement(modes, null, at(17, 59)))
        assertEquals("Evening", resolveArrangement(modes, null, at(18)))
        assertEquals("Evening", resolveArrangement(modes, null, at(21, 59)))
        // The end is exclusive: the window has closed by its own end minute.
        assertNull(resolveArrangement(modes, null, at(22)))
    }

    @Test
    fun `the earlier mode wins an overlap, and the later never activates while it holds`() {
        val modes = listOf(mode("Work", 9, 18), mode("Errands", 12, 14))

        assertEquals("Work", resolveArrangement(modes, null, at(13)))
        // Reordered, the tie breaks the other way — the order is the whole rule.
        assertEquals("Errands", resolveArrangement(modes.reversed(), null, at(13)))
        // And outside the overlap the later one still gets its own hours.
        assertEquals("Errands", resolveArrangement(modes.reversed(), null, at(12, 30)))
    }

    @Test
    fun `a window crossing midnight is active on both sides of the crossing`() {
        val modes = listOf(ContextMode("Night", ScheduleWindow(22 * 60, 6 * 60)))

        assertEquals("Night", resolveArrangement(modes, null, at(23)))
        assertEquals("Night", resolveArrangement(modes, null, at(2)))
        assertNull(resolveArrangement(modes, null, at(12)))
    }

    @Test
    fun `a manual switch expires exactly at the next window boundary`() {
        val modes = listOf(mode("Evening", 18, 22))
        val switch = ManualSwitch("Evening", at(14))

        assertEquals(at(18), nextWindowBoundary(modes, at(14)))
        assertFalse(manualSwitchExpired(switch, modes, at(17, 59)))
        assertTrue(manualSwitchExpired(switch, modes, at(18)))
    }

    /** Not the 4am day boundary: one manual switch must not silently disable a day of schedules. */
    @Test
    fun `a switch made inside a window lapses at that window's own end`() {
        val modes = listOf(mode("Evening", 18, 22))
        // Switched to Default at 19:00, while Evening's window is open.
        val switch = ManualSwitch(null, at(19))

        assertNull(resolveArrangement(modes, switch, at(21, 59)))
        assertEquals(at(22), nextWindowBoundary(modes, at(19)))
        // Past the end nothing is scheduled either, so the answer is the same
        // value arrived at for a different reason.
        assertNull(resolveArrangement(modes, switch, at(22)))
    }

    @Test
    fun `after the boundary the schedule's own answer applies, with no user action`() {
        val modes = listOf(mode("Evening", 18, 22))
        val switch = ManualSwitch(null, at(14))

        assertNull(resolveArrangement(modes, switch, at(17, 59)))
        assertEquals("Evening", resolveArrangement(modes, switch, at(18)))
    }

    @Test
    fun `the next boundary can be tomorrow's first, when none is left today`() {
        val modes = listOf(mode("Morning", 6, 9))

        assertEquals(
            LocalDateTime.of(2026, 8, 8, 6, 0),
            nextWindowBoundary(modes, at(23, 30)),
        )
    }

    @Test
    fun `a switch with no boundary ahead of it holds until the user changes it`() {
        val modes = listOf(mode("Work"), mode("Rest"))
        val switch = ManualSwitch("Work", at(9))

        assertNull(nextWindowBoundary(modes, at(9)))
        assertFalse(manualSwitchExpired(switch, modes, at(23, 59)))
        assertEquals(
            "Work",
            resolveArrangement(modes, switch, LocalDateTime.of(2036, 1, 1, 0, 0)),
        )
    }

    /**
     * Nothing is cached against a clock: the resolver is a function of the local
     * time it is handed, so a zone or DST change simply resolves again.
     */
    @Test
    fun `a local time change re-evaluates rather than leaving an arrangement stuck`() {
        val modes = listOf(mode("Evening", 18, 22))

        // The same instant, read in two zones an hour apart, answers differently.
        val utc = LocalDateTime.ofInstant(Instant.parse("2026-08-07T17:30:00Z"), java.time.ZoneOffset.UTC)
        val plusOne = LocalDateTime.ofInstant(Instant.parse("2026-08-07T17:30:00Z"), java.time.ZoneOffset.ofHours(1))

        assertNull(resolveArrangement(modes, null, utc))
        assertEquals("Evening", resolveArrangement(modes, null, plusOne))
    }

    /**
     * A mode switches Home's pins and nothing else (ADR 0016): the resolver takes
     * no rule and returns only an arrangement name, so no Open Check decision can
     * be reached through it. Asserted rather than argued, on the one seam where
     * both could meet — the window type they share.
     */
    @Test
    fun `an open mode window changes no Open Check decision`() {
        val shared = window(18, 22)
        val modes = listOf(ContextMode("Evening", shared))
        val engine = OpenCheckEngine()
        val insideTheWindow = OpenCheckContext(minuteOfDay = 19 * 60)

        assertEquals("Evening", resolveArrangement(modes, null, at(19)))
        // No rule: the app opens, whatever any mode's window is doing.
        assertEquals(
            OpenCheckDecision.Proceed,
            engine.onLaunchAttempt("com.app", rule = null, now = Instant.now(), context = insideTheWindow),
        )
    }

    @Test
    fun `a blank name is refused`() {
        assertEquals(ModeNameError.Blank, validateModeName("   ", emptyList()))
        assertEquals(ModeNameError.Blank, validateModeName("", emptyList()))
    }

    @Test
    fun `a name longer than the cap is refused after trimming`() {
        assertEquals(ModeNameError.TooLong, validateModeName("a".repeat(25), emptyList()))
        assertNull(validateModeName("  " + "a".repeat(24) + "  ", emptyList()))
    }

    @Test
    fun `a case-insensitive duplicate is refused`() {
        assertEquals(ModeNameError.Duplicate, validateModeName("work", listOf("Work")))
        assertEquals(ModeNameError.Duplicate, validateModeName(" WORK ", listOf("Work")))
        assertNull(validateModeName("Rest", listOf("Work")))
    }
}
