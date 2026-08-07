package com.bodhalauncher.engine

import java.time.LocalDateTime

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

/** A contact as Search sees it (#186): a name to match, and the keys that open it. */
data class SearchContact(val contactId: Long, val lookupKey: String, val name: String)

data class ContactResult(val contact: SearchContact) : SearchResult {
    override val key get() = "contact:${contact.lookupKey}"
    override val label get() = contact.name
}

/**
 * One matched calendar instance (#187), carried as the day slot's own
 * [DayEvent] so selecting it opens through the same edge Today uses. A
 * recurring event repeats within the searched window, so the key takes the
 * instance's begin as well as the event id.
 */
data class EventResult(val event: DayEvent) : SearchResult {
    override val key get() = "event:${event.eventId}@${event.begin}"
    override val label get() = event.title
}

/**
 * A previous Focus setup offered as something Search can start (#190). The row
 * says the label — the user's own words — and selecting it starts a session
 * with that setup's duration and allowed apps.
 */
data class FocusActionResult(val setup: FocusSetup) : SearchResult {
    override val key get() = "focus:${setup.label}"
    override val label get() = setup.label
}

/**
 * An optional-permission section without its grant (#186, #187): the named
 * state that stands where the results would, never an empty section and never
 * a silent omission. It is a result so it renders as a vocabulary row, keys
 * into the lazy list, and sits on the keyboard route like everything else;
 * activating it enters the capability-education flow — a tap on "turn it on"
 * is an explicit user request, so the education always shows (#157).
 *
 * [capability] rides along so the section-to-capability mapping exists once,
 * here, rather than as a second cascade at the surface.
 */
data class UngrantedResult(val section: SearchSection, val capability: Capability) : SearchResult {
    override val key get() = "ungranted:$section"
    override val label
        get() = when (capability) {
            Capability.Contacts -> SEARCH_CONTACTS_OFF
            else -> SEARCH_CALENDAR_OFF
        }
}

/** The contacts section's named state without the grant (#186). */
const val SEARCH_CONTACTS_OFF = "Contact search is off"

/** The calendar section's named state without the grant (#187). */
const val SEARCH_CALENDAR_OFF = "Calendar search is off"

/**
 * A surface offered by its name (#189): the universal route to everything past
 * Home's four swipes. Selecting one navigates, which is why its row alone wears
 * a chevron. Matched on [Surface.title] — the name the user sees is the name
 * that answers.
 */
data class SurfaceResult(val surface: Surface) : SearchResult {
    override val key get() = "surface:${surface.name}"
    override val label get() = surface.title
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

/** The reason line under the result the user chose for this query (#185). */
const val REASON_DEFAULT = "Your choice for this search"

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
    /**
     * The surfaces the host actually renders, in [Surface]'s own vocabulary — the
     * host owns which have shipped, so an unbuilt one never appears as a result.
     */
    val surfaces: List<Surface> = emptyList(),
    /** What has been typed; [isBlankQuery] text lists nothing at all. */
    val query: String = "",
    /** App ids the user has hidden in the App Library (#62). */
    val hidden: Set<String> = emptySet(),
    /** App ids pinned to Home — the explicit user choice ADR 0014 ranks second. */
    val pinned: Set<String> = emptySet(),
    /**
     * "When I type this, this one first" (#185): [canonicalQuery] to the chosen
     * app id. An id no longer in [apps] is ignored — the default disappears
     * silently and the query ranks as it otherwise would.
     */
    val defaults: Map<String, String> = emptyMap(),
    /** Whether hidden apps may match; off, a query never surfaces them. */
    val hiddenSearchable: Boolean = false,
    /**
     * Whether the contacts grant stood when this query began (#186). Defaults
     * true — like every input here, the default means the domain contributes
     * nothing; false is the explicit fact that puts the named state up.
     */
    val contactsGranted: Boolean = true,
    /** Every contact, unranked; matching and the lexical order happen here. */
    val contacts: List<SearchContact> = emptyList(),
    /** Whether the calendar grant stood when this query began (#187); defaults as [contactsGranted] does. */
    val calendarGranted: Boolean = true,
    /**
     * The provider's expanded instances over [searchCalendarWindow], or null
     * while the read is still in flight — the calendar section fills in beneath
     * the local sections when the rows arrive, and until then it is absent
     * rather than empty (#187). Declined and hidden-calendar instances are
     * dropped here, so the edge stays the same reader Today uses.
     */
    val calendarInstances: List<ProviderInstance>? = null,
    /** Previous Focus setups, most recent first; their labels match like any other (#190). */
    val focusSetups: List<FocusSetup> = emptyList(),
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
/** How far behind the current day boundary calendar search reads (#187). */
const val SEARCH_CALENDAR_DAYS_BACK = 7L

/** How far past the current day boundary calendar search reads (#187). */
const val SEARCH_CALENDAR_DAYS_FORWARD = 30L

/**
 * The range calendar search reads (#187): the 7 days before the current day
 * boundary through the 30 after it — wide enough for "when is that dentist
 * appointment" and last week's meeting, narrow enough that a two-letter prefix
 * does not return a year of standups. One constant pair, cheap to change.
 */
fun searchCalendarWindow(now: LocalDateTime): Pair<LocalDateTime, LocalDateTime> {
    val boundary = dayStart(now)
    return boundary.minusDays(SEARCH_CALENDAR_DAYS_BACK) to boundary.plusDays(SEARCH_CALENDAR_DAYS_FORWARD)
}

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

