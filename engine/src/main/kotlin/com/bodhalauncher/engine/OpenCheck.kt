package com.bodhalauncher.engine

import java.time.Duration
import java.time.Instant

/**
 * When a rule's check fires (#8). [RepeatedOpening] catches autopilot: the
 * first launches are frictionless, the third within a rolling window is
 * checked (#72). [DailyThreshold] frees the first visits of the day (#73);
 * [Schedule] confines checks to a daily window (#74); [DuringFocus] is wired
 * but inert until Focus (#9) lands (#77).
 */
enum class OpenCheckMode { Always, RepeatedOpening, DailyThreshold, Schedule, DuringFocus, Never }

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
    /** Focus active; false until Focus (#9) exists (#77). */
    val focusActive: Boolean = false,
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

private fun agoPhrase(elapsedMillis: Long): String {
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

private fun spanPhrase(millis: Long): String {
    val minutes = millis / 60_000
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours < 1 -> plural(minutes, "minute")
        rest == 0L -> plural(hours, "hour")
        else -> "${plural(hours, "hour")} ${plural(rest, "minute")}"
    }
}

private fun plural(n: Long, unit: String): String = "$n $unit${if (n == 1L) "" else "s"}"

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
    ) : OpenCheckDecision
}

/**
 * The engine's complete state. Nothing in the skeleton outlives a process, so
 * the adapter doesn't persist it yet; timed sessions (#75) will.
 */
data class OpenCheckState(
    /** The app a passed check granted entry to, and when it runs out. */
    val grantedApp: String?,
    val grantedUntil: Instant?,
    /** Recent frictionless launches per app, for the repeated-opening window (#72). */
    val recentLaunches: Map<String, List<Instant>> = emptyMap(),
    /** Per-app cooldown after a repeated-opening check fired (#72). */
    val cooldownUntil: Map<String, Instant> = emptyMap(),
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

    fun snapshot(): OpenCheckState = OpenCheckState(grantedApp, grantedUntil, recentLaunches, cooldownUntil)

    fun onLaunchAttempt(
        appId: String,
        rule: OpenCheckRule?,
        now: Instant,
        context: OpenCheckContext = OpenCheckContext(),
    ): OpenCheckDecision {
        // Friction never stands between the user and a call or alarm (#77) —
        // before even the grant, so nothing is consumed on the way through.
        if (context.bypass) return OpenCheckDecision.Proceed
        if (appId == grantedApp && grantedUntil?.isBefore(now) == false) {
            clearGrant()
            return OpenCheckDecision.Proceed
        }
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
            OpenCheckMode.DuringFocus ->
                if (context.focusActive) OpenCheckDecision.ShowCheck(appId, now)
                else OpenCheckDecision.Proceed
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

    /** The user chose Open on the check sheet. */
    fun onProceeded(appId: String, now: Instant) {
        grantedApp = appId
        grantedUntil = now.plus(GRANT_WINDOW)
    }

    /** The user went back (or dismissed the sheet). */
    fun onTurnedBack(appId: String) {
        if (appId == grantedApp) clearGrant()
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
    }
}
