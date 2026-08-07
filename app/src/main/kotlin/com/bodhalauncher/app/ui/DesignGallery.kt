package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.DayEvent
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.LibraryIndexEntry
import com.bodhalauncher.engine.LibraryLayout
import com.bodhalauncher.engine.ScheduleWindow
import com.bodhalauncher.engine.SessionRecord

/**
 * The design system's living spec (#26): every token rendered once, with fixed
 * content and no clock, so screenshot tests photograph exactly the identity —
 * a drifted token fails the diff. Not reachable from the product UI.
 */
@Composable
fun DesignGallery() {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ground)
            .padding(BodhaSpacing.page),
    ) {
        Text("09:41", style = BodhaType.voiceClock, color = colors.ink)
        Text("finish the reading, then rest", style = BodhaType.voiceLine, color = colors.inkMuted)
        Spacer(Modifier.height(BodhaSpacing.xl))

        Text("Still the voice, at length", style = BodhaType.voicePassage, color = colors.ink)
        Text("what do you want to do there?", style = BodhaType.voiceInput, color = colors.inkMuted)
        Spacer(Modifier.height(BodhaSpacing.xl))

        Text("Voice title", style = BodhaType.voiceTitle, color = colors.ink)
        Text("Title — an app's own name", style = BodhaType.title, color = colors.ink)
        Text("Body — operational text and data", style = BodhaType.body, color = colors.ink)
        Text("Action", style = BodhaType.action, color = colors.accent)
        Text("Label — controls and rows", style = BodhaType.label, color = colors.ink)
        Text("OVERLINE", style = BodhaType.overline, color = colors.inkMuted)
        Text("Caption — screen-time context", style = BodhaType.caption, color = colors.inkMuted)
        Spacer(Modifier.height(BodhaSpacing.xl))

        Row {
            Swatch(colors.ground, outlined = true)
            Swatch(colors.ink)
            Swatch(colors.inkMuted)
            Swatch(colors.accent)
            Swatch(colors.error)
        }
        Spacer(Modifier.height(BodhaSpacing.xl))

        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text("Hairline rule", style = BodhaType.caption, color = colors.inkMuted,
            modifier = Modifier.padding(vertical = BodhaSpacing.s))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Spacer(Modifier.height(BodhaSpacing.xl))

        // The actionable components, which is what makes this fixture answer the
        // accessibility floor and not only visual drift (ADR 0020). A component
        // the gallery cannot see is a component neither check covers.
        // The visual vocabulary (ADR 0025). Rendered here first because the
        // vocabulary is built as components before screens adopt it, and this
        // fixture is what makes both guards see them.
        SectionOverline("Vocabulary")
        CardRow(
            title = "Card row — a block acted on once",
            subtitle = "Home actions, Today's slots, Settings",
            onClick = {},
            leading = { IconChip { Text("✎", style = BodhaType.body, color = colors.ink) } },
            trailing = { TrailingChevron() },
        )
        Spacer(Modifier.height(BodhaSpacing.s))
        CardRow(
            title = "Tinted — the current or summarising item",
            onClick = {},
            emphasis = Emphasis.Tinted,
        )
        Spacer(Modifier.height(BodhaSpacing.s))
        BodhaPill("Solid — the one primary action", onClick = {}, emphasis = Emphasis.Solid)
        Spacer(Modifier.height(BodhaSpacing.s))
        BodhaPill("Plain pill", onClick = {})
        Spacer(Modifier.height(BodhaSpacing.s))
        BodhaPill("Destructive pill", onClick = {}, destructive = true)
        Spacer(Modifier.height(BodhaSpacing.s))
        BodhaField {
            Text("Field — the pill shape, typed into", style = BodhaType.body, color = colors.inkMuted)
        }
        ListRow(
            title = "List row — one entry in a list that scrolls",
            subtitle = "Search results, Library apps, Context modes",
            onClick = {},
            trailing = { TrailingChevron() },
        )
        ListRow(
            title = "List row, tinted — the current choice",
            subtitle = "The selected context mode",
            onClick = {},
            tinted = true,
        )
        // The multi-select row (#137): picked is the accent check in the
        // trailing slot, with no fill or outline change; unavailable is a cap
        // reached, spoken as disabled.
        MultiSelectRow(title = "Multi-select row, picked", picked = true, onToggle = {})
        MultiSelectRow(title = "Multi-select row, unpicked", picked = false, onToggle = {})
        MultiSelectRow(title = "Multi-select row, unavailable — the cap reached", picked = false, onToggle = {}, enabled = false)
        Spacer(Modifier.height(BodhaSpacing.xl))

        // Today's day slot (#159): the event rows, the turn-on and the named
        // absence, with fixed times so the golden never drifts with the clock.
        SectionOverline("Day slot")
        DayEventRow(event = GALLERY_ALL_DAY_EVENT, onClick = {})
        DayEventRow(event = GALLERY_TIMED_EVENT, onClick = {})
        Spacer(Modifier.height(BodhaSpacing.s))
        CardRow(
            title = "See what's left of your day",
            subtitle = "Turn on calendar access",
            onClick = {},
        )
        Spacer(Modifier.height(BodhaSpacing.s))
        SlotNote("Nothing left on the calendar today.")
        // The Tomorrow peek (#160): one row under its label, only when the day is spent.
        Spacer(Modifier.height(BodhaSpacing.l))
        SectionOverline("Tomorrow")
        DayEventRow(event = GALLERY_TIMED_EVENT, onClick = {})
        Spacer(Modifier.height(BodhaSpacing.xl))

        // The digest slot (#161): the counts card — a chevron because it
        // navigates — and its turn-on, with fixed counts so nothing drifts.
        SectionOverline("Digest")
        DigestCard(subtitle = "3 People · 1 Time-sensitive · 5 Updates", onTap = {})
        Spacer(Modifier.height(BodhaSpacing.s))
        CardRow(
            title = "Today's notifications, counted",
            subtitle = "Turn on notification access",
            onClick = {},
        )
        Spacer(Modifier.height(BodhaSpacing.xl))

        // The daily-window editor (#74, #156): shared the moment context modes
        // became its second caller, so both guards see it rather than only the
        // Open Check rule dialog that used to own it privately.
        SectionOverline("Time window")
        WindowEditor(current = GALLERY_WINDOW, prompt = "Switch to Evening between these times") {}
        Spacer(Modifier.height(BodhaSpacing.xl))

        // Awareness's session rows (#172): the two classifications and the
        // running session, with fixed spans so the golden never drifts. Inert by
        // construction — the row is read, not activated — which is the state no
        // other specimen here shows.
        SectionOverline("Awareness sessions")
        SessionRow(GALLERY_UNCLASSIFIED_SESSION)
        SessionRow(GALLERY_INTENTIONAL_SESSION)
        SessionRow(GALLERY_RUNNING_SESSION)
        Spacer(Modifier.height(BodhaSpacing.xl))

        // The inbox row (#162): a live notification under its section, the
        // source's mark bare — the gallery has none to give, so the specimen
        // shows the row a package manager could not answer for.
        SectionOverline("Inbox")
        NotificationRow(
            title = "Gallery notification",
            line = "The latest state, from the system",
            icon = null,
            onOpen = {},
        )
        Spacer(Modifier.height(BodhaSpacing.xl))

        // The Focus session (#166, #170): setup, the running surface and the end
        // moment, with fixed content so nothing drifts. The setup sheet's picker
        // is the shared multi-select row, already specimened above; here it
        // renders with the fixture apps so the whole sheet face is photographed.
        SectionOverline("Focus session")
        FocusSetupSheetContent(
            apps = GALLERY_PICKER_APPS,
            onStart = { _, _, _ -> },
            initialLabel = "Deep work",
            initialAllowed = listOf("gallery.one"),
        )
        Spacer(Modifier.height(BodhaSpacing.l))
        FocusScreenContent(
            label = "Deep work",
            remaining = "42 minutes remaining",
            allowedAppLabels = listOf("Atlas", "Ledger"),
            onEnd = {},
        )
        Spacer(Modifier.height(BodhaSpacing.l))
        FocusEndSheetContent(
            label = "Deep work",
            durationLine = "You focused for 30 minutes.",
            reachLine = "You reached for something else once.",
            onExtend = {},
            onDone = {},
        )
        Spacer(Modifier.height(BodhaSpacing.xl))

        // Focus (ADR 0026), forced rather than requested: only one node can hold
        // real focus, and the fixture has to show the ring on all four shapes.
        // Screenshots capture at rest, so without these specimens no golden ever
        // contains the treatment.
        CompositionLocalProvider(
            LocalForceFocusRing provides true,
            // The hint retires on first use, so the fixture states the untaught
            // case: a golden recorded after someone's Right press would show a
            // row the product draws only until then (ADR 0023).
            LocalActionsKeyHint provides ActionsKeyHint(shown = true),
        ) {
            SectionOverline("Focus")
            CardRow(title = "Card row, focused", onClick = {})
            Spacer(Modifier.height(BodhaSpacing.s))
            CardRow(title = "Tinted card row, focused", onClick = {}, emphasis = Emphasis.Tinted)
            Spacer(Modifier.height(BodhaSpacing.s))
            BodhaPill("Pill, focused", onClick = {})
            Spacer(Modifier.height(BodhaSpacing.s))
            BodhaField {
                Text("Field, focused", style = BodhaType.body, color = colors.inkMuted)
            }
            ListRow(title = "List row, focused", onClick = {})
            ListRow(title = "Mode row, tinted, focused", onClick = {}, tinted = true)
            // The deliberately focused picked specimen (#137, ADR 0026): without
            // it no golden ever contains the ring on a multi-select row.
            MultiSelectRow(title = "Multi-select row, picked, focused", picked = true, onToggle = {})
            // The rows that carry per-item actions, so the hint and the
            // actions node are inside both guards rather than only on a screen.
            CardRow(title = "Pin row, focused", onClick = {}, onLongClick = {})
            ListRow(title = "App row, focused", onClick = {}, onLongClick = {})
            // The inbox row's actions (#163): handled and snooze hang off this
            // node, so the tree-walk and the Tab traversal both see the route.
            NotificationRow(
                title = "Notification row, focused",
                line = "Carries handled and snooze",
                icon = null,
                onOpen = {},
                onActions = {},
            )
            // Home's gestures, lifted here for the traversal to see them — the
            // same move ADR 0020 made for AppRow, IconCell and the rail. The
            // forced ring is what puts them in the tree at all here: on Home
            // they exist only in keyboard input mode, and a fixture presses
            // nothing.
            HomeGestureAffordances(GALLERY_GESTURES)
        }
        Spacer(Modifier.height(BodhaSpacing.xl))

        // What is left after the ADR 0025 migration: the nodes a screen still
        // builds itself, because the roster does not express them. A row that is
        // now a bare CardRow or ListRow call is covered by the vocabulary block
        // above, and repeating it here would only inflate the walk's count.
        Text("Actionable components", style = BodhaType.overline, color = colors.inkMuted)
        OnboardingPromiseStep(onContinue = {})
        // The pickers at a fixed height: the flow gives them the screen's
        // leftover; a fixture has none to give.
        OnboardingPickerStep(
            headline = "Pick your essentials",
            support = "Four to eight is plenty.",
            apps = GALLERY_PICKER_APPS,
            onContinue = {},
            onSkip = {},
            initialPicked = listOf("gallery.one"),
            modifier = Modifier.height(300.dp),
        )
        // The capped variant (#138): with the cap met, the unpicked row is unavailable.
        OnboardingPickerStep(
            headline = "Which apps deserve a pause?",
            support = "Three to start.",
            apps = GALLERY_PICKER_APPS,
            cap = 2,
            onContinue = {},
            onSkip = {},
            initialPicked = listOf("gallery.one", "gallery.two"),
            modifier = Modifier.height(300.dp),
        )
        OnboardingIntentionStep(onContinue = {}, onSkip = {})
        OnboardingBecomeHomeStep(onRequestRole = {}, onSkip = {})
        IntentionCard(text = "Today's intention, tinted", muted = false, onEdit = {})
        AppRow(app = GALLERY_APP, iconKey = Unit, iconFor = { null }, onOpen = {}, onLongPress = {})
        AppRow(
            app = GALLERY_APP, iconKey = Unit, iconFor = { null },
            onOpen = {}, onLongPress = {}, lastUsedLine = "Last used 8 minutes ago",
        )
        SheetRow("Sheet row") {}
        LibrarySearchField(query = "", onQueryChange = {})
        SectionOverline("Overline, long-pressable", onLongClick = {})
        LayoutSwitcher(current = LibraryLayout.Alphabetical, onChange = {})
        Row {
            IconCell(app = GALLERY_APP, iconKey = Unit, iconFor = { null }, onOpen = {}, onLongPress = {})
            Spacer(Modifier.width(BodhaSpacing.l))
            AlphabetScrubber(
                index = GALLERY_INDEX,
                onJump = {},
                modifier = Modifier.height(160.dp),
            )
        }
    }
}

