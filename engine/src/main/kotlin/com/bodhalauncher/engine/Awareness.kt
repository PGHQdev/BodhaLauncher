package com.bodhalauncher.engine

import java.time.Duration
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
 *
 * [text] is the user's own words where the signal carried any — a typed Open
 * Check intention, a Focus session's label, a prompt answer someone wrote into.
 * Null is a signal stated without words, which is a real answer and not a
 * missing one: a category picked from the prompt states an intent and spells
 * out nothing.
 */
data class IntentSignal(
    val at: LocalDateTime,
    val session: Long? = null,
    val text: String? = null,
)

/** A session with its classification — intent as an attribute, never a fifth view (ADR 0013). */
data class AwarenessSession(val record: SessionRecord, val intentional: Boolean)

/**
 * Whether a timestamp fell inside this session. An open record has no upper
 * bound: the session still running is the one anything after its start happened
 * in.
 */
private fun SessionRecord.holds(at: LocalDateTime): Boolean =
    !at.isBefore(start) && (end == null || at.isBefore(end))

private fun IntentSignal.belongsTo(record: SessionRecord): Boolean =
    if (session != null) session == record.id else record.holds(at)

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
fun awarenessSessionLine(
    session: AwarenessSession,
    /**
     * The chosen clock (#141): a session's start is a wall-clock time Bodha
     * writes, so it is spelled the way Home's clock spells it. The default is
     * what this line already read, so callers that have no preference to hand
     * are unchanged.
     */
    clock: ClockFormat = ClockFormat.TwentyFourHour,
): String {
    val started = formatClock(session.record.start.toLocalTime(), clock)
    val end = session.record.end ?: return "$started · running now"
    val millis = Duration.between(session.record.start, end).toMillis()
    return "$started · ${if (millis < 60_000) "under a minute" else spanPhrase(millis)}"
}

/** The classification as a word, in one ink and with no valence colour (ADR 0013). */
fun awarenessIntentWord(intentional: Boolean): String =
    if (intentional) "Intentional" else "Unclassified"

private fun sessionsCount(finished: Int): String =
    if (finished == 1) "1 session" else "$finished sessions"

/**
 * One launch Bodha mediated (#173, ADR 0013): what was opened, when, and the
 * session it happened in. App identity and a timestamp, and nothing else — the
 * event log could carry neither, which is why this store exists at all.
 */
data class LaunchRecord(
    val appId: String,
    val at: LocalDateTime,
    /** Null for a launch with no session open: recorded, and attributed to none. */
    val session: Long? = null,
)

/**
 * Awareness's Session view (#173): one session opened up — what was launched in
 * it and in what order, the Open Checks it raised, whether a repeated open was
 * noticed, and the words the user stated if there were any.
 */
data class SessionDetail(
    val session: AwarenessSession,
    val launches: List<LaunchRecord>,
    val checks: Int,
    val repeatedOpen: Boolean,
    val statement: String?,
)

/**
 * Resolves one session's view from the three stores that know about it (#173).
 *
 * A launch belongs to the session **it named** and to no other: the id was read
 * at the moment of the launch, which is a stronger attribution than any span,
 * and it is what keeps a launch made outside a session out of every session.
 * Events carry no session — the event log is a type and a timestamp (ADR 0009) —
 * so the span they fell inside is what attributes them, the same rule [IntentSignal]
 * already takes when it names none.
 */
fun resolveSessionDetail(
    session: AwarenessSession,
    launches: List<LaunchRecord>,
    events: List<LoggedEvent>,
    signals: List<IntentSignal>,
): SessionDetail {
    val record = session.record
    val inSession = events.filter { record.holds(it.at) }
    return SessionDetail(
        session = session,
        launches = launches.filter { it.session == record.id }.sortedBy { it.at },
        checks = inSession.count { it.type == EventType.OpenCheckDisplayed },
        repeatedOpen = inSession.any { it.type == EventType.RepeatedOpenDetected },
        // The first signal that carried words: a session may hold several, and
        // the one that spoke is the one worth reading back.
        statement = signals.filter { it.belongsTo(record) }.firstNotNullOfOrNull { it.text },
    )
}

/**
 * A launch's own line: the time it happened, spelled the way the clock is (#141).
 * The app's name is the row's title and comes from the catalog, never from here —
 * the record holds an id, and an id is not a name.
 */
fun launchTimeLine(
    launch: LaunchRecord,
    clock: ClockFormat = ClockFormat.TwentyFourHour,
): String = formatClock(launch.at.toLocalTime(), clock)

/**
 * What the Session view says beneath its rows (#173): bare statements of what
 * happened, in the order they are worth reading — never a count of nothing, and
 * never a word carrying a verdict (ADR 0013).
 *
 * The absence of launches is a line here rather than an invented row, which is
 * what keeps "the view never invents a row" true for a session that raised a
 * check and opened nothing.
 */
fun sessionDetailNotes(detail: SessionDetail): List<String> = buildList {
    if (detail.launches.isEmpty()) add("Nothing was opened in this session")
    if (detail.checks > 0) add("${plural(detail.checks.toLong(), "Open Check")} fired")
    if (detail.repeatedOpen) add("A repeated open was noticed")
}

