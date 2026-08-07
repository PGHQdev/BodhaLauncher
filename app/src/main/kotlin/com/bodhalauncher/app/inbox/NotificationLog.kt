package com.bodhalauncher.app.inbox

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.bodhalauncher.engine.DataCategorySummary
import com.bodhalauncher.engine.RetentionCategory
import com.bodhalauncher.engine.RetentionConfig

/**
 * One counted notification (#161, ADR 0015): app, section, category and
 * timestamps only. The type cannot represent a title, a body or a sender name
 * — there is no field that could carry one; the key is a hash, so even a tag
 * chosen by the posting app is never written down. Device-local only, never
 * transmitted (ADR 0009).
 */
@Entity(tableName = "notification_log")
data class NotificationRecordEntity(
    /** A hash of the system's notification key — identity for update-in-place, content-free. */
    @PrimaryKey val keyHash: String,
    val appPackage: String,
    val section: String,
    val category: String?,
    val postedAtMillis: Long,
    val updatedAtMillis: Long,
)

@Dao
interface NotificationLogDao {

    /** A notification updating in place replaces its row — it counts once (#161). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: NotificationRecordEntity)

    /**
     * One count per section over the day key's window, by post time — so the
     * shade re-read after a reboot does not stamp yesterday's leftovers into
     * today (#161). Dismissal elsewhere doesn't remove a row.
     */
    @Query(
        "SELECT section, COUNT(*) AS count FROM notification_log " +
            "WHERE postedAtMillis >= :fromMillis AND postedAtMillis < :toMillis GROUP BY section"
    )
    suspend fun countsBetween(fromMillis: Long, toMillis: Long): List<SectionCount>

    /** REASON_LOCKDOWN obliges deleting our copy; metadata-only storage makes that this row (ADR 0015). */
    @Query("DELETE FROM notification_log WHERE keyHash = :keyHash")
    suspend fun deleteByKey(keyHash: String)

    /** The retention worker's cut, under the notification-content category (#19). */
    @Query("DELETE FROM notification_log WHERE updatedAtMillis < :cutoffMillis")
    suspend fun deleteBefore(cutoffMillis: Long)

    /** The privacy dashboard's row: how many records Bodha holds (#24). */
    @Query("SELECT COUNT(*) FROM notification_log")
    suspend fun count(): Int
}

data class SectionCount(val section: String, val count: Int)

/** The store's row for the privacy dashboard's local-data section (#24, #161). */
suspend fun NotificationLogDao.dashboardSummary(
    config: RetentionConfig = RetentionConfig(),
): DataCategorySummary = DataCategorySummary(
    category = RetentionCategory.NotificationContent,
    count = count(),
    retentionDays = config.days(RetentionCategory.NotificationContent),
)
