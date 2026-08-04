package com.bodhalauncher.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bodhalauncher.engine.RetentionCategory
import com.bodhalauncher.engine.RetentionConfig
import com.bodhalauncher.engine.resolveRetention
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Executes the retention resolver's pruning plan (#19). The engine decides,
 * this worker only deletes. Categories whose tables don't exist yet (raw usage,
 * notifications, …) start pruning when their feature lands; retention windows
 * become user-configurable with the Settings surface (#15).
 */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val plan = resolveRetention(LocalDateTime.now(), RetentionConfig())
        plan.cutoffs[RetentionCategory.EventLog]?.let { cutoff ->
            BodhaDatabase.get(applicationContext).eventLog().deleteBefore(cutoff.toEpochMillis())
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "retention",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
