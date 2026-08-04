package com.bodhalauncher.app.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
    val hairline: Color,
    /** Genuine errors and destructive actions only — red never means "look here" (#26). */
    val error: Color,
)

private val Light = BodhaColors(
    ground = Color(0xFFF6F1E7),
    ink = Color(0xFF2B261F),
    inkMuted = Color(0xFF7A7263),
    accent = Color(0xFF7D8C6F),
    hairline = Color(0x332B261F),
    error = Color(0xFF9C4A38),
)

private val Dark = BodhaColors(
    ground = Color(0xFF221F1A),
    ink = Color(0xFFEAE3D6),
    inkMuted = Color(0xFF9A9184),
    accent = Color(0xFF93A284),
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
 * The two families, for screens that still set a face directly rather than
 * taking a role. A transitional seam: #109 migrates those call sites onto the
 * roles, after which nothing outside this file names a family.
 */
object BodhaFaces {
    val serif = SerifVoice
    val sans = SansMachinery
}

/**
 * Type roles (ADR 0010): serif is the voice — clock, intention, expressive
 * moments — and sans is the machinery. Screens consume roles, never faces, so
 * a future face swap touches only this object.
 */
object BodhaType {
    val voiceClock = TextStyle(fontFamily = SerifVoice, fontSize = 56.sp)
    val voiceTitle = TextStyle(fontFamily = SerifVoice, fontSize = 22.sp)
    val voiceLine = TextStyle(fontFamily = SerifVoice, fontStyle = FontStyle.Italic, fontSize = 18.sp)
    val body = TextStyle(fontFamily = SansMachinery, fontSize = 16.sp)
    val label = TextStyle(fontFamily = SansMachinery, fontSize = 14.sp)
    val caption = TextStyle(fontFamily = SansMachinery, fontSize = 12.sp)
}

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
