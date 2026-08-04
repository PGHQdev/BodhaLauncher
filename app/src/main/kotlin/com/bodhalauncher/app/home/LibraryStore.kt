package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.LibraryLayout

/** Library-only preferences, persisted locally (ADR 0009). */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    val hiddenSearchable = mutableStateOf(prefs.getBoolean(KEY_HIDDEN_SEARCHABLE, false))

    val layout = mutableStateOf(
        prefs.getString(KEY_LAYOUT, null)
            ?.let { saved -> LibraryLayout.entries.find { it.name == saved } }
            ?: LibraryLayout.Alphabetical
    )

    fun setHiddenSearchable(value: Boolean) {
        hiddenSearchable.value = value
        prefs.edit { putBoolean(KEY_HIDDEN_SEARCHABLE, value) }
    }

    fun setLayout(value: LibraryLayout) {
        layout.value = value
        prefs.edit { putString(KEY_LAYOUT, value.name) }
    }

    private companion object {
        const val KEY_HIDDEN_SEARCHABLE = "hidden_searchable"
        const val KEY_LAYOUT = "layout"
    }
}
