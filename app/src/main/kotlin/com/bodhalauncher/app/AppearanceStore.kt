package com.bodhalauncher.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.ClockFormat
import com.bodhalauncher.engine.DateFormat
import com.bodhalauncher.engine.ThemeChoice

/**
 * Appearance's three choices, persisted locally (ADR 0009): theme, clock format
 * and date format (#141).
 *
 * The first user preference Settings stores, so it settles where preferences
 * live — the same per-store `SharedPreferences` idiom the sixteen built stores
 * already use, one file per store, state held in `mutableStateOf` so a change
 * recomposes whoever reads it. A settings-wide preference blob was not built:
 * every store here is scoped to what it is about, and a section that later needs
 * its own is one more file rather than a growing shared one.
 *
 * It is read before anything composes, which is what makes "no flash of the
 * previous palette" true by construction rather than by a transition — the first
 * frame is already drawn in the chosen theme.
 */
class AppearanceStore(context: Context) {

    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    /**
     * System, so an untouched install follows the platform exactly as the theme
     * did when its dark side had no other switch. Twenty-four hour and the
     * weekday form are likewise what Home already drew, so a device that had no
     * choice to make keeps the launcher it had.
     */
    val theme = mutableStateOf(read("theme", ThemeChoice.entries, ThemeChoice.System))
    val clock = mutableStateOf(read("clock", ClockFormat.entries, ClockFormat.TwentyFourHour))
    val date = mutableStateOf(read("date", DateFormat.entries, DateFormat.WeekdayAndMonth))

    fun set(value: ThemeChoice) = write("theme", value) { theme.value = value }

    fun set(value: ClockFormat) = write("clock", value) { clock.value = value }

    fun set(value: DateFormat) = write("date", value) { date.value = value }

    /**
     * In memory first, then on disk: what recomposes is the state, and the write
     * is what makes it survive. [holdInMemory] is the caller's because the three
     * values are separately typed — one state field cannot hold all three.
     */
    private fun write(key: String, value: Enum<*>, holdInMemory: () -> Unit) {
        holdInMemory()
        prefs.edit { putString(key, value.name) }
    }

    /**
     * Stored by name rather than by ordinal, and an unrecognised one falls back:
     * a downgrade, or an entry dropped from one of these enums, then costs the
     * setting rather than the launcher.
     */
    private fun <T : Enum<T>> read(key: String, options: List<T>, fallback: T): T =
        prefs.getString(key, null)?.let { stored -> options.firstOrNull { it.name == stored } }
            ?: fallback
}
