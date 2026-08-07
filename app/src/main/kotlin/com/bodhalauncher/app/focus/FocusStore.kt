package com.bodhalauncher.app.focus

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.engine.DataCategorySummary
import com.bodhalauncher.engine.EventType
import com.bodhalauncher.engine.FocusRecord
import com.bodhalauncher.engine.FocusSession
import com.bodhalauncher.engine.FocusSetup
import com.bodhalauncher.engine.IntentSignal
import com.bodhalauncher.engine.RetentionCategory
import com.bodhalauncher.engine.RetentionConfig
import com.bodhalauncher.engine.endFocusSession
import com.bodhalauncher.engine.extendFocusSession
import com.bodhalauncher.engine.focusLateBy
import com.bodhalauncher.engine.startFocusSession
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * What an ended Focus session leaves behind (#169, ADR 0029): the label — the
 * user's own words — its span, and its check counts. No app identity: counts,
 * never names. Device-local only, never transmitted (ADR 0009).
 */
@Entity(tableName = "focus_record")
data class FocusRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startMillis: Long,
    val endMillis: Long,
    val reaches: Int,
    val proceeds: Int,
    val endedEarly: Boolean,
)

@Dao
interface FocusRecordDao {

    @Insert
    suspend fun insert(record: FocusRecordEntity): Long

