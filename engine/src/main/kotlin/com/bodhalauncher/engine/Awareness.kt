package com.bodhalauncher.engine

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * One phone session as Awareness reads it (#171, ADR 0028): the engine's
 * session (ADR 0001) made durable. A null end is a session still running —
 * rendered as running, never as a zero-length one.
 */
data class SessionRecord(
    val id: Long,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    /**
     * The day a session belongs to is the day it started (ADR 0003). Defaulted
     * from the start, but a store that stamped the key at write passes its own —
     * one source of truth per record, never two readings of the boundary.
     */
    val day: LocalDate = dayKey(start),
)

/**
 * The three signals that make a session intentional (ADR 0013): the user
 * answered the Intent Prompt, wrote an Open Check intention (#76), or ran a
 * Focus session. Everything else is unclassified — never "unintentional",
 * because the phone does not know.
 *
 * Named once, as the events that state them. [computeMetrics] reads this set
 * directly and Awareness's view reads the same three sources as [IntentSignal]s,
 * so the definition is one thing rather than two that happen to agree.
 */
val INTENT_SIGNAL_EVENTS: Set<EventType> = setOf(
    EventType.IntentPromptAnswered,
    EventType.OpenCheckIntentionWritten,
    EventType.FocusStarted,
)

/**
 * One stated intent, at the moment it was stated (#172).
 *
 * [session] is the session the record named, and it decides on its own where one
 * is named — a prompt answer carries the id it was asked under. An Open Check
 * intention and a Focus record carry none, so the span they fell inside is what
 * attributes them, which is the whole of "attributed to the session they
 * happened in".
 */
data class IntentSignal(val at: LocalDateTime, val session: Long? = null)

/** A session with its classification — intent as an attribute, never a fifth view (ADR 0013). */
data class AwarenessSession(val record: SessionRecord, val intentional: Boolean)

private fun IntentSignal.belongsTo(record: SessionRecord): Boolean =
    if (session != null) session == record.id
    // An open record has no upper bound: the session still running is the one
    // anything stated after its start was stated in.
    else !at.isBefore(record.start) && (record.end == null || at.isBefore(record.end))

/**
 * The day's sessions in time order, each classified (#172). One rule, applied to
 * whatever signals the caller could read: a source it could not reach simply
 * contributes nothing, which classifies a session as unclassified rather than
 * as a falsehood.
 */
fun resolveAwarenessSessions(
    records: List<SessionRecord>,
    signals: List<IntentSignal>,
): List<AwarenessSession> = records
    .sortedBy { it.start }
    .map { record -> AwarenessSession(record, signals.any { it.belongsTo(record) }) }

/**
 * Awareness's Today view (#171): a count, or the absence said in words.
 * [None] exists because 0 must never stand in for an unknown — an empty read
 * and a failed one are the caller's to tell apart before resolving.
 */
sealed interface AwarenessToday {
    data object None : AwarenessToday
    data class Sessions(val finished: Int, val running: Boolean) : AwarenessToday
}

fun resolveAwarenessToday(records: List<SessionRecord>, now: LocalDateTime): AwarenessToday {
    val today = dayKey(now)
    val finished = records.count { it.end != null && it.day == today }
    val running = records.any { it.end == null }
    return if (finished == 0 && !running) AwarenessToday.None
    else AwarenessToday.Sessions(finished = finished, running = running)
}

/**
 * The one rendered line. Bare counts only — no delta, no direction word,
 * no second ink, no ranking (ADR 0013).
 */
fun awarenessTodayLine(view: AwarenessToday): String = when (view) {
    AwarenessToday.None -> "No sessions yet today"
    is AwarenessToday.Sessions -> when {
        view.finished == 0 -> "A session is running now"
        view.running -> "${sessionsCount(view.finished)} today · one running now"
        else -> "${sessionsCount(view.finished)} today"
    }
}

/**
 * A session row's line: when it started, and how long it ran (#172).
 *
 * Nothing is marked short. The spec names short sessions, but no threshold
 * exists anywhere and inventing one here would invent a judgement (ADR 0013).
 */
fun awarenessSessionLine(session: AwarenessSession): String {
    val started = session.record.start.format(SESSION_TIME)
    val end = session.record.end ?: return "$started · running now"
    val millis = Duration.between(session.record.start, end).toMillis()
    return "$started · ${if (millis < 60_000) "under a minute" else spanPhrase(millis)}"
}

/** The classification as a word, in one ink and with no valence colour (ADR 0013). */
fun awarenessIntentWord(intentional: Boolean): String =
    if (intentional) "Intentional" else "Unclassified"

private fun sessionsCount(finished: Int): String =
    if (finished == 1) "1 session" else "$finished sessions"

private val SESSION_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
