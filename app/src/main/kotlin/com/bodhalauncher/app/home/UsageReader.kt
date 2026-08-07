package com.bodhalauncher.app.home

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.bodhalauncher.app.capability.CapabilityEdge
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.ForegroundEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What Android's usage statistics can tell Bodha — last use, foreground totals,
 * and the moments the front changed — read on demand and never stored
 * (ADR 0009). Everything is absent until the user grants usage access in system
 * settings, and every reading here answers null rather than empty when it is,
 * so no caller can mistake "nothing measured" for "nothing happened".
 */
class UsageReader(private val context: Context) {

    private val capabilities = CapabilityEdge(context)

    /** Package name to last-use epoch millis over the past month; null without access. */
    fun lastUsed(): Map<String, Long>? =
        aggregate(System.currentTimeMillis() - LOOKBACK_MILLIS) { it.lastTimeUsed }

    /**
     * Package name to foreground millis since [startMillis]; null without access.
     * Bucket-based, so the total near a boundary is best-effort — good enough
     * for a context line, never for enforcement.
     */
    fun usedSince(startMillis: Long): Map<String, Long>? =
        aggregate(startMillis) { it.totalTimeInForeground }

    /**
     * Every moment the front of the phone became some app since [startMillis],
     * in the order Android reports them; null without access (#175).
     *
     * **Unfiltered, Bodha's own resumes included.** What counts as an opening
     * rather than an app resuming its own next activity is decided by comparing
     * an entry with the one before it, and that comparison is only meaningful
     * over the interleaved stream — narrowing here would answer the question
     * before `resolveForegroundOpens` could ask it.
     *
     * It cannot go through [aggregate]: that is a `queryAndAggregateUsageStats`
     * funnel, which collapses per-event structure by construction and could
     * never yield a moment. `ACTIVITY_RESUMED` is API 29 and minSdk is 29
     * (ADR 0002), so no version guard is owed.
     *
     * Read on demand and never stored (ADR 0009). Android keeps only a few days
     * of these regardless of what [startMillis] asks for, which is exactly why
     * the launch log exists (ADR 0013).
     */
    fun foregroundEntries(startMillis: Long): List<ForegroundEntry>? {
        if (!capabilities.granted(Capability.UsageAccess)) return null
        val zone = ZoneId.systemDefault()
        val events = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents(startMillis, System.currentTimeMillis())
        return buildList {
            // One reused instance: getNextEvent fills it, as the platform intends.
            val event = UsageEvents.Event()
            while (events.getNextEvent(event)) {
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    add(
                        ForegroundEntry(
                            appId = event.packageName,
                            at = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(event.timeStamp), zone,
                            ),
                        )
                    )
                }
            }
        }
    }

    private fun aggregate(
        startMillis: Long,
        field: (UsageStats) -> Long,
    ): Map<String, Long>? {
        if (!capabilities.granted(Capability.UsageAccess)) return null
        return context.getSystemService(UsageStatsManager::class.java)
            .queryAndAggregateUsageStats(startMillis, System.currentTimeMillis())
            .mapValues { (_, stats) -> field(stats) }
            .filterValues { it > 0 }
    }

    private companion object {
        const val LOOKBACK_MILLIS = 30L * 24 * 3_600_000
    }
}
