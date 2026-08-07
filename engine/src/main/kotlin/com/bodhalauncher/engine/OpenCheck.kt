package com.bodhalauncher.engine

import java.time.Duration
import java.time.Instant

/**
 * When a rule's check fires (#8). [RepeatedOpening] catches autopilot: the
 * first launches are frictionless, the third within a rolling window is
 * checked (#72). [DailyThreshold] frees the first visits of the day (#73);
 * [Schedule] confines checks to a daily window (#74).
 */
enum class OpenCheckMode { Always, RepeatedOpening, DailyThreshold, Schedule, Never }

/** One daily window in minutes of day, start inclusive, end exclusive; start after end crosses midnight (#74). */
data class ScheduleWindow(val startMinute: Int, val endMinute: Int) {
    fun contains(minuteOfDay: Int): Boolean =
        if (startMinute <= endMinute) minuteOfDay in startMinute until endMinute
        else minuteOfDay >= startMinute || minuteOfDay < endMinute
}

/**
 * A user's per-app Open Check rule; no rule at all means the app just opens.
 * A mode whose config is absent is inert — the app just opens.
 */
data class OpenCheckRule(
    val mode: OpenCheckMode,
    /** DailyThreshold only: today's allowance before checks begin (#73). */
    val dailyThreshold: Duration? = null,
    /** Schedule only: the daily window checks fire inside (#74). */
    val window: ScheduleWindow? = null,
)

/**
 * What the adapter observed at this launch (#8). Context it can't observe stays
 * at the default and the corresponding trigger is inert — the [SuppressionFlags]
 * convention.
 */
data class OpenCheckContext(
    /** The app's foreground time since the 4am boundary; null without usage access (#73). */
    val usedTodayMillis: Long? = null,
    /** Local minute of day, for schedule windows (#74). */
    val minuteOfDay: Int = 0,
    /** A Focus session is running (#9, ADR 0012). */
    val focusActive: Boolean = false,
    /**
     * The session's allowed-list verdict, fed from the [focusCheckDue] seam —
     * the adapter answers from the session alone, so no per-app rule is read to
     * make the allowed/not-allowed decision (#168).
     */
    val focusCheckDue: Boolean = false,
    /** Adapter-classified emergency/utility app — always proceeds (#77). */
    val bypass: Boolean = false,
)

/**
 * The check sheet's context lines (#8): information, not guilt. Absent inputs
 * mean absent lines — no placeholders, no zeros.
 */
data class OpenCheckLines(
    val lastOpened: String?,
    val usedToday: String?,
)

/**
 * Phrases the context for a check on one app. [usedTodayMillis] is the app's
 * foreground time since [dayStart] — the adapter reads it on demand and never
 * stores it (ADR 0009). Under a minute of use isn't worth a line.
 */
fun resolveOpenCheckLines(
    lastOpenedEpochMillis: Long?,
    usedTodayMillis: Long?,
    nowEpochMillis: Long,
): OpenCheckLines = OpenCheckLines(
    lastOpened = lastOpenedEpochMillis?.let { "Last opened ${agoPhrase(nowEpochMillis - it)}" },
    usedToday = usedTodayMillis?.takeIf { it >= 60_000 }?.let { "Used ${spanPhrase(it)} today" },
)

internal fun agoPhrase(elapsedMillis: Long): String {
    val minutes = elapsedMillis / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${plural(minutes, "minute")} ago"
        hours < 24 -> "${plural(hours, "hour")} ago"
        else -> "${plural(days, "day")} ago"
    }
}

/** Shared with Awareness's session rows (#172), which phrase the same kind of span. */
internal fun spanPhrase(millis: Long): String {
    val minutes = millis / 60_000
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours < 1 -> plural(minutes, "minute")
        rest == 0L -> plural(hours, "hour")
        else -> "${plural(hours, "hour")} ${plural(rest, "minute")}"
    }
}

internal fun plural(n: Long, unit: String): String = "$n $unit${if (n == 1L) "" else "s"}"

sealed interface OpenCheckDecision {
    data object Proceed : OpenCheckDecision

    /**
     * The check is due — the adapter presents the sheet. [repeatedOpen] marks a
     * repeated-opening detection (#72); the adapter logs the event, type only.
     */
    data class ShowCheck(
        val appId: String,
        val at: Instant,
        val repeatedOpen: Boolean = false,
        /** Raised by a Focus session's allowed list (#168) — the adapter counts it as a reach. */
        val raisedByFocus: Boolean = false,
    ) : OpenCheckDecision
}

