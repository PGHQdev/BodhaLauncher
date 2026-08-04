package com.bodhalauncher.app.home

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.bodhalauncher.app.capability.CapabilityEdge
import com.bodhalauncher.engine.Capability

/**
 * Last-use timestamps from Android's usage stats, read on demand and never
 * stored (ADR 0009). Everything is absent until the user grants usage access
 * in system settings.
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
