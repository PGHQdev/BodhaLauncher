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
        BodhaField(name = "Gallery field") {
            Text("Field — the pill shape, typed into", style = BodhaType.body, color = colors.inkMuted)
        }
        ListRow(
            title = "List row — one entry in a list that scrolls",
            subtitle = "Search results, Library apps, Context modes",
            onClick = {},
            trailing = { TrailingChevron() },
        )
        Spacer(Modifier.height(BodhaSpacing.xl))

        Text("Actionable components", style = BodhaType.overline, color = colors.inkMuted)
        PinRow(action = GALLERY_APP, onAction = {}, onLongPress = {})
        AddPinRow(onAdd = {})
        AppRow(app = GALLERY_APP, onOpen = {}, onLongPress = {})
        AppRow(app = GALLERY_APP, onOpen = {}, onLongPress = {}, lastUsedLine = "Last used 8 minutes ago")
        SheetRow("Sheet row") {}
        LibrarySearchField(query = "", onQueryChange = {})
        SectionHeader(title = "Section header", onLongPress = {})
        HiddenHeader(count = 3, expanded = false, onToggle = {})
        HiddenSearchableRow(enabled = true, onChange = {})
        NewGroupRow(onTap = {})
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
