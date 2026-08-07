package com.bodhalauncher.app.home

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.ContextMode
import com.bodhalauncher.engine.ModeNameError
import com.bodhalauncher.engine.ScheduleWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class ModeStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 7, 14, 0)

    @Before
    fun clearPrefs() {
        listOf("context_modes", "home_pins").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun store(pins: PinStore = PinStore(context)) = ModeStore(context, pins)

    private fun ModeStore.names() = modes.value.map { it.name }

    @Test
    fun `starts with no modes and no switch — nothing is pre-made`() {
        assertEquals(emptyList<String>(), store().names())
        assertNull(store().switch.value)
    }

    @Test
    fun `create trims the name and survives a new instance`() {
        assertNull(store().create("  Work  "))
        assertEquals(listOf("Work"), store().names())
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
    fun `the manual switch persists across process death, with the moment it was made`() {
        val s = store()
        s.create("Work")
        s.select("Work", now)

        assertEquals("Work", store().switch.value?.mode)
        assertEquals(now, store().switch.value?.at)
    }

    /** Choosing the default arrangement is a switch, distinguishable from never choosing. */
    @Test
    fun `choosing Default is a switch of its own, not the absence of one`() {
        val s = store()
        s.select(null, now)

        assertEquals(null, store().switch.value?.mode)
        assertEquals(now, store().switch.value?.at)
    }

    @Test
    fun `rename follows the switch and carries the arrangement's pins`() {
        val pins = PinStore(context)
        val s = store(pins)
        s.create("Work")
        s.select("Work", now)
        pins.setActive("Work")
        pins.pin("com.work")

        assertNull(s.rename("Work", "Office"))

        assertEquals("Office", s.switch.value?.mode)
        assertEquals(listOf("Office"), s.names())
        assertEquals(listOf("com.work"), pins.pinsOf("Office"))
    }

    @Test
    fun `deleting the active mode drops the switch immediately`() {
        val s = store()
        s.create("Work")
        s.select("Work", now)
        s.delete("Work")
        assertNull(s.switch.value)
        assertEquals(emptyList<String>(), s.names())
    }

    @Test
    fun `a window is set, cleared, and survives a new instance`() {
        val s = store()
        s.create("Evening")
        s.setWindow("Evening", ScheduleWindow(21 * 60, 23 * 60 + 30))

        assertEquals(ScheduleWindow(1260, 1410), store().modes.value.single().window)

        s.setWindow("Evening", null)
        assertNull(store().modes.value.single().window)
    }

    @Test
    fun `a rename keeps the window it was carrying`() {
        val s = store()
        s.create("Evening")
        s.setWindow("Evening", ScheduleWindow(1260, 1410))

        assertNull(s.rename("Evening", "Wind down"))

        assertEquals(ContextMode("Wind down", ScheduleWindow(1260, 1410)), store().modes.value.single())
    }

    @Test
    fun `move reorders, and stops at each end rather than wrapping`() {
        val s = store()
        listOf("One", "Two", "Three").forEach(s::create)

        s.move("Three", -1)
        assertEquals(listOf("One", "Three", "Two"), s.names())

        s.move("Three", -1)
        assertEquals(listOf("Three", "One", "Two"), s.names())

        s.move("Three", -1)
        assertEquals(listOf("Three", "One", "Two"), s.names())

        s.move("Two", 1)
        assertEquals(listOf("Three", "One", "Two"), s.names())
    }

    @Test
    fun `moving a mode that is not there changes nothing`() {
        val s = store()
        s.create("One")
        s.move("Absent", -1)
        assertEquals(listOf("One"), s.names())
    }

    @Test
    fun `a switch is dropped once it has reached its boundary`() {
        val s = store()
        s.create("Evening")
        s.setWindow("Evening", ScheduleWindow(18 * 60, 22 * 60))
        s.create("Work")
        s.select("Work", now)

        s.expireSwitch(now.withHour(17))
        assertEquals("Work", s.switch.value?.mode)

        s.expireSwitch(now.withHour(18))
        assertNull(s.switch.value)
    }

    /**
     * The bug the write exists for: with the switch only ignored, taking the
     * window away would leave no boundary to have passed, and Home would jump
     * back to an arrangement chosen hours earlier.
     */
    @Test
    fun `a lapsed switch does not come back when its window is edited away`() {
        val s = store()
        s.create("Evening")
        s.setWindow("Evening", ScheduleWindow(18 * 60, 22 * 60))
        s.create("Work")
        s.select("Work", now)
        s.expireSwitch(now.withHour(18))

        s.setWindow("Evening", null)

        assertNull(store().switch.value)
    }

    @Test
    fun `a switch with no boundary ahead of it is never expired`() {
        val s = store()
        s.create("Work")
        s.select("Work", now)

        s.expireSwitch(now.plusYears(10))

        assertEquals("Work", store().switch.value?.mode)
    }

    /**
     * The list was newline-joined names before windows existed (#155). A device
     * that already had modes keeps them, each without a window — and with no
     * window anywhere there is no boundary, so a stamp-less choice still holds
     * exactly as it did.
     */
    @Test
    fun `modes and a choice written before windows are read as they were`() {
        context.getSharedPreferences("context_modes", Context.MODE_PRIVATE).edit()
            .putString("modes", "Work\nRest")
            .putString("choice", "Rest")
            .commit()

        val s = store()

        assertEquals(listOf("Work", "Rest"), s.names())
        assertEquals(emptyList<ScheduleWindow?>(), s.modes.value.mapNotNull { it.window })
        assertEquals("Rest", s.switch.value?.mode)
    }
}