    /** Extend resurrects the session (#170): its ending un-happens, so its record goes. */
    @Query("DELETE FROM focus_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * The sessions that started in a span — Awareness's third intent signal
     * (#172). A running session has no record yet, so it contributes when it
     * ends, which is what "Focus contributes the moment the record exists" means.
     */
    @Query("SELECT * FROM focus_record WHERE startMillis >= :fromMillis ORDER BY startMillis")
    suspend fun startedSince(fromMillis: Long): List<FocusRecordEntity>

    /** The retention worker's cut, under Reflections (#19, ADR 0029). */
    @Query("DELETE FROM focus_record WHERE endMillis < :cutoffMillis")
    suspend fun deleteBefore(cutoffMillis: Long)

    /** The privacy dashboard's row: how many records Bodha holds (#24). */
    @Query("SELECT COUNT(*) FROM focus_record")
    suspend fun count(): Int
}

/**
 * A recorded session as the third of ADR 0013's intent signals (#172): running a
 * Focus session is stating something, and the moment it was stated is the moment
 * it started. It carries no phone session — the span it fell inside attributes it.
 *
 * Beside the entity, as `SessionRecordEntity.toRecord` is: the mapping from a row
 * to what the engine reads belongs with the row.
 */
fun FocusRecordEntity.toIntentSignal(): IntentSignal = IntentSignal(
    at = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneId.systemDefault()),
)

/** The store's row for the privacy dashboard's local-data section (#24, ADR 0029). */
suspend fun FocusRecordDao.dashboardSummary(
    config: RetentionConfig = RetentionConfig(),
): DataCategorySummary = DataCategorySummary(
    category = RetentionCategory.Reflections,
    count = count(),
    retentionDays = config.days(RetentionCategory.Reflections),
)

/**
 * An ended session's moment, waiting for the next arrival at root (#170). The
 * durable record already stands; [allowedAppIds] rides here — not in the record —
 * so extend can resurrect the same session.
 */
data class PendingFocusEnd(
    val record: FocusRecord,
    val allowedAppIds: Set<String>,
)

/**
 * The Focus session's home (#166): one at a time, persisted as its end instant
 * so remaining time is derived against wall clock across process death and
 * reboot — never a counter. All lifecycle rules come from the engine's pure
 * seams; this store only persists and logs. Starting never touches the
 * entitlement store — nothing here holds a reference to reach it with.
 */
class FocusStore(
    context: Context,
    private val dao: FocusRecordDao,
    private val events: EventLogger,
    /**
     * The single FIFO lane the record writes ride. Injectable so a test can
     * join it instead of sleeping and hoping — the CI-speed flake that ruled
     * out a fixed wait.
     */
    @Suppress("OPT_IN_USAGE")
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
) {

    private val prefs = context.getSharedPreferences("focus_session", Context.MODE_PRIVATE)

    /** The one running session; null reverts root to Home (#167). */
    val active = mutableStateOf(loadActive())

    /** The end moment owed, if any — read once by the next arrival at root (#170). */
    val pending = mutableStateOf(loadPending())

    /**
     * Previous setups, most recent first — what Search offers to start again
     * (#190). One entry per label: restarting a label moves it up carrying its
     * latest duration and allowed apps. Capped at [MAX_SETUPS], a default
     * chosen here and cheap to change; unlike the records these are a
     * convenience list, so no retention category applies (ADR 0029).
     */
    val setups = mutableStateOf(loadSetups())

    /**
     * The last ended session's row, persisted so extend can remove it even
     * across a restart. Written on the single lane after the insert, so a
     * following extend's delete always finds it (FIFO).
     */
    private var lastEndedRowId: Long
        get() = prefs.getLong(KEY_LAST_ROW, NONE)
        set(value) = prefs.edit { putLong(KEY_LAST_ROW, value) }

    /** One session at a time (#166): a start while one runs is a no-op, not a replacement. */
    fun start(label: String, minutes: Long, allowedAppIds: Set<String>, now: Instant) {
        if (active.value != null) return
        setActive(startFocusSession(label, minutes, allowedAppIds, now))
        recordSetup(FocusSetup(label.trim(), minutes, allowedAppIds))
        // Type and timestamp only — no label, no app names (ADR 0009).
        events.log(EventType.FocusStarted)
    }

    /** A remembered setup re-enacted whole — Search's start path (#190). */
    fun start(setup: FocusSetup, now: Instant) =
        start(setup.label, setup.minutes, setup.allowedAppIds, now)

    // JSON rather than joined strings: the label is arbitrary user text, and
    // SharedPreferences is XML-backed — a control-character separator is not a
    // valid XML 1.0 character and could corrupt the whole file on disk.
    private fun recordSetup(setup: FocusSetup) {
        val kept = listOf(setup) + setups.value.filter { it.label != setup.label }
        setups.value = kept.take(MAX_SETUPS)
        val json = JSONArray()
        setups.value.forEach { entry ->
            json.put(
                JSONObject()
                    .put(SETUP_LABEL, entry.label)
                    .put(SETUP_MINUTES, entry.minutes)
                    .put(SETUP_ALLOWED, JSONArray(entry.allowedAppIds.toList()))
            )
        }
        prefs.edit { putString(KEY_SETUPS, json.toString()) }
    }

    /** A malformed store loads as empty rather than crashing the launcher at bind. */
    private fun loadSetups(): List<FocusSetup> = runCatching {
        val json = JSONArray(prefs.getString(KEY_SETUPS, null) ?: return emptyList())
        (0 until json.length()).map { i ->
            val entry = json.getJSONObject(i)
            val allowed = entry.getJSONArray(SETUP_ALLOWED)
            FocusSetup(
                label = entry.getString(SETUP_LABEL),
                minutes = entry.getLong(SETUP_MINUTES),
                allowedAppIds = (0 until allowed.length()).mapTo(mutableSetOf(), allowed::getString),
            )
        }
    }.getOrDefault(emptyList())

    /** End from the running surface: the moment shows immediately, since the user is on root. */
    fun endEarly(now: Instant) = end(now)

    /**
     * The end seam's caller (#169): detects a duration that has elapsed —
     * including while the process was dead — at bind and while visible. The
     * lateness is not stored; the moment re-derives it at render, which is what
     * keeps the copy honest however late it shows.
     */
    fun resolveEnd(now: Instant) {
        val session = active.value ?: return
        if (focusLateBy(session.endsAt, now) != null) end(now)
    }

    /** Every ending path (#169): the record is written, the event logged, the moment owed. */
    private fun end(now: Instant) {
        val session = active.value ?: return
        val record = endFocusSession(session, now)
        setActive(null)
        setPending(PendingFocusEnd(record, session.allowedAppIds))
        // FocusPaused exists in the enum and is never logged, by design (#169).
        events.log(if (record.endedEarly) EventType.FocusAbandoned else EventType.FocusCompleted)
        scope.launch {
            // Cleared first, on the lane: a failed insert must leave nothing —
            // a stale id here is a *previous* session's row, and a later extend
            // would delete the wrong record.
            lastEndedRowId = NONE
            runCatching { lastEndedRowId = dao.insert(record.toEntity()) }
        }
    }

    /** The moment is shown once and never again (#170): taking it clears it. */
    fun consumePending(): PendingFocusEnd? =
        pending.value?.also { setPending(null) }

    /** Extend (#170): the same session, ten more minutes; its record un-happens. */
    fun extend(moment: PendingFocusEnd, now: Instant) {
        setActive(extendFocusSession(moment.record, moment.allowedAppIds, now))
        scope.launch {
            runCatching {
                val row = lastEndedRowId
                if (row != NONE) {
                    dao.deleteById(row)
                    lastEndedRowId = NONE
                }
            }
        }
    }

    /**
     * An app uninstalled mid-session is treated as not allowed from then on
     * (#168): membership is dropped when the catalog stops answering for it,
     * and persisted, so a reinstall does not resurrect the allowance.
     */
    fun retainAllowed(installedIds: Set<String>) {
        val session = active.value ?: return
        if (session.allowedAppIds.any { it !in installedIds }) {
            setActive(session.copy(allowedAppIds = session.allowedAppIds intersect installedIds))
        }
    }

    /** A focus-raised check was shown (#168); persisted at once, so counts survive process death. */
    fun countReach() = updateActive { it.copy(reaches = it.reaches + 1) }

    /** The user proceeded on a focus-raised check (#168). */
    fun countProceed() = updateActive { it.copy(proceeds = it.proceeds + 1) }

    private fun updateActive(change: (FocusSession) -> FocusSession) {
        active.value?.let { setActive(change(it)) }
    }

    private fun setActive(session: FocusSession?) {
        active.value = session
        prefs.edit {
            if (session == null) {
                ACTIVE_KEYS.forEach(::remove)
            } else {
                putString(KEY_LABEL, session.label)
                putLong(KEY_STARTED, session.startedAt.toEpochMilli())
                putLong(KEY_ENDS, session.endsAt.toEpochMilli())
                putString(KEY_ALLOWED, session.allowedAppIds.joinToString("\n"))
                putInt(KEY_REACHES, session.reaches)
                putInt(KEY_PROCEEDS, session.proceeds)
            }
        }
    }

    private fun setPending(moment: PendingFocusEnd?) {
        pending.value = moment
        prefs.edit {
            if (moment == null) {
                PENDING_KEYS.forEach(::remove)
            } else {
                putString(KEY_P_LABEL, moment.record.label)
                putLong(KEY_P_STARTED, moment.record.startedAt.toEpochMilli())
                putLong(KEY_P_ENDED, moment.record.endedAt.toEpochMilli())
                putInt(KEY_P_REACHES, moment.record.reaches)
                putInt(KEY_P_PROCEEDS, moment.record.proceeds)
                putBoolean(KEY_P_EARLY, moment.record.endedEarly)
                putString(KEY_P_ALLOWED, moment.allowedAppIds.joinToString("\n"))
            }
        }
    }

    private fun loadActive(): FocusSession? {
        val label = prefs.getString(KEY_LABEL, null) ?: return null
        return FocusSession(
            label = label,
            startedAt = Instant.ofEpochMilli(prefs.getLong(KEY_STARTED, 0)),
            endsAt = Instant.ofEpochMilli(prefs.getLong(KEY_ENDS, 0)),
            allowedAppIds = idSet(prefs.getString(KEY_ALLOWED, "")),
            reaches = prefs.getInt(KEY_REACHES, 0),
            proceeds = prefs.getInt(KEY_PROCEEDS, 0),
        )
    }

    private fun loadPending(): PendingFocusEnd? {
        val label = prefs.getString(KEY_P_LABEL, null) ?: return null
        return PendingFocusEnd(
            record = FocusRecord(
                label = label,
                startedAt = Instant.ofEpochMilli(prefs.getLong(KEY_P_STARTED, 0)),
                endedAt = Instant.ofEpochMilli(prefs.getLong(KEY_P_ENDED, 0)),
                reaches = prefs.getInt(KEY_P_REACHES, 0),
                proceeds = prefs.getInt(KEY_P_PROCEEDS, 0),
                endedEarly = prefs.getBoolean(KEY_P_EARLY, false),
            ),
            allowedAppIds = idSet(prefs.getString(KEY_P_ALLOWED, "")),
        )
    }

    private fun idSet(joined: String?): Set<String> =
        joined.orEmpty().split("\n").filter { it.isNotEmpty() }.toSet()

    private fun FocusRecord.toEntity() = FocusRecordEntity(
        label = label,
        startMillis = startedAt.toEpochMilli(),
        endMillis = endedAt.toEpochMilli(),
        reaches = reaches,
        proceeds = proceeds,
        endedEarly = endedEarly,
    )

    private companion object {
        const val KEY_LABEL = "label"
        const val KEY_STARTED = "startedAt"
        const val KEY_ENDS = "endsAt"
        const val KEY_ALLOWED = "allowedAppIds"
        const val KEY_REACHES = "reaches"
        const val KEY_PROCEEDS = "proceeds"
        val ACTIVE_KEYS = listOf(KEY_LABEL, KEY_STARTED, KEY_ENDS, KEY_ALLOWED, KEY_REACHES, KEY_PROCEEDS)

        const val KEY_P_LABEL = "pendingLabel"
        const val KEY_P_STARTED = "pendingStartedAt"
        const val KEY_P_ENDED = "pendingEndedAt"
        const val KEY_P_REACHES = "pendingReaches"
        const val KEY_P_PROCEEDS = "pendingProceeds"
        const val KEY_P_EARLY = "pendingEndedEarly"
        const val KEY_P_ALLOWED = "pendingAllowedAppIds"
        val PENDING_KEYS = listOf(
            KEY_P_LABEL, KEY_P_STARTED, KEY_P_ENDED, KEY_P_REACHES, KEY_P_PROCEEDS, KEY_P_EARLY, KEY_P_ALLOWED,
        )

        const val KEY_LAST_ROW = "lastEndedRowId"
        const val NONE = -1L

        const val KEY_SETUPS = "setups"
        const val MAX_SETUPS = 12
        const val SETUP_LABEL = "label"
        const val SETUP_MINUTES = "minutes"
        const val SETUP_ALLOWED = "allowedAppIds"
    }
}
