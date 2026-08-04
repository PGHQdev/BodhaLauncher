package com.bodhalauncher.engine

import java.time.Duration
import java.time.Instant

/** Trigger configuration; everything beyond ask-every-time defaults off (ADR 0004). */
data class IntentPromptConfig(
    val askEveryTime: Boolean = false,
)

/**
 * The spec's suppression states, sampled at decision time. Detection is the
 * adapter's job; flags for states v1 can't observe simply stay false.
 */
data class SuppressionFlags(
    val callActive: Boolean = false,
    val navigationActive: Boolean = false,
    val cameraViaShortcut: Boolean = false,
    val emergencyFlow: Boolean = false,
    val returningToFocusTask: Boolean = false,
) {
    val anySet: Boolean
        get() = callActive || navigationActive || cameraViaShortcut || emergencyFlow || returningToFocusTask
}

enum class TriggerSource { Reflexive, EverySession }

/** The prompt is due for this session — the adapter presents the bottom sheet. */
data class PromptDecision(val session: SessionId, val at: Instant, val trigger: TriggerSource)

/**
 * The engine's complete state, exposed after every transition so the adapter can
 * persist it; counting and cooldown continue across process death.
 */
data class IntentPromptState(
    /** Session starts still inside the rolling window, oldest first. */
    val recentStarts: List<Instant>,
    val cooldownUntil: Instant?,
) {
    companion object {
        val Initial = IntentPromptState(recentStarts = emptyList(), cooldownUntil = null)
    }
}

/**
 * Pure trigger engine per ADR 0004: the Intent Prompt is due at the start of the
 * [REFLEXIVE_SESSION_COUNT]th session within [REFLEXIVE_WINDOW], then rests for
 * [COOLDOWN]. Only [Transition.SessionStarted] counts — a resume is the same
 * session (ADR 0001) and a peek is not a session. A set suppression flag blocks
 * the decision without consuming anything: no cooldown starts, the window keeps
 * counting. Time enters only through transition timestamps.
 */
class IntentPromptEngine(
    private val config: IntentPromptConfig = IntentPromptConfig(),
    initial: IntentPromptState = IntentPromptState.Initial,
) {

    private var recentStarts = initial.recentStarts
    private var cooldownUntil = initial.cooldownUntil

    fun snapshot(): IntentPromptState = IntentPromptState(recentStarts, cooldownUntil)

    fun onTransition(transition: Transition, suppression: SuppressionFlags): PromptDecision? {
        if (transition !is Transition.SessionStarted) return null
        val at = transition.at

        recentStarts = (recentStarts + at).filter { Duration.between(it, at) <= REFLEXIVE_WINDOW }

        val coolingDown = cooldownUntil?.isAfter(at) == true
        val trigger = when {
            // Explicit "ask every time" means every session — the cooldown never gates it.
            config.askEveryTime -> TriggerSource.EverySession
            coolingDown -> null
            recentStarts.size >= REFLEXIVE_SESSION_COUNT -> TriggerSource.Reflexive
            else -> null
        }
        if (trigger == null || suppression.anySet) return null

        cooldownUntil = at.plus(COOLDOWN)
        return PromptDecision(transition.session, at, trigger)
    }

    companion object {
        /** ADR 0004: the three numbers ship fixed in v1 and become Settings defaults later. */
        const val REFLEXIVE_SESSION_COUNT = 3
        val REFLEXIVE_WINDOW: Duration = Duration.ofMinutes(15)
        val COOLDOWN: Duration = Duration.ofMinutes(30)
    }
}
