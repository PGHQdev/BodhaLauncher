package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The running Focus session (#166, ADR 0012): the activity label, the time
 * remaining derived at render, the allowed apps, and End — no Pause anywhere.
 * The reference's screen 7 draws this; its "Continue writing" (a linked task)
 * and "Pause focus" rows fell with ADR 0012, and the ADRs win on content.
 *
 * [gestures] is Home's fan-out carried over (#167): the same shared host, so
 * every surface stays a swipe away and each swipe is a named action. Null in
 * the gallery, whose fixture covers the affordances once, on Home's specimens.
 */
@Composable
fun FocusScreen(
    label: String,
    remaining: String,
    allowedAppLabels: List<String>,
    onEnd: () -> Unit,
    gestures: HomeGestures?,
) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .then(if (gestures != null) Modifier.homeGestures(gestures, surfaceName = "Focus") else Modifier)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        gestures?.let { HomeGestureAffordances(it) }
        Spacer(Modifier.weight(1f))
        FocusScreenContent(
            label = label,
            remaining = remaining,
            allowedAppLabels = allowedAppLabels,
            onEnd = onEnd,
            endModifier = Modifier.focusOnOpen(),
        )
        Spacer(Modifier.weight(1f))
    }
}

/** The surface's face, separate so the screenshot gate can photograph it (#26). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocusScreenContent(
    label: String,
    remaining: String,
    allowedAppLabels: List<String>,
    onEnd: () -> Unit,
    endModifier: Modifier = Modifier,
) {
    val colors = LocalBodhaColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // The activity label is the user's word — the sans (ADR 0021).
        Text(text = label, color = colors.ink, style = BodhaType.title)
        Spacer(Modifier.height(BodhaSpacing.m))
        // The time is Bodha's line — the serif; derived from the end instant,
        // so a killed process changes nothing about it.
        Text(text = remaining, color = colors.inkMuted, style = BodhaType.voiceLine)
        Spacer(Modifier.height(BodhaSpacing.xl))
        if (allowedAppLabels.isNotEmpty()) {
            Text(text = "ALLOWED APPS", color = colors.inkMuted, style = BodhaType.overline)
            Spacer(Modifier.height(BodhaSpacing.s))
            // As the reference draws it, unpromoted to a rule (ADR 0025): the
            // installed apps' names in the sans; an uninstalled one just isn't
            // here. A flow, because a long list or 200% type must wrap, not clip.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.m, Alignment.CenterHorizontally),
            ) {
                allowedAppLabels.forEach { app ->
                    Text(text = app, color = colors.ink, style = BodhaType.caption)
                }
            }
            Spacer(Modifier.height(BodhaSpacing.l))
        }
        // End is a choice, not an alarm — the reference colours its own label (rule 4).
        BodhaPill(
            label = "End focus",
            onClick = onEnd,
            destructive = true,
            modifier = endModifier,
        )
    }
}
