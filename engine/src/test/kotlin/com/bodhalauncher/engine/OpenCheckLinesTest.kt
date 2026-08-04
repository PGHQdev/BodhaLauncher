package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenCheckLinesTest {

    private val now = 1_000_000_000_000L
    private fun minutesAgo(m: Long): Long = now - m * 60_000
    private fun minutes(m: Long): Long = m * 60_000

    @Test
    fun `absent inputs mean absent lines - never placeholders`() {
        val lines = resolveOpenCheckLines(lastOpenedEpochMillis = null, usedTodayMillis = null, nowEpochMillis = now)
        assertNull(lines.lastOpened)
        assertNull(lines.usedToday)
    }

    @Test
    fun `last opened phrases minutes, hours and days`() {
        fun lastOpened(then: Long) =
            resolveOpenCheckLines(then, null, now).lastOpened

        assertEquals("Last opened just now", lastOpened(minutesAgo(0)))
        assertEquals("Last opened 1 minute ago", lastOpened(minutesAgo(1)))
        assertEquals("Last opened 8 minutes ago", lastOpened(minutesAgo(8)))
        assertEquals("Last opened 1 hour ago", lastOpened(minutesAgo(60)))
        assertEquals("Last opened 5 hours ago", lastOpened(minutesAgo(5 * 60)))
        assertEquals("Last opened 1 day ago", lastOpened(minutesAgo(24 * 60)))
        assertEquals("Last opened 3 days ago", lastOpened(minutesAgo(3 * 24 * 60)))
    }

    @Test
    fun `used today phrases minutes and hours, dropping zero minutes`() {
        fun usedToday(millis: Long) =
            resolveOpenCheckLines(null, millis, now).usedToday

        assertEquals("Used 1 minute today", usedToday(minutes(1)))
        assertEquals("Used 34 minutes today", usedToday(minutes(34)))
        assertEquals("Used 1 hour today", usedToday(minutes(60)))
        assertEquals("Used 1 hour 12 minutes today", usedToday(minutes(72)))
        assertEquals("Used 2 hours today", usedToday(minutes(120)))
    }

    @Test
    fun `under a minute of use is not worth a line`() {
        assertNull(resolveOpenCheckLines(null, 59_000L, now).usedToday)
        assertNull(resolveOpenCheckLines(null, 0L, now).usedToday)
    }

}
