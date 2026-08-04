package com.bodhalauncher.engine

import java.time.Duration
import java.time.Instant

/**
 * When a rule's check fires (#8). The walking skeleton ships [Always] and
 * [Never]; the remaining trigger modes (repeated opening, daily threshold,
 * schedule, during Focus) arrive with their own tickets.
 */
enum class OpenCheckMode { Always, Never }

/** A user's per-app Open Check rule; no rule at all means the app just opens. */
data class OpenCheckRule(val mode: OpenCheckMode)

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

    /** The check is due — the adapter presents the sheet. */
    data class ShowCheck(val appId: String, val at: Instant) : OpenCheckDecision
}

/**
 * The engine's complete state. Nothing in the skeleton outlives a process, so
 * the adapter doesn't persist it yet; timed sessions (#75) will.
 */
data class OpenCheckState(
    /** The app a passed check granted entry to, and when it runs out. */
    val grantedApp: String?,
    val grantedUntil: Instant?,
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

    fun snapshot(): OpenCheckState = OpenCheckState(grantedApp, grantedUntil)

    fun onLaunchAttempt(appId: String, rule: OpenCheckRule?, now: Instant): OpenCheckDecision {
        if (appId == grantedApp && grantedUntil?.isBefore(now) == false) {
            clearGrant()
            return OpenCheckDecision.Proceed
        }
        return when (rule?.mode) {
            null, OpenCheckMode.Never -> OpenCheckDecision.Proceed
            OpenCheckMode.Always -> OpenCheckDecision.ShowCheck(appId, now)
        }
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
    }
}
