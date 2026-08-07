package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchingTest {

    private val corpus = listOf(
        "Instagram",
        "Telegram",
        "Signal",
        "Camera",
        "iNaturalist",
        "1Password",
        "WhatsApp",
        "Café Racer",
        "John's app",
        "Files by Google",
        "X (formerly Twitter)",
        "パスワード",
    )

    private fun matching(query: String) = corpus.filter { matchesQuery(it, query) }

    @Test
    fun `a query matches a prefix of a word`() {
        assertTrue(matchesQuery("Instagram", "insta"))
        assertTrue(matchesQuery("Instagram", "instagram"))
    }

    @Test
    fun `a query does not match mid-word`() {
        assertTrue(matching("gram").isEmpty())
    }

    @Test
    fun `a query longer than the word does not match`() {
        assertFalse(matchesQuery("Instagram", "instagrams"))
    }

    @Test
    fun `matching ignores case on both sides`() {
        assertTrue(matchesQuery("Signal", "SIG"))
        assertTrue(matchesQuery("SIGNAL", "sig"))
    }

    @Test
    fun `matching folds accents on both sides`() {
        assertTrue(matchesQuery("Café Racer", "cafe"))
        assertTrue(matchesQuery("Cafe Racer", "café"))
    }

    @Test
    fun `a kana voicing mark is part of the letter, not an accent`() {
        assertTrue(matchesQuery("パスワード", "パス"))
        assertFalse(matchesQuery("パスワード", "ハス"))
        assertFalse(matchesQuery("ハスワート", "パス"))
    }

    @Test
    fun `a later word is matchable by its own prefix`() {
        assertTrue(matchesQuery("Files by Google", "goo"))
        assertFalse(matchesQuery("Files by Google", "oogle"))
    }

    @Test
    fun `every word of a multi-word query has to match`() {
        assertTrue(matchesQuery("Files by Google", "fil goo"))
        assertFalse(matchesQuery("Files by Google", "fil zzz"))
        assertFalse(matchesQuery("Instagram", "insta gram"))
    }

    @Test
    fun `two query words may match the same word`() {
        assertTrue(matchesQuery("Instagram", "in in"))
    }

    @Test
    fun `punctuation starts a new word in the label`() {
        assertTrue(matchesQuery("John's app", "jo"))
        assertTrue(matchesQuery("John's app", "s"))
        assertTrue(matchesQuery("X (formerly Twitter)", "twit"))
    }

    @Test
    fun `punctuation separates words in the query too`() {
        assertTrue(matchesQuery("X (formerly Twitter)", "(twit"))
        assertTrue(matchesQuery("John's app", "john's"))
    }

    @Test
    fun `a case change or a digit is not a word boundary`() {
        assertFalse(matchesQuery("iNaturalist", "nat"))
        assertFalse(matchesQuery("1Password", "pass"))
        assertFalse(matchesQuery("WhatsApp", "app"))
        assertTrue(matchesQuery("iNaturalist", "inat"))
        assertTrue(matchesQuery("1Password", "1pass"))
    }

    @Test
    fun `an empty query matches everything`() {
        assertTrue(matchesQuery("Instagram", ""))
        assertEquals(corpus, matching(""))
    }

    @Test
    fun `a whitespace-only query matches everything`() {
        assertEquals(corpus, matching("   "))
    }

    @Test
    fun `a punctuation-only query matches everything`() {
        assertEquals(corpus, matching("..."))
    }

    @Test
    fun `the corpus narrows to what the rule allows`() {
        assertEquals(listOf("Instagram"), matching("insta"))
        assertEquals(listOf("Camera", "Café Racer"), matching("ca"))
        assertTrue(matching("zzz").isEmpty())
    }
}
