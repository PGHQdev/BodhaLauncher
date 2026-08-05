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
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.LibraryIndexEntry
import com.bodhalauncher.engine.LibraryLayout

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
            // The two rows that carry per-item actions, so the hint and the
            // actions node are inside both guards rather than only on a screen.
            CardRow(title = "Pin row, focused", onClick = {}, onLongClick = {})
            ListRow(title = "App row, focused", onClick = {}, onLongClick = {})
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
