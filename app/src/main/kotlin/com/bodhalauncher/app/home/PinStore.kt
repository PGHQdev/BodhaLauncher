package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * The user's pinned actions (ordered) and hidden suggestions, persisted locally
 * (ADR 0009). Ids are package names; labels resolve at render time.
 *
 * Pins belong to an arrangement (ADR 0016): a context mode switches Home's pins
 * and nothing else, so `hidden` stays global. The unnamed default arrangement is
 * the only one that exists today and is always the active one. Its pins keep the
 * pre-arrangement key, so an upgrader's pins carry across in order with nothing
 * to migrate and nothing to lose on a rollback.
 */
class PinStore(context: Context) {

    private val prefs = context.getSharedPreferences("home_pins", Context.MODE_PRIVATE)

    private val active = DEFAULT_ARRANGEMENT

    val pinned = mutableStateOf(pinsOf(active))
    val hidden = mutableStateOf(load(KEY_HIDDEN).toSet())

    /**
     * The pins of [arrangement], in order. An arrangement nobody has pinned to
     * has none of its own and never borrows the default arrangement's.
     */
    fun pinsOf(arrangement: String): List<String> = load(keyOf(arrangement))

    fun pin(id: String) {
        if (id in pinned.value) return
        pinned.value = pinned.value + id
        persist(keyOf(active), pinned.value)
    }

    fun unpin(id: String) {
        pinned.value = pinned.value - id
        persist(keyOf(active), pinned.value)
    }

    /** Hides a suggestion for good; never applies to pins. */
    fun hide(id: String) {
        hidden.value = hidden.value + id
        persist(KEY_HIDDEN, hidden.value.toList())
    }

    fun unhide(id: String) {
        hidden.value = hidden.value - id
        persist(KEY_HIDDEN, hidden.value.toList())
    }

    private fun keyOf(arrangement: String): String =
        if (arrangement == DEFAULT_ARRANGEMENT) KEY_PINNED else "$KEY_PINNED.$arrangement"

    private fun load(key: String): List<String> =
        prefs.getString(key, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    private fun persist(key: String, ids: Collection<String>) {
        prefs.edit { putString(key, ids.joinToString("\n")) }
    }

    companion object {
        /** The default arrangement is unnamed (ADR 0016); the empty id is reserved for it. */
        const val DEFAULT_ARRANGEMENT = ""

        private const val KEY_PINNED = "pinned"
        private const val KEY_HIDDEN = "hidden"
    }
}
