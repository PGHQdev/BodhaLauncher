package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.awarenessTodayLine

/**
 * Awareness's Today view (#171): the count as one line and nothing else — the
 * spine ships thin. With nothing actionable on it yet, the whole surface is the
 * tappable-to-leave node the placeholder and the inbox's named absence already
 * use, which is also what gives it focus on arrival and so a back key (ADR 0022).
 */
@Composable
fun AwarenessScreen(
    /** Null while the store is still being read — nothing renders, never a stand-in 0. */
    today: AwarenessToday?,
    onBack: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    val line = today?.let(::awarenessTodayLine)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .semantics {
                contentDescription = listOfNotNull("Awareness.", line?.plus("."), "Tap to go back.")
                    .joinToString(" ")
            }
            .focusOnOpen()
            .clickable(onClick = onBack),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Awareness", color = colors.ink, style = BodhaType.title)
        line?.let {
            Spacer(Modifier.height(BodhaSpacing.m))
            // A count is data, so it speaks in the operational voice (ADR 0021)
            // with one ink and no direction (ADR 0013).
            Text(it, color = colors.inkMuted, style = BodhaType.body)
        }
    }
}
