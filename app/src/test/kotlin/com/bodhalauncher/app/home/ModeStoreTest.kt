package com.bodhalauncher.app.home

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.ModeNameError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class ModeStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        listOf("context_modes", "home_pins").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun store(pins: PinStore = PinStore(context)) = ModeStore(context, pins)

    @Test
    fun `starts with no modes and no choice — nothing is pre-made`() {
        assertEquals(emptyList<String>(), store().modes.value)
        assertNull(store().choice.value)
    }

    @Test
    fun `create trims the name and survives a new instance`() {
        assertNull(store().create("  Work  "))
        assertEquals(listOf("Work"), store().modes.value)
    }

    @Test
    fun `a duplicate is refused case-insensitively at create and rename`() {
        val s = store()
        s.create("Work")
        s.create("Rest")
        assertEquals(ModeNameError.Duplicate, s.create("work"))
        assertEquals(ModeNameError.Duplicate, s.rename("Rest", "WORK"))
        // Renaming a mode to a case-variant of itself is not a duplicate.
        assertNull(s.rename("Work", "work"))
    }

    @Test
    fun `the manual choice persists across process death`() {
        val s = store()
        s.create("Work")
        s.select("Work")
        assertEquals("Work", store().choice.value)
    }

    @Test
    fun `rename follows the choice and carries the arrangement's pins`() {
        val pins = PinStore(context)
        val s = store(pins)
        s.create("Work")
        s.select("Work")
        pins.setActive("Work")
        pins.pin("com.work")

        assertNull(s.rename("Work", "Office"))

        assertEquals("Office", s.choice.value)
        assertEquals(listOf("Office"), s.modes.value)
        assertEquals(listOf("com.work"), pins.pinsOf("Office"))
    }

    @Test
    fun `deleting the active mode drops the choice immediately`() {
        val s = store()
        s.create("Work")
        s.select("Work")
        s.delete("Work")
        assertNull(s.choice.value)
        assertEquals(emptyList<String>(), s.modes.value)
    }
}