/** Fixed fixture content: no clock, no package manager, so the diff is the identity. */
private val GALLERY_APP = HomeAction(id = "gallery.app", label = "Gallery app")

private val GALLERY_ALL_DAY_EVENT = DayEvent(
    eventId = 1, title = "Gallery holiday", allDay = true,
    begin = java.time.LocalDateTime.of(2026, 8, 5, 0, 0),
    end = java.time.LocalDateTime.of(2026, 8, 6, 0, 0),
)

private val GALLERY_TIMED_EVENT = DayEvent(
    eventId = 2, title = "Gallery stand-up", allDay = false,
    begin = java.time.LocalDateTime.of(2026, 8, 5, 9, 30),
    end = java.time.LocalDateTime.of(2026, 8, 5, 10, 0),
)

/** Fixed times, so the fields photograph the same span every run. */
private val GALLERY_WINDOW = ScheduleWindow(startMinute = 21 * 60, endMinute = 23 * 60 + 30)

private fun gallerySession(id: Long, from: Int, to: Int?, intentional: Boolean) = AwarenessSession(
    record = SessionRecord(
        id = id,
        start = java.time.LocalDateTime.of(2026, 8, 5, 9, from),
        end = to?.let { java.time.LocalDateTime.of(2026, 8, 5, 9, it) },
    ),
    intentional = intentional,
)

