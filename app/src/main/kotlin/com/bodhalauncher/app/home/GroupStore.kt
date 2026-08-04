package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.LibraryGroup
import com.bodhalauncher.engine.UNGROUPED_GROUP

/**
 * The user's app groups (ordered), persisted locally (ADR 0009). Names are
 * unique; "Ungrouped" is reserved for the synthetic section the reducer emits.
 * Ids are package names; stale ones resolve away in the reducer and are pruned
 * for good when their package uninstalls.
 */
class GroupStore(context: Context) {

    private val prefs = context.getSharedPreferences("library_groups", Context.MODE_PRIVATE)

    val groups = mutableStateOf(load())

    /** Creates an empty group; blank, taken, or reserved names no-op. */
    fun create(name: String) {
        val trimmed = normalize(name) ?: return
        persist(groups.value + LibraryGroup(trimmed, emptyList()))
    }

    fun rename(from: String, to: String) {
        val trimmed = normalize(to) ?: return
        persist(groups.value.map { if (it.name == from) it.copy(name = trimmed) else it })
    }

    fun delete(name: String) {
        persist(groups.value.filter { it.name != name })
    }

    /** Assigns [appId] to the group when absent, removes it when present. */
    fun toggle(name: String, appId: String) {
        persist(groups.value.map {
            when {
                it.name != name -> it
                appId in it.appIds -> it.copy(appIds = it.appIds - appId)
                else -> it.copy(appIds = it.appIds + appId)
            }
        })
    }

    /** Prunes uninstalled packages from every group; the groups themselves stay. */
    fun removeApps(ids: Collection<String>) {
        persist(groups.value.map { it.copy(appIds = it.appIds - ids.toSet()) })
    }

    private fun normalize(name: String): String? = name
        .trim().replace(RECORD_SEPARATOR, ' ').replace(FIELD_SEPARATOR, ' ')
        .takeIf { candidate ->
            candidate.isNotEmpty() &&
                !candidate.equals(UNGROUPED_GROUP, ignoreCase = true) &&
                groups.value.none { it.name == candidate }
        }

    private fun persist(value: List<LibraryGroup>) {
        groups.value = value
        prefs.edit {
            putString(KEY_GROUPS, value.joinToString(RECORD_SEPARATOR.toString()) { group ->
                (listOf(group.name) + group.appIds).joinToString(FIELD_SEPARATOR.toString())
            })
        }
    }

    private fun load(): List<LibraryGroup> = prefs.getString(KEY_GROUPS, "").orEmpty()
        .split(RECORD_SEPARATOR)
        .filter { it.isNotEmpty() }
        .map { record ->
            val fields = record.split(FIELD_SEPARATOR)
            LibraryGroup(fields.first(), fields.drop(1).filter { it.isNotEmpty() })
        }

    private companion object {
        const val KEY_GROUPS = "groups"
        const val RECORD_SEPARATOR = '\n'
        const val FIELD_SEPARATOR = '\t'
    }
}
