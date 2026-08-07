package com.bodhalauncher.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.ClockFormat
import com.bodhalauncher.engine.DateFormat
import com.bodhalauncher.engine.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The first preferences Settings persists (#141). A second store built over the
 * same prefs is what "survives a restart" means here — the process is gone and
 * the choice is read back from disk.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class AppearanceStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun prefs() = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() {
        prefs().edit().clear().commit()
    }

    @Test
    fun `the defaults are the launcher as it already drew itself`() {
        val store = AppearanceStore(context)

        // System, so an untouched install follows the platform exactly as the
        // theme's own default did before there was a choice to store.
        assertEquals(ThemeChoice.System, store.theme.value)
        assertEquals(ClockFormat.TwentyFourHour, store.clock.value)
        assertEquals(DateFormat.WeekdayAndMonth, store.date.value)
    }

    @Test
    fun `each choice survives the process`() {
        AppearanceStore(context).apply {
            set(ThemeChoice.Dark)
            set(ClockFormat.Nato)
            set(DateFormat.Numeric)
        }

        val restarted = AppearanceStore(context)

        assertEquals(ThemeChoice.Dark, restarted.theme.value)
        assertEquals(ClockFormat.Nato, restarted.clock.value)
        assertEquals(DateFormat.Numeric, restarted.date.value)
    }

    @Test
    fun `setting one leaves the other two alone`() {
        val store = AppearanceStore(context)
        store.set(ClockFormat.TwelveHour)

        store.set(ThemeChoice.Light)

        assertEquals(ClockFormat.TwelveHour, AppearanceStore(context).clock.value)
    }

    @Test
    fun `a name this version no longer knows reads as the default rather than crashing`() {
        prefs().edit().putString("theme", "Sepia").commit()

        assertEquals(ThemeChoice.System, AppearanceStore(context).theme.value)
    }
}
