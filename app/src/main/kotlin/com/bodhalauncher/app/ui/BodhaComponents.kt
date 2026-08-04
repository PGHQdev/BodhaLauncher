package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** The card surface itself: rule 1's block, without any behaviour. */
@Composable
fun BodhaCard(
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.Plain,
    content: @Composable () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(fillFor(emphasis))
            .then(
                if (emphasis == Emphasis.Plain) Modifier.border(
                    1.dp, colors.hairline, RoundedCornerShape(CARD_RADIUS)
                ) else Modifier
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
    BodhaCard(modifier = modifier.fillMaxWidth(), emphasis = emphasis) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
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
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalBodhaColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
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
            trailing?.invoke()
        }
    }
}

/**
 * Rule 4: a **pill** is a discrete button, against a card's row or block.
 *
 * [Emphasis.Solid] is the screen's one primary action — Open, Continue writing.
 * A destructive pill stays [Emphasis.Plain] and colours its own label, because
 * red never means "look here" (#26).
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
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fillFor(emphasis))
            .then(
                if (emphasis == Emphasis.Plain) Modifier.border(1.dp, colors.hairline, shape)
                else Modifier
            )
            .touchTargetFloor()
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
 * [name] is required rather than optional. Compose gives a text field click
 * semantics, so an unnamed one is an actionable node reading as an edit box for
 * nothing in particular (ADR 0020).
 */
@Composable
fun BodhaField(
    name: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    field: @Composable () -> Unit,
) {
    val colors = LocalBodhaColors.current
    val shape = RoundedCornerShape(percent = 50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.m),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .semantics { contentDescription = name }
            .padding(horizontal = BodhaSpacing.l)
            .touchTargetFloor(),
    ) {
        Box(Modifier.weight(1f)) { field() }
        trailing?.invoke()
    }
}

/**
 * The group label above a set of rows. Carried from the built Library rather
 * than decided — it already existed on both sides of ADR 0025's divergence.
 */
@Composable
fun SectionOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = BodhaType.overline,
        color = LocalBodhaColors.current.inkMuted,
        modifier = modifier.padding(top = BodhaSpacing.l, bottom = BodhaSpacing.s),
    )
}
