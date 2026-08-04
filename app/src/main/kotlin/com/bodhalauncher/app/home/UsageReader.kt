package com.bodhalauncher.app.home

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
    fun lastUsed(): Map<String, Long>? {
        if (!capabilities.granted(Capability.UsageAccess)) return null
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        return manager
            .queryAndAggregateUsageStats(now - LOOKBACK_MILLIS, now)
            .mapValues { (_, stats) -> stats.lastTimeUsed }
            .filterValues { it > 0 }
    }

    private companion object {
        const val LOOKBACK_MILLIS = 30L * 24 * 3_600_000
    }
}
