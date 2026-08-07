package com.bodhalauncher.app.ui

import androidx.compose.runtime.saveable.SaverScope
import com.bodhalauncher.engine.HomeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The one sheet slot (ADR 0011, #133). What is asserted here is the structural
 * claim the ADR makes — a new sheet replaces the open one, and there is no way
 * to express two — rather than any one sheet's behaviour.
 */
class SheetSlotTest {

    private val maps = HomeAction("maps", "Maps")
    private val notes = HomeAction("notes", "Notes")

    @Test
    fun `a new sheet replaces the open one`() {
        val slot = SheetSlot()
        val first = Sheet.AppActions(maps)
        val second = Sheet.OpenCheck(notes)

        slot.open(first)
        slot.open(second)

        assertSame(second, slot.current)
    }

    @Test
    fun `the replaced sheet's state goes with it`() {
        val slot = SheetSlot()
        slot.open(Sheet.OpenCheck(maps))

        slot.open(Sheet.AppActions(notes))

        assertNull(slot.showing<Sheet.OpenCheck>())
    }

    @Test
    fun `a replaced sheet is told, so what it left running stops`() {
        val slot = SheetSlot()
        var told = 0
        slot.open(Sheet.OpenCheck(maps), onReplaced = { told++ })

        slot.open(Sheet.AppActions(notes))

        assertEquals(1, told)
    }

    @Test
    fun `a sheet dismissed on its own terms is not told it was replaced`() {
        val slot = SheetSlot()
        var told = 0
        val sheet = Sheet.OpenCheck(maps)
        slot.open(sheet, onReplaced = { told++ })

        slot.close(sheet)

        assertNull(slot.current)
        assertEquals(0, told)
    }

    @Test
    fun `a replaced sheet closing late does not take the one that replaced it`() {
        val slot = SheetSlot()
        val replaced = Sheet.AppActions(maps)
        val current = Sheet.OpenCheck(notes)
        slot.open(replaced)
        slot.open(current)

        slot.close(replaced)

        assertSame(current, slot.current)
    }

    @Test
    fun `the sheet a caller asks for is the one open, or nothing`() {
        val slot = SheetSlot()
        slot.open(Sheet.AppActions(maps))

        assertEquals(maps, slot.showing<Sheet.AppActions>()?.app)
        assertNull(slot.showing<Sheet.OpenCheck>())
    }

    @Test
    fun `an in-flight Open Check survives the save and restore`() {
        val slot = SheetSlot()
        slot.open(Sheet.OpenCheck(maps))

        val restored = SheetSlot.Saver.restore(save(slot)!!)

        assertEquals(maps, restored?.showing<Sheet.OpenCheck>()?.app)
    }

    @Test
    fun `no other sheet is restored, and an empty slot saves nothing`() {
        val slot = SheetSlot()
        slot.open(Sheet.AppActions(notes))

        assertNull(save(slot))
        assertNull(save(SheetSlot()))
    }

    private fun save(slot: SheetSlot): Any? = with(SheetSlot.Saver) { AllowingSave.save(slot) }
}

/** The saver only needs somewhere to say "yes, that value can be stored". */
private object AllowingSave : SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
}
