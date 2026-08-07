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

/**
 * Something Search can do rather than something it found — an Android settings
 * screen today (#188). The provider hands over only actions this device can
 * honour; the engine matches labels and knows nothing of intents.
 *
 * [keywords] are the spellings people type that the label's own words cannot
 * answer for — "wifi" against "Wi-Fi", whose words are "wi" and "fi". Matching
 * stays the one word-boundary rule, run over each keyword as over the label.
 */
data class SearchAction(
    val id: String,
    val label: String,
    val keywords: List<String> = emptyList(),
)

data class ActionResult(val action: SearchAction) : SearchResult {
    override val key get() = "action:${action.id}"
    override val label get() = action.label
}

/**
 * A ranked result: what matched, and the one-line reason it sits where it does.
 * [reason] cites only a tier that actually lifted the row — an exact match or the
 * user's own pin. Match quality is every row's baseline, so it explains nothing
 * and a row ranked on it alone carries no line (#182).
 */
data class SearchRow(val result: SearchResult, val reason: String? = null)

/** A section that has matches; sections without any are absent rather than empty. */
data class SearchSectionState(val section: SearchSection, val rows: List<SearchRow>)

/** The reason line under a result whose label is the whole query. */
const val REASON_EXACT_MATCH = "Exact match"

/** The reason line under a result the user pinned to Home. */
const val REASON_PINNED = "Pinned to Home"

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
    /** Actions this device can honour; the actions section draws exactly these, matched. */
    val actions: List<SearchAction> = emptyList(),
    /** What has been typed; [isBlankQuery] text lists nothing at all. */
    val query: String = "",
    /** App ids the user has hidden in the App Library (#62). */
    val hidden: Set<String> = emptySet(),
    /** App ids pinned to Home — the explicit user choice ADR 0014 ranks second. */
    val pinned: Set<String> = emptySet(),
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
 * section is ADR 0014's tiers, each a number the user could be shown (#182): an
 * exact label match first, then the user's own pin, then how early the match
 * landed, and alphabetical last so ties never swap between keystrokes. The two
 * remaining tiers — per-query defaults (#185) and the launch log (#183) — slot
 * between these as their stores arrive. Sections never interleave: ranking runs
 * inside each one.
 */
fun resolveSearch(inputs: SearchInputs): SearchState {
    if (isBlankQuery(inputs.query)) return SearchState(sections = emptyList(), nothingFound = false)
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

    val actions = inputs.actions
        .filter { action ->
            matchesQuery(action.label, inputs.query) ||
                action.keywords.any { matchesQuery(it, inputs.query) }
        }
        .map(::ActionResult)

    val sections = listOf(
        SearchSectionState(SearchSection.Apps, rank(apps, inputs)),
        SearchSectionState(SearchSection.Shortcuts, rank(shortcuts, inputs)),
        SearchSectionState(SearchSection.Actions, rank(actions, inputs)),
    ).filter { it.rows.isNotEmpty() }
    return SearchState(sections = sections, nothingFound = sections.isEmpty())
}

private fun rank(results: List<SearchResult>, inputs: SearchInputs): List<SearchRow> {
    fun pinned(result: SearchResult) = result is AppResult && result.app.id in inputs.pinned
    val ordered = results.sortedWith(
        compareByDescending<SearchResult> { matchesExactly(it.label, inputs.query) }
            .thenByDescending(::pinned)
            .thenBy { matchDepth(it.label, inputs.query) }
            // Locale-independent, for the reason the library's ordering is; the
            // key falls through to [SearchResult.key] so equal labels still tie
            // identically on repeat runs.
            .thenBy(String.CASE_INSENSITIVE_ORDER, SearchResult::label)
            .thenBy(SearchResult::key)
    )
    return ordered.map { result ->
        SearchRow(
            result = result,
            reason = when {
                matchesExactly(result.label, inputs.query) -> REASON_EXACT_MATCH
                pinned(result) -> REASON_PINNED
                else -> null
            },
        )
    }
}
