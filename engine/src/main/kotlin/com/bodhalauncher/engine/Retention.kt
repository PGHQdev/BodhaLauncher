package com.bodhalauncher.engine

import java.time.LocalDateTime

/**
 * The retention-bearing data categories and their default windows (#19).
 * A null default means "until the user deletes it" — no window can apply.
 */
enum class RetentionCategory(val defaultDays: Int?) {
    RawUsageEvents(30),
    AggregatedUsage(365),
    NotificationContent(7),
    EventLog(365),
    Reflections(null),
}

/** User-adjusted windows, in days; categories not present keep their defaults. */
data class RetentionConfig(val overrides: Map<RetentionCategory, Int> = emptyMap()) {
    fun days(category: RetentionCategory): Int? =
        category.defaultDays?.let { overrides[category] ?: it }
}

/**
 * What to prune: everything strictly older than its category's cutoff goes.
 * Raw usage rolls up into aggregates before deletion; [rollupBoundary] marks
 * where that aggregation ends.
 */
data class PruningPlan(
    val cutoffs: Map<RetentionCategory, LocalDateTime>,
    val rollupBoundary: LocalDateTime,
)

/**
 * Resolves the pruning plan (#19). The raw-usage cutoff snaps back to the 4am
 * day boundary (ADR 0003) so a day is always aggregated whole or not at all;
 * until-deleted categories never receive a cutoff, even by override.
 */
fun resolveRetention(now: LocalDateTime, config: RetentionConfig): PruningPlan {
    val cutoffs = RetentionCategory.entries.mapNotNull { category ->
        config.days(category)?.let { days -> category to now.minusDays(days.toLong()) }
    }.toMap(mutableMapOf())

    val rawCutoff = cutoffs.getValue(RetentionCategory.RawUsageEvents)
    val aligned = dayKey(rawCutoff).atTime(4, 0)
    cutoffs[RetentionCategory.RawUsageEvents] = aligned

    return PruningPlan(cutoffs = cutoffs, rollupBoundary = aligned)
}
