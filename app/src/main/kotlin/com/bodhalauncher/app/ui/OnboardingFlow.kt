package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.OnboardingStep

/**
 * The onboarding flow (#135, ADR 0018) — a flow, not a surface: it composes
 * instead of the host, so no swipe, Search resolution or Home control can
 * reach it, and ADR 0011's radial model does not apply while it runs.
 *
 * Back on the first step leaves the app. With BodhaHost not composed there is
 * no enabled back callback, so the system back does that by default; Escape is
 * bound here to the same [onExit] because the root escape binding needs an
 * enabled callback to press and there is none.
 */
@Composable
fun OnboardingFlow(
    step: OnboardingStep,
    onAdvance: (OnboardingStep) -> Unit,
    onExit: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .escapeDismisses(onExit)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        when (step) {
            OnboardingStep.Promise -> OnboardingPromiseStep(
                onContinue = { onAdvance(step) },
                // Focus on arrival is what gives the step an Escape route (ADR 0022).
                pillModifier = Modifier.focusOnOpen(),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Step one: the promise (ADR 0018). The headline is voice; the single sentence
 * about permissions and privacy is the honest residue of the deleted
 * permissions step — no list, no grants, no system screen.
 */
@Composable
internal fun OnboardingPromiseStep(onContinue: () -> Unit, pillModifier: Modifier = Modifier) {
    val colors = LocalBodhaColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "A phone that helps you remember why you picked it up",
            color = colors.ink,
            style = BodhaType.voicePassage,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Bodha asks for nothing until a feature needs it, and nothing leaves the phone.",
            color = colors.inkMuted,
            style = BodhaType.body,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        BodhaPill(
            label = "Continue",
            onClick = onContinue,
            emphasis = Emphasis.Solid,
            modifier = pillModifier.fillMaxWidth(),
        )
    }
}
