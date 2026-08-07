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
        search.sections.firstOrNull { it.section == section }?.rows?.map { it.result.label }.orEmpty()

    private fun reasons(search: SearchState, section: SearchSection): List<String?> =
        search.sections.firstOrNull { it.section == section }?.rows?.map { it.reason }.orEmpty()

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
    fun `an exact label match outranks everything, and says so`() {
        val search = resolveSearch(
            SearchInputs(
                apps = listOf(app("Camera"), app("Camera FV-5"), app("Camcorder")),
                query = "camera",
                pinned = setOf("camera fv-5"),
            )
        )

        assertEquals(listOf("Camera", "Camera FV-5"), labels(search, SearchSection.Apps))
        assertEquals(listOf(REASON_EXACT_MATCH, REASON_PINNED), reasons(search, SearchSection.Apps))
    }

    @Test
    fun `a pin outranks match quality but not an exact match`() {
        val search = resolveSearch(
            SearchInputs(
                apps = listOf(app("Instagram"), app("iNaturalist")),
                query = "in",
                pinned = setOf("instagram"),
            )
        )

        assertEquals(listOf("Instagram", "iNaturalist"), labels(search, SearchSection.Apps))
        assertEquals(listOf(REASON_PINNED, null), reasons(search, SearchSection.Apps))
    }

    @Test
    fun `an earlier match in the label outranks a later one`() {
        val search = resolveSearch(
            SearchInputs(
                apps = listOf(app("Google Photos"), app("Photos")),
                query = "pho",
            )
        )

        assertEquals(listOf("Photos", "Google Photos"), labels(search, SearchSection.Apps))
        assertEquals(listOf(null, null), reasons(search, SearchSection.Apps))
    }

    @Test
    fun `ties order identically on repeat runs`() {
        val inputs = SearchInputs(
            apps = listOf(app("Notes"), app("Notion"), app("Notebook")),
            query = "no",
        )

        val first = resolveSearch(inputs)
        assertEquals(first, resolveSearch(inputs))
        assertEquals(listOf("Notebook", "Notes", "Notion"), labels(first, SearchSection.Apps))
    }

    @Test
    fun `a row lifted by no tier carries no reason line`() {
        val search = resolveSearch(SearchInputs(apps = installed, query = "insta"))

        assertEquals(listOf<String?>(null), reasons(search, SearchSection.Apps))
    }

    @Test
    fun `actions match in their own section, last in the fixed order`() {
        val search = resolveSearch(
            SearchInputs(
                apps = listOf(app("Wickr")),
                actions = listOf(
                    SearchAction(id = "settings:wifi", label = "Wi-Fi"),
                    SearchAction(id = "settings:bluetooth", label = "Bluetooth"),
                ),
                query = "wi",
            )
        )

        assertEquals(
            listOf(SearchSection.Apps, SearchSection.Actions),
            search.sections.map { it.section },
        )
        assertEquals(listOf("Wi-Fi"), labels(search, SearchSection.Actions))
    }

    @Test
    fun `a keyword match ranks as a label match would`() {
        val search = resolveSearch(
            SearchInputs(
                actions = listOf(
                    SearchAction(id = "settings:wifi", label = "Wi-Fi", keywords = listOf("wifi")),
                    SearchAction(id = "settings:wifi-direct", label = "Wifi Direct"),
                ),
                query = "wifi",
            )
        )

        assertEquals(listOf("Wi-Fi", "Wifi Direct"), labels(search, SearchSection.Actions))
        assertEquals(listOf(REASON_EXACT_MATCH, null), reasons(search, SearchSection.Actions))
    }

    @Test
    fun `an action absent from the inputs never appears`() {
        val search = resolveSearch(
            SearchInputs(
                actions = listOf(SearchAction(id = "settings:wifi", label = "Wi-Fi")),
                query = "blue",
            )
        )

        assertTrue(search.sections.isEmpty())
        assertTrue(search.nothingFound)
    }

    @Test
    fun `a surface answers to its name in the actions section`() {
        val search = resolveSearch(
            SearchInputs(
                surfaces = listOf(Surface.Home, Surface.Library, Surface.Today),
                query = "app",
            )
        )

        assertEquals(listOf(SearchSection.Actions), search.sections.map { it.section })
        assertEquals(listOf("App Library"), labels(search, SearchSection.Actions))
        assertTrue(search.sections.single().rows.single().result is SurfaceResult)
    }

    @Test
    fun `a surface not handed in never appears`() {
        val search = resolveSearch(
            SearchInputs(surfaces = listOf(Surface.Home, Surface.Today), query = "aware")
        )

        assertTrue(search.sections.isEmpty())
        assertTrue(search.nothingFound)
    }

    @Test
    fun `surfaces and device actions share the actions section, ranked together`() {
        val search = resolveSearch(
            SearchInputs(
                actions = listOf(SearchAction(id = "settings:apps", label = "Apps")),
                surfaces = listOf(Surface.Library),
                query = "app",
            )
        )

        assertEquals(listOf("App Library", "Apps"), labels(search, SearchSection.Actions))
    }

    @Test
    fun `a default for the query puts its result first, and names the choice`() {
        val search = resolveSearch(
            SearchInputs(
                apps = listOf(app("Instagram"), app("iNaturalist")),
                query = "in",
                defaults = mapOf("in" to "instagram"),
            )
        )

        assertEquals(listOf("Instagram", "iNaturalist"), labels(search, SearchSection.Apps))
        assertEquals(listOf(REASON_DEFAULT, null), reasons(search, SearchSection.Apps))
    }

    @Test
    fun `a default answers to the query however it is typed, but only to that query`() {
        val inputs = SearchInputs(
            apps = listOf(app("Instagram"), app("iNaturalist")),
            defaults = mapOf("in" to "instagram"),
        )

        val retyped = resolveSearch(inputs.copy(query = "  IN "))
        assertEquals(listOf(REASON_DEFAULT, null), reasons(retyped, SearchSection.Apps))

        val other = resolveSearch(inputs.copy(query = "i"))
        assertEquals(listOf(null, null), reasons(other, SearchSection.Apps))
        assertEquals(listOf("iNaturalist", "Instagram"), labels(other, SearchSection.Apps))
    }

    @Test
    fun `a default outranks a pin and loses to an exact match`() {
        val search = resolveSearch(
            SearchInputs(
                apps = listOf(app("Inbox"), app("Instagram"), app("iNaturalist")),
                query = "in",
                pinned = setOf("inaturalist"),
                defaults = mapOf("in" to "instagram"),
            )
        )

        // "Inbox" only prefix-matches; nothing is exact here, so the default leads.
        assertEquals(listOf("Instagram", "iNaturalist", "Inbox"), labels(search, SearchSection.Apps))

        val exact = resolveSearch(
            SearchInputs(
                apps = listOf(app("In"), app("Instagram")),
                query = "in",
                defaults = mapOf("in" to "instagram"),
            )
        )
        assertEquals(listOf("In", "Instagram"), labels(exact, SearchSection.Apps))
    }

    @Test
    fun `a default whose app is gone is dropped silently`() {
        val search = resolveSearch(
            SearchInputs(
                apps = listOf(app("iNaturalist"), app("Instagram")),
                query = "in",
                defaults = mapOf("in" to "uninstalled.app"),
            )
        )

        assertEquals(listOf("iNaturalist", "Instagram"), labels(search, SearchSection.Apps))
        assertEquals(listOf(null, null), reasons(search, SearchSection.Apps))
    }

    @Test
    fun `clearing the default restores the ranking the query had before`() {
        val base = SearchInputs(
            apps = listOf(app("Instagram"), app("iNaturalist")),
            query = "in",
        )

        val before = resolveSearch(base)
        val cleared = resolveSearch(base.copy(defaults = emptyMap()))
        assertEquals(before, cleared)
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

    // --- Contacts (#186) ---

    private fun contact(name: String) =
        SearchContact(contactId = name.hashCode().toLong(), lookupKey = name.lowercase(), name = name)

    @Test
    fun `contacts match by the word-boundary rule in their own section`() {
        val search = resolveSearch(
            SearchInputs(
                contacts = listOf(contact("John Okafor"), contact("Marjorie Johns"), contact("Ines")),
                query = "jo",
            )
        )

        // "jo" prefixes "John" and "Johns" at a word boundary; nothing mid-word.
        assertEquals(listOf("John Okafor", "Marjorie Johns"), labels(search, SearchSection.Contacts))
    }

    @Test
    fun `contacts order lexically with no reason lines — no affinity signal exists`() {
        val search = resolveSearch(
            SearchInputs(
                contacts = listOf(contact("john b"), contact("Adele Johnson"), contact("Johanna")),
                query = "jo",
            )
        )

        assertEquals(listOf("Adele Johnson", "Johanna", "john b"), labels(search, SearchSection.Contacts))
        assertEquals(listOf(null, null, null), reasons(search, SearchSection.Contacts))
    }

    @Test
    fun `without the contacts grant the section is a named state, never absent`() {
        val search = resolveSearch(
            SearchInputs(contacts = listOf(contact("John")), contactsGranted = false, query = "jo")
        )

        assertEquals(listOf(SEARCH_CONTACTS_OFF), labels(search, SearchSection.Contacts))
        assertTrue(
            search.nothingFound,
            "a named state is not a find: the query itself still matched nothing",
        )
    }

    @Test
    fun `a real match beside a named state clears the empty state`() {
        val search = resolveSearch(
            SearchInputs(apps = installed, contactsGranted = false, query = "insta")
        )

        assertEquals(listOf("Instagram"), labels(search, SearchSection.Apps))
        assertEquals(listOf(SEARCH_CONTACTS_OFF), labels(search, SearchSection.Contacts))
        assertFalse(search.nothingFound)
    }

    // --- Calendar (#187) ---

    private fun instance(
        title: String,
        beginHour: Int = 10,
        day: Int = 7,
        visible: Boolean = true,
        declined: Boolean = false,
    ) = ProviderInstance(
        eventId = title.hashCode().toLong(),
        title = title,
        allDay = false,
        begin = java.time.LocalDateTime.of(2026, 8, day, beginHour, 0),
        end = java.time.LocalDateTime.of(2026, 8, day, beginHour + 1, 0),
        calendarVisible = visible,
        selfDeclined = declined,
    )

    @Test
    fun `calendar matches event titles, chronologically, declined and hidden dropped`() {
        val search = resolveSearch(
            SearchInputs(
                calendarInstances = listOf(
                    instance("Dentist follow-up", day = 20),
                    instance("Dentist appointment", day = 12),
                    instance("Dentist declined", day = 8, declined = true),
                    instance("Dentist hidden", day = 9, visible = false),
                    instance("Standup", day = 10),
                ),
                query = "dent",
            )
        )

        assertEquals(
            listOf("Dentist appointment", "Dentist follow-up"),
            labels(search, SearchSection.Calendar),
        )
    }

    @Test
    fun `the calendar section is absent while its read is still in flight`() {
        val search = resolveSearch(SearchInputs(apps = installed, calendarInstances = null, query = "insta"))

        assertEquals(listOf(SearchSection.Apps), search.sections.map { it.section })
    }

    @Test
    fun `without the calendar grant the section is a named state, never absent`() {
        val search = resolveSearch(SearchInputs(calendarGranted = false, query = "dent"))

        assertEquals(listOf(SEARCH_CALENDAR_OFF), labels(search, SearchSection.Calendar))
    }

    @Test
    fun `the searched range is 7 days back through 30 forward of the day boundary`() {
        // 2am belongs to the previous day key (ADR 0003), so the boundary is yesterday's 4am.
        val (from, until) = searchCalendarWindow(java.time.LocalDateTime.of(2026, 8, 7, 2, 0))

        assertEquals(java.time.LocalDateTime.of(2026, 7, 30, 4, 0), from)
        assertEquals(java.time.LocalDateTime.of(2026, 9, 5, 4, 0), until)
    }

    @Test
    fun `sections keep the fixed order — contacts, calendar, between shortcuts and actions`() {
        val search = resolveSearch(
            SearchInputs(
                apps = listOf(app("Dent")),
                contacts = listOf(contact("Dentist Dan")),
                calendarInstances = listOf(instance("Dentist")),
                actions = listOf(SearchAction(id = "a", label = "Dental settings")),
                query = "dent",
            )
        )

        assertEquals(
            listOf(SearchSection.Apps, SearchSection.Contacts, SearchSection.Calendar, SearchSection.Actions),
            search.sections.map { it.section },
        )
    }

    // --- Focus setups (#190) ---

    @Test
    fun `a previous Focus label matches by the word-boundary rule in the actions section`() {
        val setup = FocusSetup(label = "Deep work", minutes = 60, allowedAppIds = setOf("com.a"))
        val search = resolveSearch(SearchInputs(focusSetups = listOf(setup), query = "wo"))

        val rows = search.sections.first { it.section == SearchSection.Actions }.rows
        assertEquals(listOf("Deep work"), rows.map { it.result.label })
        assertEquals(setup, (rows.single().result as FocusActionResult).setup)
    }

    @Test
    fun `with no previous sessions the focus domain contributes nothing at all`() {
        val search = resolveSearch(SearchInputs(apps = installed, query = "focus"))

        assertTrue(search.sections.none { section -> section.rows.any { it.result is FocusActionResult } })
        assertTrue(search.nothingFound)
    }
}
