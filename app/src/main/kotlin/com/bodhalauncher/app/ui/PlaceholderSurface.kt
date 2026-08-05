package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Stands in for a surface that hasn't shipped; tap anywhere (or back) returns Home.
 *
 * It takes focus on arrival for the reason a real surface's first field does
 * (ADR 0022) — and because Escape reaches the root binding along the focus
 * chain, so a surface where nothing holds focus is a surface with no back key.
 */
@Composable
fun PlaceholderSurface(title: String, onBack: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .semantics { contentDescription = "$title — coming soon. Tap to go back." }
            .focusOnOpen()
            .clickable(onClick = onBack),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        // A surface name is a label, not a spoken line (ADR 0021).
        Text(text = title, color = colors.ink, style = BodhaType.title)
        Spacer(Modifier.height(12.dp))
        Text(text = "Coming soon", color = colors.inkMuted, style = BodhaType.label)
    }
}
