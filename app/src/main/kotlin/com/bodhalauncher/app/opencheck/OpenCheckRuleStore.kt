package com.bodhalauncher.app.opencheck

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.OpenCheckRule
import com.bodhalauncher.engine.ScheduleWindow
import java.time.Duration

/**
 * The user's per-app Open Check rules (#8), persisted locally (ADR 0009).
 * Ids are app ids as the catalog issues them; no rule means the app just opens.
 * One line per rule: mode, threshold minutes, schedule window, id — absent
 * config as "-"; the two-field lines of the mode-only era still parse.
 */
class OpenCheckRuleStore(context: Context) {

    private val prefs = context.getSharedPreferences("open_check_rules", Context.MODE_PRIVATE)

    val rules = mutableStateOf(load())

    fun ruleFor(id: String): OpenCheckRule? = rules.value[id]

    fun set(id: String, rule: OpenCheckRule) {
        rules.value = rules.value + (id to rule)
        persist()
    }

    fun remove(id: String) {
        rules.value = rules.value - id
        persist()
    }

    private fun load(): Map<String, OpenCheckRule> =
        prefs.getString(KEY_RULES, "").orEmpty().split('\n')
            .filter { it.isNotEmpty() }
            .mapNotNull(::parse)
            .toMap()

    private fun parse(line: String): Pair<String, OpenCheckRule>? {
        val fields = line.split('\t')
        val mode = OpenCheckMode.entries.find { it.name == fields.first() } ?: return null
        return when (fields.size) {
            2 -> fields[1].takeIf { it.isNotEmpty() }?.let { it to OpenCheckRule(mode) }
            4 -> {
                val id = fields[3].takeIf { it.isNotEmpty() } ?: return null
                id to OpenCheckRule(
                    mode = mode,
                    dailyThreshold = fields[1].toLongOrNull()?.let(Duration::ofMinutes),
                    window = parseWindow(fields[2]),
                )
            }
            else -> null
        }
    }

    private fun parseWindow(field: String): ScheduleWindow? {
        val (start, end) = field.split('-').takeIf { it.size == 2 } ?: return null
        return ScheduleWindow(start.toIntOrNull() ?: return null, end.toIntOrNull() ?: return null)
    }

    private fun persist() {
        val lines = rules.value.entries.joinToString("\n") { (id, rule) ->
            listOf(
                rule.mode.name,
                rule.dailyThreshold?.toMinutes()?.toString() ?: "-",
                rule.window?.let { "${it.startMinute}-${it.endMinute}" } ?: "-",
                id,
            ).joinToString("\t")
        }
        prefs.edit { putString(KEY_RULES, lines) }
    }

    private companion object {
        const val KEY_RULES = "rules"
    }
}
