package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchReducerTest {

    private fun app(label: String) = HomeAction(id = label.lowercase(), label = label)

    private fun shortcut(label: String, appId: String, id: String = label.lowercase()) =
        SearchShortcut(id = id, appId = appId, label = label)

    private val installed = listOf(app("Instagram"), app("Telegram"), app("Camera"))

    private fun labels(search: SearchState, section: SearchSection): List<String> =
        search.sections.firstOrNull { it.section == section }?.rows?.map { it.label }.orEmpty()

    @Test
    fun `search opens listing nothing at all`() {
        val search = resolveSearch(SearchInputs(apps = installed))

        assertTrue(search.sections.isEmpty())
        assertFalse(search.nothingFound, "an untyped query has found nothing rather than failed to")
    }

    @Test
    fun `a query holding no words returns to the opening state`() {
        val whitespace = resolveSearch(SearchInputs(apps = installed, query = "   "))
        val punctuation = resolveSearch(SearchInputs(apps = installed, query = "..."))

        assertEquals(resolveSearch(SearchInputs(apps = installed)), whitespace)
        assertEquals(resolveSearch(SearchInputs(apps = installed)), punctuation)
    }

    @Test
    fun `a query matches word-boundary prefixes, not substrings`() {
        val prefix = resolveSearch(SearchInputs(apps = installed, query = "insta"))
        val midWord = resolveSearch(SearchInputs(apps = installed, query = "gram"))

        assertEquals(listOf("Instagram"), labels(prefix, SearchSection.Apps))
        assertTrue(midWord.sections.isEmpty())
        assertTrue(midWord.nothingFound, "a typed query with no match owes an empty state")
    }

    @Test
    fun `results sort alphabetically ignoring case`() {
        val search = resolveSearch(
            SearchInputs(apps = listOf(app("infra"), app("Instagram"), app("iNaturalist")), query = "in")
        )

        assertEquals(listOf("iNaturalist", "infra", "Instagram"), labels(search, SearchSection.Apps))
    }

    @Test
    fun `a hidden app stays out of results while the toggle keeps it out`() {
        val search = resolveSearch(
            SearchInputs(apps = installed, query = "insta", hidden = setOf("instagram"))
        )

        assertTrue(search.sections.isEmpty())
        assertTrue(search.nothingFound)
    }

    @Test
    fun `a hidden app matches once the toggle allows it, in the one list`() {
        val search = resolveSearch(
            SearchInputs(
                apps = installed,
                query = "insta",
                hidden = setOf("instagram"),
                hiddenSearchable = true,
            )
        )

        assertEquals(listOf("Instagram"), labels(search, SearchSection.Apps))
    }

    @Test
    fun `shortcuts match on label in their own section, after apps`() {
        val search = resolveSearch(
            SearchInputs(
                apps = installed,
                shortcuts = listOf(shortcut("New chat", appId = "telegram")),
                query = "new",
            )
        )

        assertEquals(listOf(SearchSection.Shortcuts), search.sections.map { it.section })
        assertEquals(listOf("New chat"), labels(search, SearchSection.Shortcuts))
    }

    @Test
    fun `sections come out in the fixed order whatever matched`() {
        val search = resolveSearch(
            SearchInputs(
                apps = installed,
                shortcuts = listOf(shortcut("Camera roll", appId = "instagram")),
                query = "cam",
            )
        )

        assertEquals(
            listOf(SearchSection.Apps, SearchSection.Shortcuts),
            search.sections.map { it.section },
        )
        assertEquals(listOf("Camera"), labels(search, SearchSection.Apps))
        assertEquals(listOf("Camera roll"), labels(search, SearchSection.Shortcuts))
    }

    @Test
    fun `a shortcut whose app also matched is dropped for that query`() {
        val search = resolveSearch(
            SearchInputs(
                apps = installed,
                shortcuts = listOf(shortcut("Telegram saved", appId = "telegram")),
                query = "tele",
            )
        )

        assertEquals(listOf("Telegram"), labels(search, SearchSection.Apps))
        assertTrue(labels(search, SearchSection.Shortcuts).isEmpty())
    }

    @Test
    fun `a shortcut whose app is not installed never appears`() {
        val search = resolveSearch(
            SearchInputs(
                apps = installed,
                shortcuts = listOf(shortcut("New note", appId = "gone.app")),
                query = "new",
            )
        )

        assertTrue(search.sections.isEmpty())
        assertTrue(search.nothingFound)
    }

    @Test
    fun `a hidden app takes its shortcuts with it, and the toggle brings both back`() {
        val inputs = SearchInputs(
            apps = installed,
            shortcuts = listOf(shortcut("New chat", appId = "telegram")),
            query = "new",
            hidden = setOf("telegram"),
        )

        assertTrue(resolveSearch(inputs).sections.isEmpty())
        assertEquals(
            listOf("New chat"),
            labels(resolveSearch(inputs.copy(hiddenSearchable = true)), SearchSection.Shortcuts),
        )
    }

    @Test
    fun `shortcuts sort alphabetically within their section`() {
        val search = resolveSearch(
            SearchInputs(
                apps = installed,
                shortcuts = listOf(
                    shortcut("New selfie", appId = "camera"),
                    shortcut("New chat", appId = "telegram"),
                ),
                query = "new",
            )
        )

        assertEquals(listOf("New chat", "New selfie"), labels(search, SearchSection.Shortcuts))
    }
}
