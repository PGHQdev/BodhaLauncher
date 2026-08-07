package com.bodhalauncher.engine

/**
 * The onboarding steps, in order (ADR 0018: promise, essentials, friction,
 * first intention, become home). All five are built; [BecomeHome] is the step
 * the completion flag resolves on — granted, declined or skipped, the flow
 * completes there and never opens again.
 *
 * Onboarding is a flow, not a surface: it is deliberately absent from [Surface],
 * which is what keeps it out of swipes, Search and every Home control.
 */
enum class OnboardingStep {
    Promise,
    Essentials,
    Friction,
    FirstIntention,
    BecomeHome,
}

/**
 * Which step the flow opens at (#135).
 *
 * The inputs are the completion flag and the progress marker — the furthest
 * step passed, skips included — and nothing else. Written store state cannot
 * tell a skipped step from an unreached one (ADR 0018 bans a draft layer), so
 * the marker is what stops a user who skipped a step being re-offered it on
 * resume.
 *
 * Returns null when the flow has nothing left to show: either the completion
 * flag is set, or the marker has passed every built step — the caller's cue to
 * write the flag and hand off to Home.
 */
fun resolveOnboardingStep(complete: Boolean, furthestPassed: Int): OnboardingStep? {
    if (complete) return null
    return OnboardingStep.entries.getOrNull(furthestPassed.coerceAtLeast(0))
}
