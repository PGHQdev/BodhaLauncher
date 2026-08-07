package com.bodhalauncher.engine

/**
 * What Search may draw from. Apps are the one domain this slice ships (#180); the
 * other six of ADR 0014's seven arrive as their providers do, as further inputs
 * and further rows rather than as a second matcher.
 */
data class SearchInputs(
    val apps: List<HomeAction> = emptyList(),
    /** What has been typed; [isBlankQuery] text lists nothing at all. */
    val query: String = "",
    /** App ids the user has hidden in the App Library (#62). */
    val hidden: Set<String> = emptySet(),
    /** Whether hidden apps may match; off, a query never surfaces them. */
    val hiddenSearchable: Boolean = false,
)

/**
 * Resolved search content; the UI renders exactly this.
 *
 * [nothingFound] is the difference between the two states that both list no rows:
 * Search opened and nothing has been typed, versus a query that matched nothing.
 * Only the second owes an empty state, and there is one of it — with a single
 * domain shipped there is no section for a per-section one to hang off (ADR 0014).
 */
data class SearchState(val rows: List<HomeAction>, val nothingFound: Boolean)

/**
 * Resolves what Search shows.
 *
 * **It opens empty** (ADR 0014): no recents, no suggestions, nothing until a query
 * narrows something — you swiped down because you had something in mind, and an
 * empty state cannot become a browsing surface. A query holding no words returns
 * to exactly that state, so a typed-then-cleared field is indistinguishable from
 * an untouched one.
 *
 * Matching is [matchesQuery], the one rule every domain shares. Ordering is
 * alphabetical: ADR 0014's four ranking tiers need a launch log and a per-query
 * default that this slice has neither of, and a partial tier order would rank on
 * reasons the user could not be shown.
 */
fun resolveSearch(inputs: SearchInputs): SearchState {
    if (isBlankQuery(inputs.query)) return SearchState(rows = emptyList(), nothingFound = false)
    // Locale-independent, for the reason the library's ordering is.
    val alphabetical = compareBy(String.CASE_INSENSITIVE_ORDER, HomeAction::label)
    val rows = inputs.apps
        .filter { (inputs.hiddenSearchable || it.id !in inputs.hidden) && matchesQuery(it.label, inputs.query) }
        .sortedWith(alphabetical)
    return SearchState(rows = rows, nothingFound = rows.isEmpty())
}
