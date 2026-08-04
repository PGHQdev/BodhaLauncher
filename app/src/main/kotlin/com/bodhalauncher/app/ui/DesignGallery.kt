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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

        Text("Voice title", style = BodhaType.voiceTitle, color = colors.ink)
        Text("Body — operational text and data", style = BodhaType.body, color = colors.ink)
        Text("Label — controls and rows", style = BodhaType.label, color = colors.ink)
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
    }
}

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
