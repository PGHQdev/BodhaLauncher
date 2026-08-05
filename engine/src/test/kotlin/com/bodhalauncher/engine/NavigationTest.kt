package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NavigationTest {

    @Test
    fun `root is Home`() {
        assertEquals(Surface.Home, resolveRoot(focusRunning = false))
    }

    @Test
    fun `root is Focus while a session runs`() {
        assertEquals(Surface.Focus, resolveRoot(focusRunning = true))
    }

    @Test
    fun `focus running defaults to none`() {
        assertEquals(Surface.Home, resolveRoot())
    }

    @Test
    fun `back on root does nothing`() {
        assertNull(resolveBack(Place(Surface.Home)))
    }

    @Test
    fun `back on Focus does nothing while a session runs`() {
        assertNull(resolveBack(Place(Surface.Focus), focusRunning = true))
    }

    @Test
    fun `back from Home returns to Focus while a session runs`() {
        assertEquals(Place(Surface.Focus), resolveBack(Place(Surface.Home), focusRunning = true))
    }

    @Test
    fun `back from every surface other than root returns to root`() {
        val offRoot = Surface.entries.filter { it != Surface.Home }

        offRoot.forEach { surface ->
            assertEquals(
                Place(Surface.Home),
                resolveBack(Place(surface)),
                "back from $surface",
            )
        }
    }

    @Test
    fun `back from every surface other than Focus returns to Focus while a session runs`() {
        val offRoot = Surface.entries.filter { it != Surface.Focus }

        offRoot.forEach { surface ->
            assertEquals(
                Place(Surface.Focus),
                resolveBack(Place(surface), focusRunning = true),
                "back from $surface",
            )
        }
    }

    @Test
    fun `back inside a surface returns to that surface, not to root`() {
        assertEquals(
            Place(Surface.Settings),
            resolveBack(Place(Surface.Settings, depth = 1)),
        )
    }

    @Test
    fun `back inside root's own depth returns to root rather than doing nothing`() {
        assertEquals(
            Place(Surface.Home),
            resolveBack(Place(Surface.Home, depth = 1)),
        )
    }

    @Test
    fun `depth beyond the permitted level is not representable`() {
        assertFailsWith<IllegalArgumentException> { Place(Surface.Settings, depth = 2) }
    }

    @Test
    fun `the surface list names every surface the glossary names`() {
        val titles = Surface.entries.map { it.title }

        assertEquals(
            listOf("Home", "Search", "App Library", "Awareness", "Today", "Focus", "Settings"),
            titles,
        )
    }
}