/**
 * A granted stretch of time in one checked app (#75). Always "timed session"
 * in code and copy — never bare "session", which the glossary reserves for the
 * device session (ADR 0001).
 */
data class TimedSession(
    val appId: String,
    val startedAt: Instant,
    val endsAt: Instant,
    val plannedMinutes: Long,
)

/** The time completed — the adapter presents the session-end moment (#75). */
data class TimedSessionEnd(val timedSession: TimedSession, val overByMillis: Long)

/**
 * The session-end moment's voice (#75): calm on time, honest when the moment
 * could only be shown [overByMillis] after the boundary — the copy acknowledges
 * the time that actually elapsed, never pretends the boundary held.
 */
fun sessionEndPhrase(plannedMinutes: Long, overByMillis: Long): String =
    if (overByMillis < 60_000) "Your $plannedMinutes minutes are complete."
    else "Your $plannedMinutes minutes ended ${agoPhrase(overByMillis)}."

/**
 * The engine's complete state, exposed for the adapter to persist — a pending
 * timed session must survive Bodha being killed (#75).
 */
data class OpenCheckState(
    /** The app a passed check granted entry to, and when it runs out. */
    val grantedApp: String?,
    val grantedUntil: Instant?,
    /** Recent frictionless launches per app, for the repeated-opening window (#72). */
    val recentLaunches: Map<String, List<Instant>> = emptyMap(),
    /** Per-app cooldown after a repeated-opening check fired (#72). */
    val cooldownUntil: Map<String, Instant> = emptyMap(),
    /** The one pending timed session, if any (#75). */
    val timedSession: TimedSession? = null,
) {
    companion object {
        val Initial = OpenCheckState(grantedApp = null, grantedUntil = null)
    }
}

/**
 * Pure decision engine for Open Check (#8): a launch attempt either proceeds or
 * is due a check, per the app's rule. Proceeding past a check grants that one
 * opening — the next attempt for the same app within [GRANT_WINDOW] passes and
 * consumes the grant, so the granted launch can flow back through the same
 * interception point without re-firing. Turning back grants nothing. Time
 * enters only through the instants passed in; the engine never reads a clock.
 */
class OpenCheckEngine(initial: OpenCheckState = OpenCheckState.Initial) {

    private var grantedApp = initial.grantedApp
    private var grantedUntil = initial.grantedUntil
    private var recentLaunches = initial.recentLaunches
    private var cooldownUntil = initial.cooldownUntil
    private var timedSession = initial.timedSession

    fun snapshot(): OpenCheckState =
        OpenCheckState(grantedApp, grantedUntil, recentLaunches, cooldownUntil, timedSession)

    fun onLaunchAttempt(
        appId: String,
        rule: OpenCheckRule?,
        now: Instant,
        context: OpenCheckContext = OpenCheckContext(),
    ): OpenCheckDecision {
        // The one decision point, in order (#168): the classified bypass first —
        // an emergency app is an emergency app whether or not a session runs —
        // then the grant, so a proceeded check flows back through without
        // re-firing; then the session's allowed list; then the app's own rule.
        if (context.bypass) return OpenCheckDecision.Proceed
        if (appId == grantedApp && grantedUntil?.isBefore(now) == false) {
            clearGrant()
            return OpenCheckDecision.Proceed
        }
        if (context.focusCheckDue) return OpenCheckDecision.ShowCheck(appId, now, raisedByFocus = true)
        return when (rule?.mode) {
            null, OpenCheckMode.Never -> OpenCheckDecision.Proceed
            OpenCheckMode.Always -> OpenCheckDecision.ShowCheck(appId, now)
            OpenCheckMode.RepeatedOpening -> onRepeatedAttempt(appId, now)
            OpenCheckMode.DailyThreshold -> {
                val threshold = rule.dailyThreshold ?: return OpenCheckDecision.Proceed
                val used = context.usedTodayMillis ?: return OpenCheckDecision.Proceed
                if (used >= threshold.toMillis()) OpenCheckDecision.ShowCheck(appId, now)
                else OpenCheckDecision.Proceed
            }
            OpenCheckMode.Schedule -> {
                val window = rule.window ?: return OpenCheckDecision.Proceed
                if (window.contains(context.minuteOfDay)) OpenCheckDecision.ShowCheck(appId, now)
                else OpenCheckDecision.Proceed
            }
        }
    }

