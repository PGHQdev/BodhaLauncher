package com.bodhalauncher.engine

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One phone session as Awareness reads it (#171, ADR 0028): the engine's
 * session (ADR 0001) made durable. A null end is a session still running —
 * rendered as running, never as a zero-length one.
 */
data class SessionRecord(
    val id: Long,
    val start: LocalDateTime,
    val end: LocalDateTime?,
) {
    /** The day a session belongs to is the day it started (ADR 0003). */
    val day: LocalDate get() = dayKey(start)
}

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

private fun sessionsCount(finished: Int): String =
    if (finished == 1) "1 session" else "$finished sessions"
