package com.bodhalauncher.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * ADR 0010 "Centered Axis" palette: warm paper ground with a designed
 * warm-charcoal dark counterpart (never an inversion), sage accent.
 */
data class BodhaColors(
    val ground: Color,
    val ink: Color,
    /** Secondary text: date, digest, labels — the machinery. */
    val inkMuted: Color,
    val accent: Color,
    val hairline: Color,
)

private val Light = BodhaColors(
    ground = Color(0xFFF6F1E7),
    ink = Color(0xFF2B261F),
    inkMuted = Color(0xFF7A7263),
    accent = Color(0xFF7D8C6F),
    hairline = Color(0x332B261F),
)

private val Dark = BodhaColors(
    ground = Color(0xFF221F1A),
    ink = Color(0xFFEAE3D6),
    inkMuted = Color(0xFF9A9184),
    accent = Color(0xFF93A284),
    hairline = Color(0x33EAE3D6),
)

val LocalBodhaColors = staticCompositionLocalOf { Light }

@Composable
fun BodhaTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Dark else Light
    CompositionLocalProvider(LocalBodhaColors provides colors, content = content)
}