    /**
     * The rolling window always rolls — launches are recorded even while the
     * rule cools down, so autopilot that outlasts the cooldown is caught on
     * the first launch after it; the cooldown only suppresses firing.
     */
    private fun onRepeatedAttempt(appId: String, now: Instant): OpenCheckDecision {
        val window = recentLaunches[appId].orEmpty()
            .filter { it.isAfter(now.minus(REPEATED_OPEN_WINDOW)) }
        val coolingDown = cooldownUntil[appId]?.isAfter(now) == true
        if (!coolingDown && window.size >= REPEATED_OPEN_THRESHOLD - 1) {
            cooldownUntil = cooldownUntil + (appId to now.plus(REPEATED_OPEN_COOLDOWN))
            recentLaunches = recentLaunches - appId
            return OpenCheckDecision.ShowCheck(appId, now, repeatedOpen = true)
        }
        recentLaunches = recentLaunches + (appId to window + now)
        return OpenCheckDecision.Proceed
    }

    /** The user chose Open on the check sheet; an untimed opening supersedes a pending timed session for the app. */
    fun onProceeded(appId: String, now: Instant) {
        grant(appId, now)
        if (timedSession?.appId == appId) timedSession = null
    }

    /** The user chose Open for [minutes] — the boundary they set for themselves (#75). */
    fun onProceededFor(appId: String, now: Instant, minutes: Long) {
        onProceeded(appId, now)
        timedSession = TimedSession(
            appId = appId,
            startedAt = now,
            endsAt = now.plusSeconds(minutes * 60),
            plannedMinutes = minutes,
        )
    }

    /**
     * Reports the session-end moment once the pending timed session's time has
     * completed; due until a session-end choice settles it. Where the moment
     * can only be shown late, [TimedSessionEnd.overByMillis] carries the truth.
     */
    fun advanceTo(now: Instant): TimedSessionEnd? {
        val session = timedSession ?: return null
        if (session.endsAt.isAfter(now)) return null
        return TimedSessionEnd(session, overByMillis = now.toEpochMilli() - session.endsAt.toEpochMilli())
    }

    /** Session end: the user closed the app; the boundary held. */
    fun onSessionEndClose() {
        timedSession = null
    }

    /** Session end: five more minutes, from the moment of the choice. */
    fun onSessionEndAddFive(now: Instant) {
        val session = timedSession ?: return
        timedSession = session.copy(
            endsAt = now.plus(ADD_FIVE),
            plannedMinutes = session.plannedMinutes + ADD_FIVE.toMinutes(),
        )
        // The reopening flows back through the interception point on a grant.
        grant(session.appId, now)
    }

    /** Session end: keep going without a timer; the reopening is granted. */
    fun onSessionEndContinue(now: Instant) {
        val session = timedSession ?: return
        timedSession = null
        grant(session.appId, now)
    }

    /** The user went back (or dismissed the sheet). */
    fun onTurnedBack(appId: String) {
        if (appId == grantedApp) clearGrant()
    }

    private fun grant(appId: String, now: Instant) {
        grantedApp = appId
        grantedUntil = now.plus(GRANT_WINDOW)
    }

    private fun clearGrant() {
        grantedApp = null
        grantedUntil = null
    }

    companion object {
        /** Long enough for the granted launch to loop back, short enough to keep Always honest. */
        val GRANT_WINDOW: Duration = Duration.ofSeconds(5)

        // Repeated-opening constants ship fixed in v1 (#72), Settings defaults
        // later — same precedent as the Intent Prompt frequency (ADR 0004).
        /** The launch that fires the check: the third inside the window. */
        const val REPEATED_OPEN_THRESHOLD = 3
        val REPEATED_OPEN_WINDOW: Duration = Duration.ofMinutes(15)
        val REPEATED_OPEN_COOLDOWN: Duration = Duration.ofMinutes(30)

        // Timed-session durations ship fixed in v1 (#75), Settings later (ADR 0004 precedent).
        val TIMED_SESSION_MINUTES = listOf(5L, 10L, 20L)
        val ADD_FIVE: Duration = Duration.ofMinutes(5)
    }
}
