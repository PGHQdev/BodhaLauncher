package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContextModesTest {

    @Test
    fun `no choice resolves to the default arrangement`() {
        assertNull(resolveArrangement(listOf("Work", "Rest"), null))
    }

    @Test
    fun `a choice that exists resolves to itself`() {
        assertEquals("Work", resolveArrangement(listOf("Work", "Rest"), "Work"))
    }

    @Test
    fun `a deleted choice falls back to the default with no intermediate state`() {
        assertNull(resolveArrangement(listOf("Rest"), "Work"))
        assertNull(resolveArrangement(emptyList(), "Work"))
    }

    @Test
    fun `a blank name is refused`() {
        assertEquals(ModeNameError.Blank, validateModeName("   ", emptyList()))
        assertEquals(ModeNameError.Blank, validateModeName("", emptyList()))
    }

    @Test
    fun `a name longer than the cap is refused after trimming`() {
        assertEquals(ModeNameError.TooLong, validateModeName("a".repeat(25), emptyList()))
        assertNull(validateModeName("  " + "a".repeat(24) + "  ", emptyList()))
    }

    @Test
    fun `a case-insensitive duplicate is refused`() {
        assertEquals(ModeNameError.Duplicate, validateModeName("work", listOf("Work")))
        assertEquals(ModeNameError.Duplicate, validateModeName(" WORK ", listOf("Work")))
        assertNull(validateModeName("Rest", listOf("Work")))
    }
}
