package com.bodhalauncher.engine

/**
 * The places you dwell in — CONTEXT.md's surface list in full.
 *
 * Settings is named here although #15 owns its shell and nothing yet uses the one
 * level of internal depth ADR 0019 grants it. Naming it now is what lets [resolveBack]
 * tell a sub-screen from a surface root before a consumer exists.
 */
enum class Surface(val title: String) {
    Home("Home"),
    Search("Search"),
    Library("App Library"),
    Awareness("Awareness"),
    Today("Today"),
    Focus("Focus"),
    Settings("Settings"),
}

/**
 * The one level of internal depth ADR 0019 grants a surface — Settings' permissions
 * list and privacy dashboard. Navigation is radial (ADR 0011), so this is a bound
 * rather than a stack: there is no depth 2 to return from.
 */
const val MAX_SURFACE_DEPTH = 1

/** Where you are: a surface, and how far inside it. */
data class Place(val surface: Surface, val depth: Int = 0) {
    init {
        require(depth in 0..MAX_SURFACE_DEPTH) { "depth $depth exceeds $MAX_SURFACE_DEPTH" }
    }
}

/**
 * The surface back and the system Home button land on: Home, except while a Focus
 * session runs (ADR 0011, ADR 0012).
 *
 * [focusRunning] has no producer yet — #9's Focus slice supplies it rather than
 * re-deciding the rule.
 */
fun resolveRoot(focusRunning: Boolean = false): Surface =
    if (focusRunning) Surface.Focus else Surface.Home

/**
 * One step out, and never more than one.
 *
 * Inside a surface's permitted depth, back returns to that surface's own root.
 * Anywhere else it returns to root, whatever route was taken to get there — there
 * is no history to walk, which is what makes the model radial rather than a stack.
 *
 * Returns null on root, meaning nothing happens: the activity is not finished and
 * no state is recreated.
 */
fun resolveBack(from: Place, focusRunning: Boolean = false): Place? = when {
    from.depth > 0 -> from.copy(depth = 0)
    from.surface == resolveRoot(focusRunning) -> null
    else -> Place(resolveRoot(focusRunning))
}
