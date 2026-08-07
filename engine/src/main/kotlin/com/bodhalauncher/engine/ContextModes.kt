package com.bodhalauncher.engine

import java.time.LocalDateTime

/** Mode names cap at 24 characters — longer makes the Home label meaningless (#155). */
const val MODE_NAME_MAX = 24

/** Why a mode name is refused, named on the spot at create and rename (#155). */
enum class ModeNameError { Blank, TooLong, Duplicate }

/**
 * One context mode (ADR 0016): a name the user gave a Home arrangement, and an
 * optional daily window it takes over in. It switches Home's pins and nothing
 * else — no rules, no notification behaviour, no ranking.
 *
 * The window is Open Check's [ScheduleWindow], reused rather than reinvented, so
 * "start after end crosses midnight" means the same thing in both places.
 */
data class ContextMode(val name: String, val window: ScheduleWindow? = null)

/**
 * A switch the user made by hand, and when (#156).
 *
 * A null [mode] is the default arrangement chosen deliberately, which is a
 * switch like any other and expires like one — otherwise picking Default would
 * be the one choice that silently disabled every schedule for good.
 *
 * Stamped in local time because what it expires against is a wall-clock
 * boundary: a zone or DST change moves both sides together, and nothing is
 * cached for it to leave stale.
 */
data class ManualSwitch(val mode: String?, val at: LocalDateTime)

/**
 * Validates a mode name against the modes that exist (ADR 0016, #155): trimmed,
 * non-empty, at most [MODE_NAME_MAX] characters, unique case-insensitively —
 * two modes called "Work" make the selector ambiguous. Null means the trimmed
 * name is acceptable.
 */
fun validateModeName(name: String, existing: Collection<String>): ModeNameError? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> ModeNameError.Blank
        trimmed.length > MODE_NAME_MAX -> ModeNameError.TooLong
        existing.any { it.equals(trimmed, ignoreCase = true) } -> ModeNameError.Duplicate
        else -> null
    }
}

/**
 * The next moment any window opens or closes, strictly after [from] (#156).
 *
 * Both edges of every window count, because either is a moment the schedules'
 * own answer changes — which is exactly what a manual switch is waiting to hand
 * back to. Null means no mode has a window at all, and so there is no boundary
 * ahead: the #155 behaviour, where a manual switch holds until the user changes
 * it, survives as the case where this returns nothing.
 */
fun nextWindowBoundary(modes: List<ContextMode>, from: LocalDateTime): LocalDateTime? {
    val edges = modes.mapNotNull { it.window }
        .flatMap { listOf(it.startMinute, it.endMinute) }
        .distinct()
        .sorted()
    if (edges.isEmpty()) return null
    val midnight = from.toLocalDate().atStartOfDay()
    val next = edges.firstOrNull { it > minuteOfDay(from) }
    return if (next != null) midnight.plusMinutes(next.toLong())
    else midnight.plusDays(1).plusMinutes(edges.first().toLong())
}

/**
 * The active arrangement (#155, #156, ADR 0016): the manual switch while it
 * holds, else the first mode whose window is open, else the unnamed default
 * arrangement (null).
 *
 * **First matching window wins**, which is why modes are ordered: windows will
 * overlap and something has to break the tie the same way every time.
 *
 * A manual switch holds until the next window boundary and not one moment
 * longer, so the schedules the user built resume on their own at a time they can
 * predict — rather than at the 4am day boundary, which would disable every
 * schedule for a day, or never, which would stop them working with no sign of why.
 *
 * Pure in [now]: nothing is cached against a clock, so a zone or DST change
 * re-evaluates rather than leaving an arrangement stuck from before it.
 */
fun resolveArrangement(
    modes: List<ContextMode>,
    switch: ManualSwitch?,
    now: LocalDateTime,
): String? {
    if (switch != null && !manualSwitchExpired(switch, modes, now)) {
        // A mode deleted under its own switch falls back with no intermediate
        // state, exactly as it did before schedules existed.
        return switch.mode?.takeIf { name -> modes.any { it.name == name } }
    }
    return modes.firstOrNull { it.window?.contains(minuteOfDay(now)) == true }?.name
}

/** Whether the schedules have taken back over from [switch] (#156). */
fun manualSwitchExpired(
    switch: ManualSwitch,
    modes: List<ContextMode>,
    now: LocalDateTime,
): Boolean {
    val boundary = nextWindowBoundary(modes, switch.at) ?: return false
    return !now.isBefore(boundary)
}

private fun minuteOfDay(at: LocalDateTime): Int = at.hour * 60 + at.minute
