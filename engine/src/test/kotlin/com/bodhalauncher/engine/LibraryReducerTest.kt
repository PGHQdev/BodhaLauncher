package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `index lists only letters that have apps, with their first row`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("Arc"), app("Anki"), app("Camera")))
        )

        assertEquals(
            listOf(LibraryIndexEntry('A', 0), LibraryIndexEntry('C', 2)),
            library.index,
        )
    }

    @Test
    fun `labels not starting with a letter bucket under hash first`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("Signal"), app("1Password")))
        )

        assertEquals(
            listOf(LibraryIndexEntry('#', 0), LibraryIndexEntry('S', 1)),
            library.index,
        )
    }

    @Test
    fun `empty library has an empty index`() {
        assertTrue(resolveLibrary(LibraryInputs()).index.isEmpty())
    }

    @Test
    fun `index follows the filtered rows, not all apps`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("Arc"), app("Camera")), query = "cam")
        )

        assertEquals(listOf(LibraryIndexEntry('C', 0)), library.index)
    }

    @Test
    fun `hidden apps leave the rows and collect in the hidden section`() {
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Signal"), app("Instagram"), app("Camera")),
                hidden = setOf("instagram"),
            )
        )

        assertEquals(listOf("Camera", "Signal"), library.rows.map { it.label })
        assertEquals(listOf("Instagram"), library.hiddenRows.map { it.label })
    }

    @Test
    fun `hidden apps stay out of search results by default`() {
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Instagram"), app("Signal")),
                hidden = setOf("instagram"),
                query = "insta",
            )
        )

        assertTrue(library.rows.isEmpty())
        assertTrue(library.hiddenRows.isEmpty())
    }

    @Test
    fun `hidden apps match searches when configured searchable`() {
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Instagram"), app("Signal")),
                hidden = setOf("instagram"),
                hiddenSearchable = true,
                query = "insta",
            )
        )

        assertTrue(library.rows.isEmpty())
        assertEquals(listOf("Instagram"), library.hiddenRows.map { it.label })
    }

    @Test
    fun `index ignores the hidden section`() {
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Arc"), app("Zulip")),
                hidden = setOf("zulip"),
            )
        )

        assertEquals(listOf(LibraryIndexEntry('A', 0)), library.index)
    }

    @Test
    fun `compact icons keeps the alphabetical rows and index`() {
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Signal"), app("Camera")),
                layout = LibraryLayout.CompactIcons,
            )
        )

        assertEquals(listOf("Camera", "Signal"), library.rows.map { it.label })
        assertEquals(2, library.index.size)
        assertTrue(library.sections.isEmpty())
    }

    @Test
    fun `categories groups rows into titled sections, uncategorized last`() {
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Signal"), app("Chess"), app("Doom"), app("Ledger")),
                layout = LibraryLayout.Categories,
                categories = mapOf("chess" to "Games", "doom" to "Games", "signal" to "Social"),
            )
        )

        assertEquals(listOf("Games", "Social", "Other"), library.sections.map { it.title })
        assertEquals(listOf("Chess", "Doom"), library.sections[0].rows.map { it.label })
        assertEquals(listOf("Ledger"), library.sections[2].rows.map { it.label })
    }

    @Test
    fun `categories respects hidden apps and search`() {
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Chess"), app("Doom"), app("Signal")),
                layout = LibraryLayout.Categories,
                categories = mapOf("chess" to "Games", "doom" to "Games"),
                hidden = setOf("doom"),
                query = "ches",
            )
        )

        assertEquals(listOf("Games"), library.sections.map { it.title })
        assertEquals(listOf("Chess"), library.sections[0].rows.map { it.label })
    }

    @Test
    fun `categories layout has no scrubber index`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("Chess")), layout = LibraryLayout.Categories)
        )

        assertTrue(library.index.isEmpty())
    }

    @Test
    fun `recent orders by last use, never-used apps last alphabetically`() {
        val now = 1_000_000_000L
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Anki"), app("Signal"), app("Camera"), app("Doom")),
                layout = LibraryLayout.Recent,
                lastUsed = mapOf("camera" to now - 60_000, "signal" to now - 5_000),
                now = now,
            )
        )

        assertEquals(listOf("Signal", "Camera", "Anki", "Doom"), library.rows.map { it.label })
        assertNull(library.layoutNote)
    }

    @Test
    fun `recent without usage access falls back to alphabetical with a note`() {
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Signal"), app("Camera")),
                layout = LibraryLayout.Recent,
                lastUsed = null,
            )
        )

        assertEquals(listOf("Camera", "Signal"), library.rows.map { it.label })
        assertEquals("Recents need usage access", library.layoutNote)
    }

    @Test
    fun `rows carry last-used context in minutes, hours or days`() {
        val now = 10_000_000_000L
        val library = resolveLibrary(
            LibraryInputs(
                apps = listOf(app("Signal"), app("Camera"), app("Anki"), app("Doom")),
                lastUsed = mapOf(
                    "signal" to now - 8 * 60_000,
                    "camera" to now - 3 * 3_600_000,
                    "anki" to now - 2 * 86_400_000,
                    "doom" to now - 30_000,
                ),
                now = now,
            )
        )

        assertEquals("Last used 8 minutes ago", library.rowContext["signal"])
        assertEquals("Last used 3 hours ago", library.rowContext["camera"])
        assertEquals("Last used 2 days ago", library.rowContext["anki"])
        assertEquals("Just now", library.rowContext["doom"])
    }

    @Test
    fun `absent usage metadata means absent context`() {
        val library = resolveLibrary(
            LibraryInputs(apps = listOf(app("Signal")), lastUsed = null)
        )

        assertTrue(library.rowContext.isEmpty())
    }

    @Test
    fun `every app passed in appears in the rows`() {
        val apps = listOf(app("A"), app("B"), app("C"), app("D"))

        val library = resolveLibrary(LibraryInputs(apps = apps))

        assertEquals(apps.toSet(), library.rows.toSet())
    }
}
