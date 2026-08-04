package com.bodhalauncher.app.home

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Last-use timestamps from Android's usage stats, read on demand and never
 * stored (ADR 0009). Everything is absent until the user grants usage access
 * in system settings.
 */
class UsageReader(private val context: Context) {

    /** Package name to last-use epoch millis over the past month; null without access. */
    fun lastUsed(): Map<String, Long>? {
        if (!hasAccess()) return null
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        return manager
            .queryAndAggregateUsageStats(now - LOOKBACK_MILLIS, now)
            .mapValues { (_, stats) -> stats.lastTimeUsed }
            .filterValues { it > 0 }
    }

    private fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private companion object {
        const val LOOKBACK_MILLIS = 30L * 24 * 3_600_000
    }
}
