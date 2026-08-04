package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryReducerTest {

    private fun app(label: String) = HomeAction(id = label.lowercase(), label = label)

    @Test
    fun `empty inputs yield an empty library`() {
        val library = resolveLibrary(LibraryInputs())

        assertTrue(library.rows.isEmpty())
    }

    @Test
    fun `rows sort alphabetically regardless of input order`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("Signal"), app("Camera"), app("Notes")))
        )

        assertEquals(listOf("Camera", "Notes", "Signal"), library.rows.map { it.label })
    }

    @Test
    fun `ordering ignores case`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("iNaturalist"), app("Instagram"), app("infra")))
        )

        assertEquals(listOf("iNaturalist", "infra", "Instagram"), library.rows.map { it.label })
    }

    @Test
    fun `query filters to labels containing it anywhere`() {
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Instagram"), app("Telegram"), app("Camera")),
                query = "gram",
            )
        )

        assertEquals(listOf("Instagram", "Telegram"), library.rows.map { it.label })
    }

    @Test
    fun `query matching ignores case`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("Signal"), app("Camera")), query = "SIG")
        )

        assertEquals(listOf("Signal"), library.rows.map { it.label })
    }

    @Test
    fun `query with no match yields no rows`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("Signal")), query = "zzz")
        )

        assertTrue(library.rows.isEmpty())
    }

    @Test
    fun `blank query leaves the full list`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("Signal"), app("Camera")), query = "  ")
        )

        assertEquals(listOf("Camera", "Signal"), library.rows.map { it.label })
    }

    @Test
    fun `every app passed in appears in the rows`() {
        val apps = listOf(app("A"), app("B"), app("C"), app("D"))

        val library = resolveLibrary(LibraryInputs(apps = apps))

        assertEquals(apps.toSet(), library.rows.toSet())
    }
}
