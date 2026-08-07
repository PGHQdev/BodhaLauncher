package com.bodhalauncher.app.awareness

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.bodhalauncher.engine.DataCategorySummary
import com.bodhalauncher.engine.LaunchRecord
import com.bodhalauncher.engine.RetentionCategory
import com.bodhalauncher.engine.RetentionConfig
import com.bodhalauncher.engine.SessionId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One launch Bodha mediated (#173, ADR 0013): the app it opened, when, and the
 * session it happened in. Nothing else — no title, no content, no counts — and
 * it needs no permission, which is what makes it the spine of Awareness's
 * per-app views where the system's few days of `UsageEvents` cannot be.
 *
 * Device-local only, never transmitted (ADR 0009).
 */
@Entity(tableName = "launch_record")
data class LaunchRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The catalog's app id — `pkg`, or `pkg:serial` for a work-profile copy. */
    val appId: String,
    val atMillis: Long,
    /** Null for a launch made with no session open. */
    val sessionId: Long?,
)

@Dao
interface LaunchRecordDao {

    @Insert
    suspend fun insert(record: LaunchRecordEntity)

    /** One session opened up (#173): what it launched, in the order it launched them. */
    @Query("SELECT * FROM launch_record WHERE sessionId = :sessionId ORDER BY atMillis")
    suspend fun forSession(sessionId: Long): List<LaunchRecordEntity>

    /**
     * One app opened up (#174): every launch of it Bodha still holds, newest
     * first — the list is unbounded in the past and the end worth reading is the
     * near one.
     *
     * **No time predicate, ever.** Retention governs what exists here (ADR 0028)
     * and entitlement governs what renders (ADR 0005), and the second of those
     * belongs in the render path where a Pro flip is a recomposition rather than
     * a re-read. A `fromMillis` parameter would have put the entitlement window
     * into SQL, where nothing tests it and every caller inherits it.
     *
     * No index on `appId`: an index is a schema change, and a schema change is a
     * version bump, a hand-written migration and a committed schema JSON —
     * against a table retention caps at 30 days, for a scan nobody has measured
     * a problem with.
     */
    @Query("SELECT * FROM launch_record WHERE appId = :appId ORDER BY atMillis DESC")
    suspend fun forApp(appId: String): List<LaunchRecordEntity>

    /**
     * The retention worker's cut, under raw usage (#19, ADR 0013). The same
     * window the session records take, because a view reading both against each
     * other would otherwise lie about one of them (ADR 0028).
     */
    @Query("DELETE FROM launch_record WHERE atMillis < :cutoffMillis")
    suspend fun deleteBefore(cutoffMillis: Long)

    /** The privacy dashboard's row: how many records Bodha holds (#24). */
    @Query("SELECT COUNT(*) FROM launch_record")
    suspend fun count(): Int
}

/**
 * Writes what the single opening path opened. Fire-and-forget like the event
 * log — a blocked launch is worse than a lost record — but on one lane, so
 * launches land in the order they happened.
 */
class LaunchLog(private val dao: LaunchRecordDao) {

    @Suppress("OPT_IN_USAGE")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun record(appId: String, at: Instant, session: SessionId?) {
        scope.launch { runCatching { write(appId, at, session) } }
    }

    internal suspend fun write(appId: String, at: Instant, session: SessionId?) = dao.insert(
        LaunchRecordEntity(
            appId = appId,
            atMillis = at.toEpochMilli(),
            sessionId = session?.value,
        )
    )
}

fun LaunchRecordEntity.toRecord(): LaunchRecord = LaunchRecord(
    appId = appId,
    at = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault()),
    session = sessionId,
)

/**
 * The store's row for the privacy dashboard's local-data section (#24, ADR 0013):
 * the launch log holds app identity, which the event log deliberately cannot, so
 * it is a row the dashboard's design predates.
 */
suspend fun LaunchRecordDao.dashboardSummary(
    config: RetentionConfig = RetentionConfig(),
): DataCategorySummary = DataCategorySummary(
    category = RetentionCategory.RawUsageEvents,
    count = count(),
    retentionDays = config.days(RetentionCategory.RawUsageEvents),
)
