package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.DailyIntention
import com.bodhalauncher.engine.dayKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Persists the daily intention locally (ADR 0009). Home reads it through
 * [DailyIntention.textOn]; the record itself outlives its expiry (ADR 0003).
 * Today (#5) becomes the real editor; until then the temporary Home editor writes here.
 */
class IntentionStore(context: Context) {

    private val prefs = context.getSharedPreferences("daily_intention", Context.MODE_PRIVATE)

    val intention = mutableStateOf(load())

    fun set(text: String, now: LocalDateTime) {
        val value = DailyIntention(text = text, dayKey = dayKey(now))
        intention.value = value
        prefs.edit {
            putString(KEY_TEXT, value.text)
            putLong(KEY_DAY, value.dayKey.toEpochDay())
        }
    }

    fun clear() {
        intention.value = null
        prefs.edit { clear() }
    }

    private fun load(): DailyIntention? {
        val text = prefs.getString(KEY_TEXT, null) ?: return null
        return DailyIntention(text = text, dayKey = LocalDate.ofEpochDay(prefs.getLong(KEY_DAY, 0)))
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_DAY = "dayKey"
    }
}
