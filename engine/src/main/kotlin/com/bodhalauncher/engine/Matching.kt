package com.bodhalauncher.engine

import java.text.Normalizer
import java.util.Locale

/**
 * ADR 0014's one matching rule, shared by every search domain: a query matches when
 * each of its words is a prefix of some word in the candidate, compared accent-folded
 * and case-insensitively. "insta" matches Instagram, "gram" matches neither Instagram
 * nor Telegram, "jo" matches "John Okafor". A blank query matches everything.
 *
 * Two definitions ADR 0014 leaves open, settled here:
 *
 * A word is a run of letters or digits, so every other character starts a new word —
 * "John's app" holds "john", "s" and "app", and "X (formerly Twitter)" is findable by
 * "twitter". A case change or a digit-to-letter change is not a boundary, which keeps
 * the rule statable in one sentence at the cost of "app" missing WhatsApp; inventing
 * boundaries inside a run would guess at where "eBay" or "1Password" divide.
 *
 * Each query word is tested independently, so two of them may land on the same word:
 * "in in" matches Instagram.
 */
fun matchesQuery(label: String, query: String): Boolean {
    val queryWords = foldedWords(query)
    if (queryWords.isEmpty()) return true
    val labelWords = foldedWords(label)
    return queryWords.all { word -> labelWords.any { it.startsWith(word) } }
}

/**
 * Whether [query] holds no words at all — empty, whitespace, punctuation — and so narrows
 * nothing. Callers that suppress or reshape content while a search is running ask this
 * rather than testing the raw text, so their idea of blank is [matchesQuery]'s idea of it.
 */
fun isBlankQuery(query: String): Boolean = foldedWords(query).isEmpty()

/**
 * The marks NFD splits off an accented Latin, Greek or Cyrillic letter. Deliberately
 * narrower than `\p{Mn}`, which also covers the Japanese voicing marks and the Indic
 * vowel signs: those make a different letter rather than an accented one, and stripping
 * them would let "はす" find パスワード.
 */
private val DIACRITICS = Regex("[\\u0300-\\u036F]+")
private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")

/**
 * The comparable words of [text]: accents dropped, lowercased. The case fold is
 * locale-independent for the same reason the library's ordering is — under a Turkish-ı
 * locale "Instagram" would otherwise stop answering to "i".
 */
private fun foldedWords(text: String): List<String> {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    // Recomposed so a mark that survived the strip stays part of its letter rather than
    // splitting the word it sits in.
    return Normalizer.normalize(DIACRITICS.replace(decomposed, ""), Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
        .split(NOT_A_WORD)
        .filter { it.isNotEmpty() }
}
