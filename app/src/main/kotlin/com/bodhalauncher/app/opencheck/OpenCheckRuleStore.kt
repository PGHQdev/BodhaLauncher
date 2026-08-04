package com.bodhalauncher.app.opencheck

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.OpenCheckRule

/**
 * The user's per-app Open Check rules (#8), persisted locally (ADR 0009).
 * Ids are app ids as the catalog issues them; no rule means the app just opens.
 */
class OpenCheckRuleStore(context: Context) {

    private val prefs = context.getSharedPreferences("open_check_rules", Context.MODE_PRIVATE)

    val rules = mutableStateOf(load())

    fun ruleFor(id: String): OpenCheckRule? = rules.value[id]?.let(::OpenCheckRule)

    fun set(id: String, mode: OpenCheckMode) {
        rules.value = rules.value + (id to mode)
        persist()
    }

    fun remove(id: String) {
        rules.value = rules.value - id
        persist()
    }

    private fun load(): Map<String, OpenCheckMode> =
        prefs.getString(KEY_RULES, "").orEmpty().split('\n')
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val mode = line.substringBefore('\t')
                val id = line.substringAfter('\t', missingDelimiterValue = "")
                if (id.isEmpty()) return@mapNotNull null
                OpenCheckMode.entries.find { it.name == mode }?.let { id to it }
            }
            .toMap()

    private fun persist() {
        val lines = rules.value.entries.joinToString("\n") { (id, mode) -> "${mode.name}\t$id" }
        prefs.edit { putString(KEY_RULES, lines) }
    }

    private companion object {
        const val KEY_RULES = "rules"
    }
}
