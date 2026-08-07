package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchReducerTest {

    private fun app(label: String) = HomeAction(id = label.lowercase(), label = label)

    private val installed = listOf(app("Instagram"), app("Telegram"), app("Camera"))

    @Test
    fun `search opens listing nothing at all`() {
        val search = resolveSearch(SearchInputs(apps = installed))

        assertTrue(search.rows.isEmpty())
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

        assertEquals(listOf("Instagram"), prefix.rows.map { it.label })
        assertTrue(midWord.rows.isEmpty())
        assertTrue(midWord.nothingFound, "a typed query with no match owes an empty state")
    }

    @Test
    fun `results sort alphabetically ignoring case`() {
        val search = resolveSearch(
            SearchInputs(apps = listOf(app("infra"), app("Instagram"), app("iNaturalist")), query = "in")
        )

        assertEquals(listOf("iNaturalist", "infra", "Instagram"), search.rows.map { it.label })
    }

    @Test
    fun `a hidden app stays out of results while the toggle keeps it out`() {
        val search = resolveSearch(
            SearchInputs(apps = installed, query = "insta", hidden = setOf("instagram"))
        )

        assertTrue(search.rows.isEmpty())
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

        assertEquals(listOf("Instagram"), search.rows.map { it.label })
    }
}
