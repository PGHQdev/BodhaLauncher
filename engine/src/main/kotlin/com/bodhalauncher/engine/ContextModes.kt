package com.bodhalauncher.engine

/** Mode names cap at 24 characters — longer makes the Home label meaningless (#155). */
const val MODE_NAME_MAX = 24

/** Why a mode name is refused, named on the spot at create and rename (#155). */
enum class ModeNameError { Blank, TooLong, Duplicate }

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
 * The active arrangement, from the ordered mode list and the manual choice and
 * nothing else (#155, ADR 0016): the chosen mode when it still exists, else the
 * unnamed default arrangement (null). A deleted or never-made choice falls back
 * with no intermediate state, and with no schedules yet a manual choice simply
 * holds until the user changes it.
 */
fun resolveArrangement(modes: List<String>, manualChoice: String?): String? =
    manualChoice?.takeIf { it in modes }
