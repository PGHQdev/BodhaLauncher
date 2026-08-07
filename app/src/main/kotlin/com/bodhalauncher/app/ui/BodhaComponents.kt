package com.bodhalauncher.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The visual vocabulary (ADR 0025), as the components the rules imply.
 *
 * The rules come from `bodhalauncher.png`, which is binding on vocabulary and
 * superseded on content: an element belongs here if it **encodes something a
 * reader decodes**, which is why the reference's artwork is not in this file.
 *
 * They live as shared components rather than per-screen edits because the
 * accessibility walk (ADR 0020) and the keyboard traversal (ADR 0022) both run
 * over the design gallery, so a shared component is covered by both guards the
 * moment it exists. Five screens re-deciding the same five rules locally is how
 * five screens end up with four interpretations.
 *
 * Every actionable component here takes the floor and a name by construction —
 * that is the point of the layer, and what makes a new surface compliant by
 * using it rather than by someone remembering.
 */

/** Card corner. Pills are fully round; the two shapes are the rule, not a scale. */
private val CARD_RADIUS = 14.dp
private val CHIP_RADIUS = 10.dp
private val CHIP_SIZE = 34.dp
private val MARK_SIZE = 32.dp

/**
 * Rule 2, and the whole of it: fill means exactly two things.
 *
 * [Plain] is the default — a card or button that is neither. [Tinted] is *the
 * current or summarising item* and [Solid] is *the single primary action on a
 * screen*, so a screen showing two [Solid]s has a bug rather than a style.
 * Nothing else may claim a fill, which is what stops focus reaching for one.
 */
enum class Emphasis { Plain, Tinted, Solid }

@Composable
private fun fillFor(emphasis: Emphasis) = when (emphasis) {
    Emphasis.Plain -> LocalBodhaColors.current.surface
    Emphasis.Tinted -> LocalBodhaColors.current.accentTint
    Emphasis.Solid -> LocalBodhaColors.current.accent
}

@Composable
private fun inkFor(emphasis: Emphasis) = when (emphasis) {
    // On solid accent the ground reads as the paper the type is knocked out of.
    Emphasis.Solid -> LocalBodhaColors.current.ground
    else -> LocalBodhaColors.current.ink
}

/**
 * The card surface itself: rule 1's block, without any behaviour.
 *
 * [focused] is passed down rather than sensed here because the focusable node is
 * the click chain inside [CardRow], not this box.
 */
@Composable
fun BodhaCard(
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.Plain,
    focused: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalBodhaColors.current
    val shape = RoundedCornerShape(CARD_RADIUS)
    Box(
        modifier = modifier
            .clip(shape)
            .background(fillFor(emphasis))
            .then(
                when {
                    focused -> Modifier.focusRing(shape)
                    emphasis == Emphasis.Plain -> Modifier.border(1.dp, colors.hairline, shape)
                    else -> Modifier
                }
            ),
    ) { content() }
}

/**
 * Rule 5's leading slot for **Bodha's own glyphs**: a chip, so the mark reads as
 * something Bodha drew. A third party's icon goes bare — see [ListRow]'s `leading`
 * — which is ADR 0021's voice-and-machinery split arriving in iconography.
 */
@Composable
fun IconChip(content: @Composable () -> Unit) {
    val colors = LocalBodhaColors.current
    Box(
        modifier = Modifier
            .size(CHIP_SIZE)
            .clip(RoundedCornerShape(CHIP_RADIUS))
            .background(colors.hairline),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Rule 5's other side: **a third party's mark, bare** — no chip, no ground, so an
 * app's own icon never reads as something Bodha drew. The size is decided here
 * rather than at each row, which is the whole reason it is a component.
 *
 * No `contentDescription`: the row it leads is named by its title, and a second
 * name on the icon would have a reader say the app twice (ADR 0020).
 */
@Composable
fun AppMark(icon: ImageBitmap) {
    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(MARK_SIZE))
}

/** Rule 3: a chevron means *this navigates*. Its absence means it acts in place. */
@Composable
fun TrailingChevron() {
    Text("›", style = BodhaType.title, color = LocalBodhaColors.current.inkMuted)
}

/**
 * Rule 1's first idiom: a **card row** — a block acted on once. Home's actions,
 * Today's slots, Settings' rows, AI Assist's suggestions.
 *
 * Not for lists that scroll; those are [ListRow]. The distinction encodes *does
 * this scroll*, which is the only reason it is a rule.
 */
@Composable
fun CardRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    emphasis: Emphasis = Emphasis.Plain,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalBodhaColors.current
    var focused by remember { mutableStateOf(false) }
    val actions = rememberRowActions(onLongClick)
    BodhaCard(
        modifier = modifier.fillMaxWidth(),
        emphasis = emphasis,
        focused = focusRingShown(focused),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
                .onFocusChanged { focused = it.isFocused }
                .actionsKeys(actions, onLongClick)
                .then(
                    if (onLongClick != null) Modifier.combinedClickable(
                        onClick = onClick, onLongClick = onLongClick
                    ) else Modifier.clickable(onClick = onClick)
                )
                .padding(horizontal = BodhaSpacing.m, vertical = BodhaSpacing.s),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = BodhaType.body, color = inkFor(emphasis))
                if (subtitle != null) {
                    Text(subtitle, style = BodhaType.caption, color = colors.inkMuted)
                }
            }
            ActionsSlot(actions, onLongClick, focused)
            trailing?.invoke()
        }
    }
}

