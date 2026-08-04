package com.bodhalauncher.engine

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetentionResolverTest {

    private val noon = LocalDateTime.parse("2026-08-04T12:00:00")

    @Test
    fun `defaults prune raw usage at thirty days and aggregates at twelve months`() {
        val plan = resolveRetention(noon, RetentionConfig())

        assertEquals(LocalDateTime.parse("2026-07-05T04:00:00"), plan.cutoffs.getValue(RetentionCategory.RawUsageEvents))
        assertEquals(noon.minusDays(365), plan.cutoffs.getValue(RetentionCategory.AggregatedUsage))
    }

    @Test
    fun `notification content defaults to seven days`() {
        val plan = resolveRetention(noon, RetentionConfig())

        assertEquals(noon.minusDays(7), plan.cutoffs.getValue(RetentionCategory.NotificationContent))
    }

    @Test
    fun `until-deleted categories have no cutoff`() {
        val plan = resolveRetention(noon, RetentionConfig())

        assertFalse(RetentionCategory.Reflections in plan.cutoffs)
    }

    @Test
    fun `a user-adjusted window replaces the default`() {
        val config = RetentionConfig(overrides = mapOf(RetentionCategory.NotificationContent to 2))

        val plan = resolveRetention(noon, config)

        assertEquals(noon.minusDays(2), plan.cutoffs.getValue(RetentionCategory.NotificationContent))
    }

    @Test
    fun `an override cannot give an until-deleted category a window`() {
        val config = RetentionConfig(overrides = mapOf(RetentionCategory.Reflections to 30))

        val plan = resolveRetention(noon, config)

        assertFalse(RetentionCategory.Reflections in plan.cutoffs)
    }

    @Test
    fun `raw usage cutoff aligns to the 4am day boundary so aggregation never splits a day`() {
        val plan = resolveRetention(noon, RetentionConfig())
        val cutoff = plan.cutoffs.getValue(RetentionCategory.RawUsageEvents)

        assertEquals(4, cutoff.hour)
        assertEquals(0, cutoff.minute)
        assertEquals(plan.rollupBoundary, cutoff)
    }

    @Test
    fun `resolving at 2am belongs to the previous day's boundary`() {
        val late = LocalDateTime.parse("2026-08-04T02:00:00")

        val plan = resolveRetention(late, RetentionConfig())

        // 30 days back lands on 2026-07-05 02:00, whose 4am-day started 2026-07-04T04:00.
        assertEquals(LocalDateTime.parse("2026-07-04T04:00:00"), plan.rollupBoundary)
    }

    @Test
    fun `every windowed category appears in the plan`() {
        val plan = resolveRetention(noon, RetentionConfig())

        for (category in RetentionCategory.entries.filter { it.defaultDays != null }) {
            assertTrue(category in plan.cutoffs, "missing: $category")
        }
    }
}
