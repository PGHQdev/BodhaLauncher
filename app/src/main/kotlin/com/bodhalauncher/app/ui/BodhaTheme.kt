package com.bodhalauncher.app.ui

import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.bodhalauncher.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ADR 0010 "Centered Axis" palette: warm paper ground with a designed
 * warm-charcoal dark counterpart (never an inversion), sage accent.
 * Screens consume roles, never raw values (#26).
 */
data class BodhaColors(
    val ground: Color,
    val ink: Color,
    /** Secondary text: date, digest, labels — the machinery. */
    val inkMuted: Color,
    val accent: Color,
    /**
     * A card's own ground, one step off [ground] (ADR 0025). Not a new colour
     * decision — a card cannot be drawn without a fill, and the rule that cards
     * exist is the reference's.
     */
    val surface: Color,
    /**
     * The tinted fill, and it means exactly one thing: *the current or
     * summarising item* (ADR 0025). The other fill is [accent] solid, for the
     * single primary action on a screen. Neither is available for anything else,
     * which is why focus cannot use them.
     */
    val accentTint: Color,
    val hairline: Color,
    /** Genuine errors and destructive actions only — red never means "look here" (#26). */
    val error: Color,
)

private val Light = BodhaColors(
    ground = Color(0xFFF6F1E7),
    ink = Color(0xFF2B261F),
    inkMuted = Color(0xFF7A7263),
    accent = Color(0xFF7D8C6F),
    surface = Color(0xFFFBF8F1),
    accentTint = Color(0x2E7D8C6F),
    hairline = Color(0x332B261F),
    error = Color(0xFF9C4A38),
)

private val Dark = BodhaColors(
    ground = Color(0xFF221F1A),
    ink = Color(0xFFEAE3D6),
    inkMuted = Color(0xFF9A9184),
    accent = Color(0xFF93A284),
    surface = Color(0xFF2A2721),
    accentTint = Color(0x3393A284),
    hairline = Color(0x33EAE3D6),
    error = Color(0xFFC4796A),
)

/**
 * The faces behind the roles (#90): Source Serif 4 and Source Sans 3, both
 * SIL OFL 1.1 — licences ship in `assets/licenses/`. Variable rather than
 * static instances, so weights can be added without new files and the
 * multilingual coverage #26 asks for stays intact.
 */
private val SerifVoice = FontFamily(
    Font(R.font.source_serif_4, FontWeight.Normal),
    Font(R.font.source_serif_4_italic, FontWeight.Normal, FontStyle.Italic),
)

private val SansMachinery = FontFamily(Font(R.font.source_sans_3, FontWeight.Normal))

/**
 * The type scale: eleven roles over nine sizes (ADR 0021), absorbed from what
 * the screens already did rather than designed as a ladder — 15sp was exactly
 * the footer actions, 13sp-with-tracking exactly the overlines.
 *
 * Which face a string takes is decided by **authorship, not rank**: voice is
 * text Bodha wrote and means, machinery is operational text and anything a
 * third party wrote. So an app's own name is machinery even as the largest
 * thing on a sheet, which is why [title] and [voiceTitle] share a size.
 *
 * Italic and letter-spacing are part of a role, never added at a call site.
 * Colour is not: [action] is the accent for Save and muted ink for Delete —
 * same rank, opposite emphasis — so colour tracks emphasis and state while
 * size and face track rank. Sites read colour from [LocalBodhaColors].
 *
 * Nothing outside this file names a family; a face swap touches only here.
 */
object BodhaType {
    // Voice — the serif.
    val voiceClock = TextStyle(fontFamily = SerifVoice, fontSize = 64.sp)
    val voiceTitle = TextStyle(fontFamily = SerifVoice, fontSize = 22.sp)
    /** The daily intention as ADR 0010 draws it: a centered serif italic line. */
    val voiceLine = TextStyle(fontFamily = SerifVoice, fontStyle = FontStyle.Italic, fontSize = 19.sp)
    /** Bodha at more than a line's length; carries its own line height. */
    val voicePassage = TextStyle(fontFamily = SerifVoice, fontSize = 18.sp, lineHeight = 28.sp)
    /** The intention while it is being typed — the same voice, a smaller moment. */
    val voiceInput = TextStyle(fontFamily = SerifVoice, fontStyle = FontStyle.Italic, fontSize = 16.sp)

    // Machinery — the sans.
    val title = TextStyle(fontFamily = SansMachinery, fontSize = 22.sp)
    val body = TextStyle(fontFamily = SansMachinery, fontSize = 16.sp)
    /** Footer actions, and only those: Save, Delete, Clear, Go. */
    val action = TextStyle(fontFamily = SansMachinery, fontSize = 15.sp)
    val label = TextStyle(fontFamily = SansMachinery, fontSize = 14.sp)
    val overline = TextStyle(fontFamily = SansMachinery, fontSize = 13.sp, letterSpacing = 2.sp)
    val caption = TextStyle(fontFamily = SansMachinery, fontSize = 12.sp)
}

/**
 * The accessibility floor's touch-target minimum (ADR 0020): 48dp on both axes,
 * no exceptions and no exception list. Compose applies this for you only inside
 * Material components, so anything hand-rolling its click handling asks here.
 *
 * A minimum rather than a size, so a control keeps drawing at whatever size it
 * draws at and only its touch area grows — the rail's letters do not move.
 *
 * **On a `BasicTextField`, put this last.** A field's click semantics live on its
 * own inner node, below every modifier in the chain, so a `padding` between the
 * two takes that padding straight off the node a reader activates: the target
 * measures the floor minus the padding while the chain still reads as floored.
 * Wrapping a field in a floored container fails for the same reason — the
 * field's node keeps its text height. Elsewhere, where the click sits on the
 * chain's own node, order does not matter.
 */
val TOUCH_TARGET_MIN = 48.dp

fun Modifier.touchTargetFloor(): Modifier =
    defaultMinSize(minWidth = TOUCH_TARGET_MIN, minHeight = TOUCH_TARGET_MIN)

/**
 * Focus is a 2dp ring in the accent on the component's own outline (ADR 0026).
 *
 * It **replaces** the resting 1dp hairline rather than stacking inside it, and
 * [Modifier.border] paints within the bounds, so nothing reflows and no padding
 * is consulted — which is what lets the same treatment sit on a `CardRow` that
 * has padding and a `ListRow` that has none.
 *
 * The colour is the accent read at the call site. A separate `focusRing` token
 * would be a second value a shade off it, and ADR 0010 reserves colour to the
 * identity. The cost, and it is the point: an accent *stroke* now means focus
 * and nothing else may claim one, the way ADR 0025 rule 2 spends the two fills.
 *
 * Touch never draws it — a touch click focuses nothing — so this is a keyboard
 * and accessibility treatment only, and can afford to be this loud.
 */
val FOCUS_RING_WIDTH = 2.dp

/**
 * Draws the ring on [shape] regardless of real focus. Only one node can hold
 * focus at a time, so the design gallery cannot show a focused specimen of all
 * four shapes any other way, and a fixture captured at rest would otherwise put
 * the treatment outside both guards ADR 0025 built the component layer to be
 * inside. Nothing in the product provides it.
 */
val LocalForceFocusRing = staticCompositionLocalOf { false }

@Composable
fun focusRingShown(focused: Boolean): Boolean = focused || LocalForceFocusRing.current

@Composable
fun Modifier.focusRing(shape: Shape): Modifier =
    border(FOCUS_RING_WIDTH, LocalBodhaColors.current.accent, shape)

/** The spacing scale; hairlines are always 1dp. */
object BodhaSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val page = 28.dp
}

/**
 * Motion tokens (#26): nothing exceeds 250ms, and the reduced-motion system
 * setting collapses every duration to zero.
 */
class BodhaMotion(private val reduced: Boolean) {
    val quickMillis: Int get() = if (reduced) 0 else 120
    val standardMillis: Int get() = if (reduced) 0 else 200
}

val LocalBodhaColors = staticCompositionLocalOf { Light }
val LocalBodhaMotion = staticCompositionLocalOf { BodhaMotion(reduced = false) }

@Composable
fun BodhaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) Dark else Light
    val context = LocalContext.current
    val reduced = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) == 0f
    CompositionLocalProvider(
        LocalBodhaColors provides colors,
        LocalBodhaMotion provides BodhaMotion(reduced),
        // Sans is the machinery and therefore the default: every Text that
        // doesn't ask for the serif voice gets it without naming a face.
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SansMachinery),
        content = content,
    )
}
