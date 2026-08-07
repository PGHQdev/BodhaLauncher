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
 * One of Bodha's own Settings rows (#191, ADR 0019): people search for the
 * control — "theme", "export", "delete" — so the individual row is the target
 * and the section it sits in is not. Selecting one goes to Settings and stands
 * on the row, which is why it wears the chevron a [SurfaceResult] does.
 *
 * Matched on [SettingsRow.label], the string the row is drawn under, so what
 * answers and what appears are the same word.
 */
data class SettingsRowResult(val row: SettingsRow) : SearchResult {
    override val key get() = "settings-row:${row.id}"
    override val label get() = row.label
}

/**
 * A ranked result: what matched, and the one-line reason it sits where it does.
 * [reason] cites only a tier that actually lifted the row — an exact match or the
 * user's own choice. Match quality is every row's baseline, so it explains nothing
 * and a row ranked on it alone carries no line (#182). The launch log is silent
 * too, and for a different reason: it did lift the row, but it says something
 * about the user rather than about the result (#183).
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
 * What Bodha's launch log (ADR 0013) says about one app: how many launches of it
 * the log still holds, and the most recent of them. Search's last ranking tier
 * (#183, ADR 0014) is these two numbers and nothing else.
 *
 * [opens] is deliberately a bare count over the whole retained log rather than a
 * decayed score. Retention already cuts the log at 30 days under
 * [RetentionCategory.RawUsageEvents], so the count *is* "how often lately" and
 * the decay is the retention window doing its job — a second, invented decay
 * curve would be the unexplainable ranking ADR 0014 rules out, and there would be
 * nothing to show a user who asked why this row is here.
 */
data class LaunchTally(val opens: Int, val lastOpened: LocalDateTime)

/**
 * Folds the launch log into one tally per app (#183).
 *
 * It is a fold in `:engine` rather than a `GROUP BY` at the DAO because what
 * counts as "how often" and "how recently" is the product decision this tier is
 * about, and a decision that lives in SQL is a decision no unit test drives. The
 * caller hands over an already-read list — every retained record, unwindowed —
 * and gets back a map keyed by the catalog's app id, which is what
 * [SearchInputs.launches] is keyed by, so the join needs no bridge.
 *
 * The tally is **not** clamped by the Awareness entitlement window and does not
 * consult the reader's Awareness exclusions. Search renders no history, so there
 * is nothing for the cap to bound; and taking an app out of Awareness must not
 * quietly make it harder to find — the control for keeping an app out of Search
 * is the App Library's hide (CONTEXT.md, **Excluded**).
 */
fun resolveLaunchTallies(launches: List<LaunchRecord>): Map<String, LaunchTally> =
    launches.groupBy(LaunchRecord::appId).mapValues { (_, records) ->
        LaunchTally(opens = records.size, lastOpened = records.maxOf(LaunchRecord::at))
    }

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
    /**
     * Bodha's own Settings rows, from [SETTINGS_ROWS] — the catalogue that sits
     * beside the row definitions, so a row added there is findable by
     * construction and Search keeps no second list (ADR 0019). The privacy
     * dashboard's rows are members of that one flat catalogue like any other, so
     * "delete" reaches delete-local-data without the domain learning about
     * sub-screens.
     *
     * Handed in rather than read, for [surfaces]' reason: the host owns which
     * rows this build renders, so a row whose control is absent never appears.
     */
    val settingsRows: List<SettingsRow> = emptyList(),
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
    /**
     * The launch log folded per app by [resolveLaunchTallies] — the last ranking
     * tier's whole input (#183).
     *
     * Non-nullable, unlike [calendarInstances]: a null there means a read still in
     * flight, and a section that must stay absent until it lands. This log needs
     * no permission and no provider, and the empty map is the entire degraded
     * case — with nothing recorded every row tallies zero, the tier separates
     * nothing, and the ranking is exactly what the earlier three tiers left.
     */
    val launches: Map<String, LaunchTally> = emptyMap(),
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
 * section is ADR 0014's four tiers, all of them now built, each a number the user
 * could be shown (#182): an exact label match, then the user's own explicit
 * choice — the per-query default above the Home pin (#185) — then how early the
 * match landed, then how often and how recently Bodha opened it (#183).
 * Alphabetical sits under all four, so ties never swap between keystrokes.
 *
 * The last tier reads [SearchInputs.launches] and therefore only ever reaches app
 * rows. Nothing else in any section has a launch record to its name: a shortcut
 * opens through its own path and writes none, a contact, an event, a surface and
 * a settings row are not launches at all. It is also the one tier that lifts a row
 * without saying so — a row it moved falls through to no reason line, because
 * "you open this a lot" is a sentence about the user rather than about the result
 * (ADR 0014 permits a line here and does not require one).
 *
 * Sections never interleave: ranking runs inside each one.
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
    // Bodha's own settings are reached rather than found, so they share the
    // actions section too (#191). Rows only: a section is a category, and people
    // search for the control (ADR 0019).
    val settingsRows = inputs.settingsRows
        .filter { matchesQuery(it.label, inputs.query) }
        .map(::SettingsRowResult)

    val sections = listOf(
        SearchSectionState(SearchSection.Apps, rank(apps, inputs)),
        SearchSectionState(SearchSection.Shortcuts, rank(shortcuts, inputs)),
        SearchSectionState(SearchSection.Contacts, contacts),
        SearchSectionState(SearchSection.Calendar, calendar),
        SearchSectionState(
            SearchSection.Actions,
            rank(actions + surfaces + focusActions + settingsRows, inputs),
        ),
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
    // The launch log reaches app rows and no others (#183); everything else in a
    // section tallies nothing because nothing else is ever launched through the
    // opening path that writes the log.
    fun tally(result: SearchResult) = if (result is AppResult) inputs.launches[result.app.id] else null
    fun opens(result: SearchResult) = tally(result)?.opens ?: 0
    // The floor is only ever compared against another floor: the clause above has
    // already separated every row with opens from every row without, so a row that
    // was never opened meets nothing but its equals here.
    fun lastOpened(result: SearchResult) = tally(result)?.lastOpened ?: LocalDateTime.MIN
    val ordered = results.sortedWith(
        compareByDescending(::exact)
            // The per-query default sits above the pin: both are tier two's
            // explicit choice, but one was made about this very query (#185).
            .thenByDescending(::chosen)
            .thenByDescending(::pinned)
            .thenBy(::depth)
            // How often before how recently (#183): the log is already cut at 30
            // days, so a count is "how often lately" and sorting by the last open
            // first would both double-count that decay and let one stray tap
            // outrank an app opened every morning. Swapping these two lines is the
            // whole of the opposite decision, if it ever proves wrong.
            .thenByDescending(::opens)
            .thenByDescending(::lastOpened)
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
