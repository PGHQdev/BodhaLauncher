package com.bodhalauncher.app.session

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.bodhalauncher.engine.DataCategorySummary
import com.bodhalauncher.engine.RetentionCategory
import com.bodhalauncher.engine.RetentionConfig
import com.bodhalauncher.engine.SessionRecord
import com.bodhalauncher.engine.Transition
import com.bodhalauncher.engine.dayKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One durable phone session (#171, ADR 0028): the engine's timestamps and
 * nothing else — no app identity, no text, nothing that transmits (ADR 0009).
 * Device-local only.
 */
@Entity(tableName = "session_record")
data class SessionRecordEntity(
    /** The engine's SessionId — monotonic and persisted, so it survives restarts. */
    @PrimaryKey val sessionId: Long,
    val startMillis: Long,
    /** Null while the session runs; the final screen-off once the engine ends it. */
    val endMillis: Long?,
    /** dayKey(start).toEpochDay(), stamped at write so reads share the 4am rule (ADR 0003). */
    val dayEpochDay: Long,
)

@Dao
interface SessionRecordDao {

    /**
     * A start the row already exists for keeps the first row — restart
     * reconciliation and backfill replay the stream, they never fork it (#171).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: SessionRecordEntity)

    @Query("UPDATE session_record SET endMillis = :endMillis WHERE sessionId = :sessionId")
    suspend fun close(sessionId: Long, endMillis: Long)

    /** The day's records, plus any still-open one — running is shown, whatever day it started. */
    @Query("SELECT * FROM session_record WHERE dayEpochDay = :epochDay OR endMillis IS NULL ORDER BY startMillis")
    suspend fun forDay(epochDay: Long): List<SessionRecordEntity>

    /**
     * The Week view's read (#176): every record stamped with a day in the range,
     * plus any session still open.
     *
     * The open-record arm is here for the same reason [forDay] has one — a
     * session that has not ended has no day to compare against a range — and it
     * decides nothing about where that record is drawn. **The resolvers place a
     * record on a day, and never this query's OR-arm**: the Week places by the
     * stamped day alone, so an old open session comes back here and lands on the
     * one row it belongs to.
     *
     * No index on `dayEpochDay`. An index costs a schema version, a hand-written
     * migration and a committed schema file, against a table retention holds to
     * 30 days and a query nothing has measured a problem with.
     */
    @Query(
        "SELECT * FROM session_record " +
            "WHERE (dayEpochDay BETWEEN :fromEpochDay AND :toEpochDay) OR endMillis IS NULL " +
            "ORDER BY startMillis"
    )
    suspend fun forDays(fromEpochDay: Long, toEpochDay: Long): List<SessionRecordEntity>

    /**
     * The records behind a set of ids (#178), for the two readers that hold ids
     * and need the sessions they name: the exclusions list, which draws a row per
     * excluded session, and the App view, which needs each excluded session's
     * span to place an unmediated open inside it.
     *
     * A query and nothing else. Which ids are asked for is the exclusion store's,
     * and what happens to what comes back is the engine's — an id the store holds
     * and this query cannot answer for is a record retention took, which the
     * caller prunes rather than this query papering over.
     *
     * One bound variable per id, against SQLite's default limit of 999. Hand-picked
     * exclusions do not reach it; no chunking is written, and if it is ever needed
     * it belongs at the call site that knows how the list got that long.
     */
    @Query("SELECT * FROM session_record WHERE sessionId IN (:ids) ORDER BY startMillis")
    suspend fun withIds(ids: List<Long>): List<SessionRecordEntity>

    /** The retention worker's cut, under raw usage (#19, ADR 0028). */
    @Query("DELETE FROM session_record WHERE startMillis < :cutoffMillis")
    suspend fun deleteBefore(cutoffMillis: Long)

    /** The privacy dashboard's row: how many records Bodha holds (#24). */
    @Query("SELECT COUNT(*) FROM session_record")
    suspend fun count(): Int
}

/**
 * Writes the engine's transitions into the durable record. Fire-and-forget like
 * the event log, but on one lane so a backfilled end can never land before its
 * start. A resume touches nothing — the row it belongs to is still open — and a
 * peek writes nothing, so a peek leaves no record.
 */
class SessionRecordLog(private val dao: SessionRecordDao) {

    @Suppress("OPT_IN_USAGE")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun record(transition: Transition) {
        scope.launch { runCatching { write(transition) } }
    }

    internal suspend fun write(transition: Transition) {
        when (transition) {
            is Transition.SessionStarted -> dao.insert(
                SessionRecordEntity(
                    sessionId = transition.session.value,
                    startMillis = transition.at.toEpochMilli(),
                    endMillis = null,
                    dayEpochDay = dayKey(transition.at.toLocal()).toEpochDay(),
                )
            )
            is Transition.SessionEnded ->
                dao.close(transition.session.value, transition.at.toEpochMilli())
            is Transition.SessionResumed, is Transition.PeekObserved -> Unit
        }
    }
}

fun SessionRecordEntity.toRecord(): SessionRecord = SessionRecord(
    id = sessionId,
    start = Instant.ofEpochMilli(startMillis).toLocal(),
    end = endMillis?.let { Instant.ofEpochMilli(it).toLocal() },
    // The key stamped at write decides — a zone change since must not re-file the day.
    day = LocalDate.ofEpochDay(dayEpochDay),
)

private fun Instant.toLocal(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())

/** The store's row for the privacy dashboard's local-data section (#24, ADR 0028). */
suspend fun SessionRecordDao.dashboardSummary(
    config: RetentionConfig = RetentionConfig(),
): DataCategorySummary = DataCategorySummary(
    category = RetentionCategory.RawUsageEvents,
    count = count(),
    retentionDays = config.days(RetentionCategory.RawUsageEvents),
)
