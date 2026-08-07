package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.ModeNameError
import com.bodhalauncher.engine.validateModeName

/**
 * The user's context modes (ordered) and the manual choice, persisted locally
 * (ADR 0009, ADR 0016). Nothing is pre-made: the list starts empty and the
 * default arrangement is not a mode — it is the absence of a choice. Free and
 * uncapped (ADR 0005). The pins themselves live in [PinStore], one arrangement
 * per mode; this store carries the names and keeps that one in step on rename
 * and delete.
 */
class ModeStore(context: Context, private val pins: PinStore) {

    private val prefs = context.getSharedPreferences("context_modes", Context.MODE_PRIVATE)

    val modes = mutableStateOf(prefs.getString(KEY_MODES, "").orEmpty().split(SEPARATOR).filter { it.isNotEmpty() })
    val choice = mutableStateOf(prefs.getString(KEY_CHOICE, null))

    /** Creating asks only for a name; the refusal, if any, is the caller's message. */
    fun create(name: String): ModeNameError? {
        val candidate = sanitize(name)
        validateModeName(candidate, modes.value)?.let { return it }
        persistModes(modes.value + candidate.trim())
        return null
    }

    fun rename(from: String, to: String): ModeNameError? {
        val trimmed = sanitize(to).trim()
        if (trimmed == from) return null
        validateModeName(trimmed, modes.value - from)?.let { return it }
        persistModes(modes.value.map { if (it == from) trimmed else it })
        pins.renameArrangement(from, trimmed)
        if (choice.value == from) persistChoice(trimmed)
        return null
    }

    /** The record separator cannot live inside a name; GroupStore's move. */
    private fun sanitize(name: String) = name.replace(SEPARATOR, ' ')

    /** Deleting the active mode drops the choice, so Home reverts immediately. */
    fun delete(name: String) {
        persistModes(modes.value - name)
        pins.removeArrangement(name)
        if (choice.value == name) persistChoice(null)
    }

    /** The manual switch; null chooses the default arrangement. Holds until changed. */
    fun select(name: String?) = persistChoice(name)

    private fun persistModes(value: List<String>) {
        modes.value = value
        prefs.edit { putString(KEY_MODES, value.joinToString(SEPARATOR.toString())) }
    }

    private fun persistChoice(value: String?) {
        choice.value = value
        prefs.edit { if (value == null) remove(KEY_CHOICE) else putString(KEY_CHOICE, value) }
    }

    private companion object {
        const val KEY_MODES = "modes"
        const val KEY_CHOICE = "choice"
        const val SEPARATOR = '\n'
    }
}
