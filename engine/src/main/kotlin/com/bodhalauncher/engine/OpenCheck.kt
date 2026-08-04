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
