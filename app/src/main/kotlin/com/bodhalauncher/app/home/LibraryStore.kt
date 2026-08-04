package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/** Library-only preferences, persisted locally (ADR 0009). */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    val hiddenSearchable = mutableStateOf(prefs.getBoolean(KEY_HIDDEN_SEARCHABLE, false))

    fun setHiddenSearchable(value: Boolean) {
        hiddenSearchable.value = value
        prefs.edit { putBoolean(KEY_HIDDEN_SEARCHABLE, value) }
    }

    private companion object {
        const val KEY_HIDDEN_SEARCHABLE = "hidden_searchable"
    }
}
