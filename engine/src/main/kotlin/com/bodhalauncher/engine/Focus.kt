package com.bodhalauncher.engine

import java.time.Duration
import java.time.Instant

/**
 * A Focus session (ADR 0012): an activity label, a duration held as its end
 * instant, and the allowed apps. Nothing else — no linked task, no end condition
 * other than the duration, and no pause. Remaining time is always derived from
 * [endsAt] against wall clock, never a counter, which is what survives process
 * death and reboot. [reaches] and [proceeds] are the checks the session fired
 * (#168) — the raw material its record carries (#169).
 */
data class FocusSession(
    val label: String,
    val startedAt: Instant,
    val endsAt: Instant,
    /** Empty is meaningful: everything is checked (#166). */
    val allowedAppIds: Set<String>,
    val reaches: Int = 0,
    val proceeds: Int = 0,
)

/** The setup sheet's fixed durations (#166) — a fixed set, per ADR 0004's precedent. */
val FOCUS_DURATION_MINUTES = listOf(15L, 30L, 60L)

/** Extend from the end moment: a fixed ten minutes, no reopened setup (#170). */
val FOCUS_EXTEND: Duration = Duration.ofMinutes(10)

/**
 * Start is unavailable only while the label is blank — the end moment and the
 * record have nothing to name without it. An empty allowed list is a valid
 * choice, not a missing one (#166).
 */
fun focusStartEnabled(label: String): Boolean = label.isNotBlank()

/**
 * Starting an ad-hoc session is free by construction (ADR 0005): this seam takes
 * no entitlement input, so no caller can consult the gate on the way through.
 */
fun startFocusSession(label: String, minutes: Long, allowedAppIds: Set<String>, now: Instant): FocusSession =
    FocusSession(
        label = label.trim(),
        startedAt = now,
        endsAt = now.plusSeconds(minutes * 60),
        allowedAppIds = allowedAppIds,
    )

/**
 * End resolution as a pure seam (#169): null while the session still runs; once
 * the duration has elapsed, how late the detection is — zero when caught at the
 * boundary, the dead-process gap when caught on the next start.
 */
fun focusLateBy(endsAt: Instant, now: Instant): Duration? =
    if (now.isBefore(endsAt)) null else Duration.between(endsAt, now)

/**
 * The allowed-list gate as a pure seam (#168): a check is due exactly when a
 * session runs and the app is off its list. No rule is read here, and none may
 * be — the list is the whole decision.
 */
fun focusCheckDue(session: FocusSession?, appId: String): Boolean =
    session != null && appId !in session.allowedAppIds

/**
 * What a Focus session leaves behind (#169, ADR 0029): the label, its span, and
 * its checks. No app identity — the record carries counts, never names. A full
 * duration ends at the boundary the user set; only an early end takes the
 * moment of the choice.
 */
data class FocusRecord(
    val label: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val reaches: Int,
    val proceeds: Int,
    val endedEarly: Boolean,
)

/** Every ending path lands here: early takes [now], full duration keeps the boundary. */
fun endFocusSession(session: FocusSession, now: Instant): FocusRecord {
    // The duration has not elapsed, so this ending is the user's choice.
    val early = now.isBefore(session.endsAt)
    return FocusRecord(
        label = session.label,
        startedAt = session.startedAt,
        endedAt = if (early) now else session.endsAt,
        reaches = session.reaches,
        proceeds = session.proceeds,
        endedEarly = early,
    )
}

/**
 * Extend is the one-tap answer to "not done yet" (#170): the same session —
 * label, start and counts intact — running ten more minutes from the choice,
 * so a moment shown hours late still buys usable time. The start instant stays
 * the true one — #169's record carries instants, not a score — so a moment
 * extended long after its boundary folds the dormant gap into the span.
 * Accepted: the record answers "when", and the duration line's honesty about
 * lateness already covered the gap once, at the moment it was shown.
 */
fun extendFocusSession(record: FocusRecord, allowedAppIds: Set<String>, now: Instant): FocusSession =
    FocusSession(
        label = record.label,
        startedAt = record.startedAt,
        endsAt = now.plus(FOCUS_EXTEND),
        allowedAppIds = allowedAppIds,
        reaches = record.reaches,
        proceeds = record.proceeds,
    )

/** The running surface's time line, derived from the end instant at render (#166). */
fun focusRemainingPhrase(endsAt: Instant, now: Instant): String {
    val minutes = Duration.between(now, endsAt).toMinutes()
    return if (minutes < 1) "Under a minute remaining" else "${plural(minutes, "minute")} remaining"
}

/**
 * The end moment's duration line (#170): calm at the boundary, honest when the
 * moment could only be shown late — the same voice the timed-session end has.
 */
fun focusDurationLine(record: FocusRecord, overByMillis: Long): String {
    val ran = Duration.between(record.startedAt, record.endedAt).toMinutes().coerceAtLeast(1)
    val phrase = "You focused for ${plural(ran, "minute")}"
    return if (overByMillis < 60_000) "$phrase." else "$phrase — it ended ${agoPhrase(overByMillis)}."
}

/**
 * One neutral line for reaching elsewhere (#170): a fact, said once. No ratio,
 * no praise, no streak, no score (ADR 0012).
 */
fun focusReachLine(reaches: Int): String = when (reaches) {
    0 -> "You didn't reach for anything else."
    1 -> "You reached for something else once."
    else -> "You reached for something else $reaches times."
}
