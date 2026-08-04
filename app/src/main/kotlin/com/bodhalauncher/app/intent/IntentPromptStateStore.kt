package com.bodhalauncher.app.intent

import android.content.Context
import androidx.core.content.edit
import com.bodhalauncher.engine.IntentPromptState
import java.time.Instant

/** Persists the trigger-engine snapshot locally (ADR 0009: nothing leaves the phone). */
class IntentPromptStateStore(context: Context) {

    private val prefs = context.getSharedPreferences("intent_prompt", Context.MODE_PRIVATE)

    fun save(state: IntentPromptState) {
        prefs.edit {
            putString(KEY_RECENT_STARTS, state.recentStarts.joinToString(",") { it.toEpochMilli().toString() })
            putLong(KEY_COOLDOWN_UNTIL, state.cooldownUntil?.toEpochMilli() ?: NONE)
        }
    }

    fun load(): IntentPromptState {
        if (!prefs.contains(KEY_RECENT_STARTS)) return IntentPromptState.Initial
        val starts = prefs.getString(KEY_RECENT_STARTS, "").orEmpty()
            .split(',')
            .filter { it.isNotEmpty() }
            .map { Instant.ofEpochMilli(it.toLong()) }
        return IntentPromptState(
            recentStarts = starts,
            cooldownUntil = prefs.getLong(KEY_COOLDOWN_UNTIL, NONE)
                .takeIf { it != NONE }?.let(Instant::ofEpochMilli),
        )
    }

    private companion object {
        const val KEY_RECENT_STARTS = "recentStarts"
        const val KEY_COOLDOWN_UNTIL = "cooldownUntil"
        const val NONE = -1L
    }
}
