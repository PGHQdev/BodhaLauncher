package com.bodhalauncher.engine

/** A launcher shortcut as Search sees it: an app's own entry point, matched on its label. */
data class SearchShortcut(val id: String, val appId: String, val label: String)

/**
 * ADR 0014's five sections, in the one order they ever render. The enum's own
 * declaration order *is* the section order, so a domain arriving later cannot
 * invent a position — it lands where the enum already put it.
 */
enum class SearchSection(val heading: String) {
    Apps("Apps"),
    Shortcuts("Shortcuts"),
    Contacts("Contacts"),
    Calendar("Calendar"),
    Actions("Actions"),
}

/**
 * One row Search can draw. [key] is unique across all sections — it is what a
 * lazy list keys on — and [label] is what the row says and what matching ran on.
 */
sealed interface SearchResult {
    val key: String
    val label: String
}

data class AppResult(val app: HomeAction) : SearchResult {
    override val key get() = "app:${app.id}"
    override val label get() = app.label
}

data class ShortcutResult(val shortcut: SearchShortcut) : SearchResult {
    override val key get() = "shortcut:${shortcut.appId}/${shortcut.id}"
    override val label get() = shortcut.label
}

/** A section that has matches; sections without any are absent rather than empty. */
data class SearchSectionState(val section: SearchSection, val rows: List<SearchResult>)

/**
 * What Search may draw from. Apps and launcher shortcuts are the two live domains
 * (#180, #181); the rest of ADR 0014's seven arrive as their providers do, as
 * further inputs and further sections rather than as a second matcher.
 */
data class SearchInputs(
    val apps: List<HomeAction> = emptyList(),
    /**
     * Shortcuts of available apps only — the provider never hands over a shortcut
     * whose app is gone, and [resolveSearch] additionally drops any whose app is
     * not in [apps], so a stale provider read cannot draw a dead row.
     */
    val shortcuts: List<SearchShortcut> = emptyList(),
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
 * Only the second owes an empty state, and there is one of it, over the whole
 * search — an absent section is not a failure, so no section owes its own.
 */
data class SearchState(val sections: List<SearchSectionState>, val nothingFound: Boolean)

/**
 * Resolves what Search shows.
 *
 * **It opens empty** (ADR 0014): no recents, no suggestions, nothing until a query
 * narrows something — you swiped down because you had something in mind, and an
 * empty state cannot become a browsing surface. A query holding no words returns
 * to exactly that state, so a typed-then-cleared field is indistinguishable from
 * an untouched one.
 *
 * Sections come out in [SearchSection]'s fixed order, only those with matches.
 * Nothing appears twice: a shortcut whose app also matched this query is dropped,
 * because the app row already reaches everything the shortcut names (#181). A
 * hidden app takes its shortcuts with it — hiding an app and still meeting its
 * "New chat" would be a hole in the hiding.
 *
 * Matching is [matchesQuery], the one rule every domain shares. Ordering within a
 * section is alphabetical: ADR 0014's four ranking tiers need a launch log and a
 * per-query default that this slice has neither of, and a partial tier order
 * would rank on reasons the user could not be shown.
 */
fun resolveSearch(inputs: SearchInputs): SearchState {
    if (isBlankQuery(inputs.query)) return SearchState(sections = emptyList(), nothingFound = false)
    // Locale-independent, for the reason the library's ordering is.
    val alphabetical = compareBy(String.CASE_INSENSITIVE_ORDER, SearchResult::label)
    fun visible(appId: String) = inputs.hiddenSearchable || appId !in inputs.hidden

    val apps = inputs.apps
        .filter { visible(it.id) && matchesQuery(it.label, inputs.query) }
        .map(::AppResult)
    val matchedApps = apps.mapTo(mutableSetOf()) { it.app.id }
    val installed = inputs.apps.mapTo(mutableSetOf(), HomeAction::id)
    val shortcuts = inputs.shortcuts
        .filter {
            it.appId in installed && it.appId !in matchedApps &&
                visible(it.appId) && matchesQuery(it.label, inputs.query)
        }
        .map(::ShortcutResult)

    val sections = listOf(
        SearchSectionState(SearchSection.Apps, apps.sortedWith(alphabetical)),
        SearchSectionState(SearchSection.Shortcuts, shortcuts.sortedWith(alphabetical)),
    ).filter { it.rows.isNotEmpty() }
    return SearchState(sections = sections, nothingFound = sections.isEmpty())
}