private val GALLERY_INTENTIONAL_SESSION = gallerySession(1, 41, 53, intentional = true)
private val GALLERY_UNCLASSIFIED_SESSION = gallerySession(2, 12, 14, intentional = false)
private val GALLERY_RUNNING_SESSION = gallerySession(3, 58, null, intentional = false)

private val GALLERY_PICKER_APPS = listOf(
    HomeAction(id = "gallery.one", label = "Atlas"),
    HomeAction(id = "gallery.two", label = "Ledger"),
    HomeAction(id = "gallery.three", label = "Signalbox"),
)

private val GALLERY_INDEX = listOf('A', 'F', 'M', 'S', 'W')
    .mapIndexed { i, letter -> LibraryIndexEntry(letter = letter, firstRow = i) }

/** The five labelled gestures; the unlabelled one draws no node, by design. */
private val GALLERY_GESTURES = HomeGestures(
    swipeDown = GestureAction("Open Search") {},
    swipeUp = GestureAction("Open App Library") {},
    swipeLeft = GestureAction("Open Awareness") {},
    swipeRight = GestureAction("Open Today") {},
    doubleTapEmpty = GestureAction(label = null) {},
    longPressEmpty = GestureAction("Edit layout") {},
)

@Composable
private fun Swatch(color: Color, outlined: Boolean = false) {
    val colors = LocalBodhaColors.current
    Box(
        Modifier
            .padding(end = BodhaSpacing.s)
            .size(48.dp)
            .background(if (outlined) colors.hairline else color)
            .padding(1.dp)
            .background(color)
    )
}
