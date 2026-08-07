package com.bodhalauncher.engine

/** An action Home can offer — a user pin or an inferred suggestion. */
data class HomeAction(val id: String, val label: String)

/** The Intent Prompt categories; the current session's choice shapes Home. */
enum class IntentCategory {
    ContinueSomething,
    Communicate,
    Capture,
    FindSomething,
    Browse,
    JustLooking,
}

/**
 * Everything Home may draw from. Absent inputs (null/empty) mean the element is
 * absent on Home — features that don't exist or lack permission simply pass nothing.
 */
data class HomeInputs(
    val dailyIntention: String? = null,
    val contextLabel: String? = null,
    val pinned: List<HomeAction> = emptyList(),
    val suggested: List<HomeAction> = emptyList(),
    /** Action ids the user has hidden; applies to suggestions only, never pins. */
    val hidden: Set<String> = emptySet(),
    val inboxDigest: String? = null,
    val focusActive: Boolean = false,
    val sessionIntent: IntentCategory? = null,
    /** Whether Bodha holds the home role; re-read on resume (#136, ADR 0018). */
    val homeRoleHeld: Boolean = true,
)

/** Resolved Home content; the UI renders exactly this, in this order. */
data class HomeState(
    val dailyIntention: String?,
    val contextLabel: String?,
    /** At most [MAX_ACTIONS], pins first. */
    val actions: List<HomeAction>,
    val inboxDigest: String?,
    val focusActive: Boolean,
    val sessionIntent: IntentCategory?,
    val homeRoleHeld: Boolean = true,
)

const val MAX_ACTIONS = 4

/**
 * Resolves what Home shows: user pins outrank inferred suggestions, the combined
 * list caps at [MAX_ACTIONS], hidden suggestions are excluded, and absent inputs
 * yield absent elements rather than placeholders.
 */
fun resolveHome(inputs: HomeInputs): HomeState {
    val pins = inputs.pinned.take(MAX_ACTIONS)
    val pinnedIds = pins.mapTo(mutableSetOf()) { it.id }
    val suggestions = inputs.suggested
        .filter { it.id !in inputs.hidden && it.id !in pinnedIds }
        .take(MAX_ACTIONS - pins.size)
    return HomeState(
        dailyIntention = inputs.dailyIntention,
        contextLabel = inputs.contextLabel,
        actions = pins + suggestions,
        inboxDigest = inputs.inboxDigest,
        focusActive = inputs.focusActive,
        sessionIntent = inputs.sessionIntent,
        homeRoleHeld = inputs.homeRoleHeld,
    )
}
