package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * Per-query defaults (#185): canonical query to the chosen app id, persisted
 * locally (ADR 0009). A default whose app is uninstalled is left in place —
 * the reducer ignores it, so it costs nothing and comes back to life if the
 * app returns.
 */
class SearchDefaultStore(context: Context) {

    private val prefs = context.getSharedPreferences("search_defaults", Context.MODE_PRIVATE)

    val defaults = mutableStateOf(load())

    fun set(query: String, appId: String) {
        prefs.edit { putString(query, appId) }
        defaults.value = load()
    }

    fun clear(query: String) {
        prefs.edit { remove(query) }
        defaults.value = load()
    }

    @Suppress("UNCHECKED_CAST")
    private fun load(): Map<String, String> = prefs.all as Map<String, String>
}