    // ADR 0014: contacts order lexically and nothing else — no affinity signal
    // exists on the minimum supported Android, so none is faked. No reason
    // lines either: no tier ever lifts a contact row (#186).
    val contacts: List<SearchRow> = when {
        !inputs.contactsGranted ->
            listOf(SearchRow(UngrantedResult(SearchSection.Contacts, Capability.Contacts)))
        else -> inputs.contacts
            .filter { matchesQuery(it.name, inputs.query) }
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER, SearchContact::name)
                    .thenBy(SearchContact::lookupKey)
            )
            .map { SearchRow(ContactResult(it)) }
    }

    // Chronological, not tiered (#187): when a title matches, "which one" is a
    // question about time, and begin order is the one explainable rank an
    // event has. Declined and hidden-calendar instances drop, as on Today.
    val calendar: List<SearchRow> = when {
        !inputs.calendarGranted ->
            listOf(SearchRow(UngrantedResult(SearchSection.Calendar, Capability.Calendar)))
        inputs.calendarInstances == null -> emptyList()
        else -> inputs.calendarInstances
            .filter { it.calendarVisible && !it.selfDeclined && matchesQuery(it.title, inputs.query) }
            .map { DayEvent(it.eventId, it.title, it.allDay, it.begin, it.end) }
            .sortedWith(compareBy(DayEvent::begin, DayEvent::end, DayEvent::title))
            .map { SearchRow(EventResult(it)) }
    }

    val actions = inputs.actions
        .filter { action ->
            matchesQuery(action.label, inputs.query) ||
                action.keywords.any { matchesQuery(it, inputs.query) }
        }
        .map(::ActionResult)
    // Going somewhere is an action rather than a thing found, so surfaces share
    // the actions section rather than adding a sixth heading (#189).
    val surfaces = inputs.surfaces
        .filter { matchesQuery(it.title, inputs.query) }
        .map(::SurfaceResult)
    // A Focus setup is something Search can do, so it shares the actions
    // section (#190). With no previous sessions the list is empty and the
    // domain contributes nothing — no placeholder and no invitation.
    val focusActions = inputs.focusSetups
        .filter { matchesQuery(it.label, inputs.query) }
        .map(::FocusActionResult)

    val sections = listOf(
        SearchSectionState(SearchSection.Apps, rank(apps, inputs)),
        SearchSectionState(SearchSection.Shortcuts, rank(shortcuts, inputs)),
        SearchSectionState(SearchSection.Contacts, contacts),
        SearchSectionState(SearchSection.Calendar, calendar),
        SearchSectionState(SearchSection.Actions, rank(actions + surfaces + focusActions, inputs)),
    ).filter { it.rows.isNotEmpty() }
    // A named state is not a find: with only ungranted rows standing, the query
    // itself still matched nothing, and the one empty state says so above them.
    val nothingFound = sections.all { section -> section.rows.all { it.result is UngrantedResult } }
    return SearchState(sections = sections, nothingFound = nothingFound)
}

private fun rank(results: List<SearchResult>, inputs: SearchInputs): List<SearchRow> {
    fun pinned(result: SearchResult) = result is AppResult && result.app.id in inputs.pinned
    val defaultId = inputs.defaults[canonicalQuery(inputs.query)]
        ?.takeIf { id -> inputs.apps.any { it.id == id } }
    fun chosen(result: SearchResult) = result is AppResult && result.app.id == defaultId
    // An action's keywords rank as its label does — "wifi" against Wi-Fi is an
    // exact answer, and matched-by-keyword must not sort as matched-nowhere.
    fun names(result: SearchResult): List<String> =
        if (result is ActionResult) listOf(result.label) + result.action.keywords
        else listOf(result.label)
    fun exact(result: SearchResult) = names(result).any { matchesExactly(it, inputs.query) }
    fun depth(result: SearchResult) = names(result).minOf { matchDepth(it, inputs.query) }
    val ordered = results.sortedWith(
        compareByDescending(::exact)
            // The per-query default sits above the pin: both are tier two's
            // explicit choice, but one was made about this very query (#185).
            .thenByDescending(::chosen)
            .thenByDescending(::pinned)
            .thenBy(::depth)
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
                exact(result) -> REASON_EXACT_MATCH
                chosen(result) -> REASON_DEFAULT
                pinned(result) -> REASON_PINNED
                else -> null
            },
        )
    }
}