/**
 * Rule 1's second idiom: a **hairline row** — one entry in a list that scrolls.
 * Search results, Library apps, Context modes.
 *
 * The rule is the hairline above rather than a card, so a hundred of these read
 * as one list instead of a hundred blocks.
 */
@Composable
fun ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Rule 2's tinted fill on a row that scrolls: the current choice — the selected context mode. */
    tinted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalBodhaColors.current
    var focused by remember { mutableStateOf(false) }
    val actions = rememberRowActions(onLongClick)
    // Square, at the row's own bounds (ADR 0026): a rounded ring would make a
    // scrolling row card-shaped on focus and conflate the two rule-1 idioms.
    val ring = focusRingShown(focused)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (ring) Modifier.focusRing(RectangleShape) else Modifier),
    ) {
        // The strip stays 1dp whether or not it is painted, so focus costs no
        // layout; the ring's top edge is what occupies that pixel.
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(if (ring) Color.Transparent else colors.hairline)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tinted) Modifier.background(colors.accentTint) else Modifier)
                .touchTargetFloor()
                .onFocusChanged { focused = it.isFocused }
                .actionsKeys(actions, onLongClick)
                .then(
                    if (onLongClick != null) Modifier.combinedClickable(
                        onClick = onClick, onLongClick = onLongClick
                    ) else Modifier.clickable(onClick = onClick)
                )
                .padding(vertical = BodhaSpacing.m),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = BodhaType.body, color = colors.ink)
                if (subtitle != null) {
                    Text(subtitle, style = BodhaType.caption, color = colors.inkMuted)
                }
            }
            ActionsSlot(actions, onLongClick, focused)
            trailing?.invoke()
        }
    }
}

/**
 * The multi-select variant of [ListRow]: one entry in a scrolling list that is
 * picked rather than opened — onboarding's essentials and friction pickers.
 *
 * The picked state is **a check glyph in the accent, in the trailing slot**
 * (#137): ADR 0025 has spent both fills and ADR 0026 the accent outline, so a
 * picked row can take none of them — and the trailing slot is free because
 * these rows act in place and draw no chevron. Nothing about the row's fill or
 * outline changes. The toggle semantics are what a screen reader speaks the
 * state through; [enabled] false is a cap reached, spoken as unavailable.
 */
