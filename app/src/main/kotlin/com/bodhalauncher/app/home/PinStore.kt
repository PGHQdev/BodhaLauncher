package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * The user's pinned actions (ordered) and hidden suggestions, persisted locally
 * (ADR 0009). Ids are package names; labels resolve at render time.
 */
class PinStore(context: Context) {

    private val prefs = context.getSharedPreferences("home_pins", Context.MODE_PRIVATE)

    val pinned = mutableStateOf(load(KEY_PINNED))
    val hidden = mutableStateOf(load(KEY_HIDDEN).toSet())

    fun pin(id: String) {
        if (id in pinned.value) return
        pinned.value = pinned.value + id
        persist(KEY_PINNED, pinned.value)
    }

    fun unpin(id: String) {
        pinned.value = pinned.value - id
        persist(KEY_PINNED, pinned.value)
    }

    /** Hides a suggestion for good; never applies to pins. */
    fun hide(id: String) {
        hidden.value = hidden.value + id
        persist(KEY_HIDDEN, hidden.value.toList())
    }

    private fun load(key: String): List<String> =
        prefs.getString(key, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    private fun persist(key: String, ids: Collection<String>) {
        prefs.edit { putString(key, ids.joinToString("\n")) }
    }

    private companion object {
        const val KEY_PINNED = "pinned"
        const val KEY_HIDDEN = "hidden"
    }
}
