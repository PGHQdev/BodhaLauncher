package com.bodhalauncher.app.data

import com.bodhalauncher.engine.EventType
import com.bodhalauncher.engine.LoggedEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fire-and-forget recording into the event log (#25): a lost event is
 * acceptable, a blocked UI thread is not.
 */
class EventLogger(private val dao: EventLogDao) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun log(type: EventType, valueMillis: Long? = null) {
        val at = System.currentTimeMillis()
        scope.launch {
            runCatching { dao.insert(EventLogEntity(type = type.name, atEpochMillis = at, valueMillis = valueMillis)) }
        }
    }

    /** The metric functions' input; unknown types (from a newer schema) are skipped. */
    suspend fun between(from: LocalDateTime, to: LocalDateTime): List<LoggedEvent> =
        dao.between(from.toEpochMillis(), to.toEpochMillis()).mapNotNull { row ->
            EventType.entries.find { it.name == row.type }?.let { type ->
                LoggedEvent(type, row.atEpochMillis.toLocalDateTime(), row.valueMillis)
            }
        }
}

private val zone: ZoneId get() = ZoneId.systemDefault()

fun LocalDateTime.toEpochMillis(): Long = atZone(zone).toInstant().toEpochMilli()

private fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