@Composable
fun MultiSelectRow(
    title: String,
    picked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = LocalBodhaColors.current
    var focused by remember { mutableStateOf(false) }
    val ring = focusRingShown(focused)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (ring) Modifier.focusRing(RectangleShape) else Modifier),
    ) {
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(if (ring) Color.Transparent else colors.hairline)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
                .onFocusChanged { focused = it.isFocused }
                .toggleable(value = picked, enabled = enabled, onValueChange = { onToggle() })
                .padding(vertical = BodhaSpacing.m),
        ) {
            leading?.invoke()
            Text(
                text = title,
                style = BodhaType.body,
                color = if (enabled || picked) colors.ink else colors.inkMuted,
                modifier = Modifier.weight(1f),
            )
            if (picked) {
                // The toggle semantics already speak the state; a literal
                // "check mark" on top would say it twice (ADR 0020).
                Text(
                    "✓",
                    style = BodhaType.title,
                    color = colors.accent,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

/**
 * Rule 4: a **pill** is a discrete button, against a card's row or block.
 *
 * [Emphasis.Solid] is the screen's one primary action — Open, Continue writing.
 * A destructive pill stays [Emphasis.Plain] and colours its own label, because
 * red never means "look here" (#26).
 *
 * Width is the label's, not the parent's: a discrete button sized to its text is
 * what lets several sit in a row (the Library's layout switcher). A pill that
 * spans a surface asks for `Modifier.fillMaxWidth()` at the call site.
 */
@Composable
fun BodhaPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.Plain,
    enabled: Boolean = true,
    destructive: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = LocalBodhaColors.current
    val shape = RoundedCornerShape(percent = 50)
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(fillFor(emphasis))
            .then(
                when {
                    focusRingShown(focused) -> Modifier.focusRing(shape)
                    emphasis == Emphasis.Plain -> Modifier.border(1.dp, colors.hairline, shape)
                    else -> Modifier
                }
            )
            .touchTargetFloor()
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = BodhaSpacing.l, vertical = BodhaSpacing.m),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.s),
        ) {
            leading?.invoke()
            Text(
                text = label,
                style = BodhaType.action,
                textAlign = TextAlign.Center,
                color = when {
                    destructive -> colors.error
                    !enabled -> colors.inkMuted
                    else -> inkFor(emphasis)
                },
            )
        }
    }
}

/**
 * Rule 4's other half: a **field** takes the pill shape, so what you type into
 * and what you press read as the same order of thing.
 *
 * **The name belongs on the caller's [field], not here.** A text field's click
 * semantics live on its own inner node, so that node is what a reader activates
 * and what ADR 0020's walk measures; a name on this Row does not reach it and
 * this Row does not merge, so naming both has a reader say the field twice —
 * the same duplicate [AppMark] declines to create. A caller whose content is not
 * a field names whatever it wraps this in, the way Home's Search does with its
 * own `clickable`.
 */
@Composable
fun BodhaField(
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    field: @Composable () -> Unit,
) {
    val colors = LocalBodhaColors.current
    val shape = RoundedCornerShape(percent = 50)
    // hasFocus, not isFocused: the focus target is the caller's text field, a
    // descendant rather than a node in this chain.
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.m),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .then(
                if (focusRingShown(focused)) Modifier.focusRing(shape)
                else Modifier.border(1.dp, colors.hairline, shape)
            )
            .onFocusChanged { focused = it.hasFocus }
            .padding(horizontal = BodhaSpacing.l)
            // Stays last: ADR 0020's caveat in BodhaTheme.kt.
            .touchTargetFloor(),
    ) {
        Box(Modifier.weight(1f)) { field() }
        trailing?.invoke()
    }
}

/**
 * The group label above a set of rows. Carried from the built Library rather
 * than decided — it already existed on both sides of ADR 0025's divergence.
 *
 * [onLongClick] is the Library's group sections: a rename lives on the label
 * because there is nowhere else for it, and an overline is not a row — a tap
 * does nothing. Given one, the overline takes the floor and is named by its own
 * text (ADR 0020), which is why the case belongs here rather than in a second
 * actionable node beside the one the screen already draws.
 *
 * It carries the same actions node and keys as the two rows, because it is the
 * same case: a long-press with no other route (ADR 0022). Enter on the label
 * itself stays inert, matching what a tap does — parity is over outcomes, and
 * the outcome here is the rename, which Right and Menu reach.
 */
@Composable
fun SectionOverline(
    text: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalBodhaColors.current
    if (onLongClick == null) {
        Text(
            text = text,
            style = BodhaType.overline,
            color = colors.inkMuted,
            modifier = modifier.padding(top = BodhaSpacing.l, bottom = BodhaSpacing.s),
        )
        return
    }
    var focused by remember { mutableStateOf(false) }
    val actions = rememberRowActions(onLongClick)
    Row(
        // Top, so the label sits where it sits without the actions node: the
        // slot is 48dp and the overline is one short line.
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .touchTargetFloor()
            // Square, at the label's own bounds — an overline has no box, for
            // the reason a ListRow has none (ADR 0026).
            .then(if (focusRingShown(focused)) Modifier.focusRing(RectangleShape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .actionsKeys(actions, onLongClick)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(top = BodhaSpacing.l, bottom = BodhaSpacing.s),
    ) {
        Text(
            text = text,
            style = BodhaType.overline,
            color = colors.inkMuted,
            modifier = Modifier.weight(1f),
        )
        ActionsSlot(actions, onLongClick, focused)
    }
}
