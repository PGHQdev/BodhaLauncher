package com.bodhalauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.OnboardingStep

/**
 * The onboarding flow (#135, ADR 0018) — a flow, not a surface: it composes
 * instead of the host, so no swipe, Search resolution or Home control can
 * reach it, and ADR 0011's radial model does not apply while it runs.
 *
 * Back and Escape step to the previous step; on the first step both leave the
 * app ([onBack] decides which). Each step commits to its real store on advance
 * — no draft layer — and a skip is an advance that writes nothing.
 */
@Composable
fun OnboardingFlow(
    step: OnboardingStep,
    /** The launchable apps, already alphabetical — both pickers list exactly these. */
    apps: List<HomeAction>,
    /** What the pickers already committed, so a revisited step shows its own writes. */
    pinnedIds: List<String>,
    ruledIds: Set<String>,
    onBack: () -> Unit,
    /** The skip path: records the step as passed and writes nothing. */
    onSkip: (OnboardingStep) -> Unit,
    onContinuePromise: () -> Unit,
    onEssentials: (List<String>) -> Unit,
    onFriction: (List<String>) -> Unit,
    onIntention: (String) -> Unit,
    onRequestHomeRole: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .escapeDismisses(onBack)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            OnboardingStep.Promise -> {
                Spacer(Modifier.weight(1f))
                OnboardingPromiseStep(
                    onContinue = onContinuePromise,
                    // Focus on arrival is what gives the step an Escape route (ADR 0022).
                    pillModifier = Modifier.focusOnOpen(),
                )
                Spacer(Modifier.weight(1f))
            }
            OnboardingStep.Essentials -> OnboardingPickerStep(
                headline = "Pick your essentials",
                support = "The apps that matter, on Home from the start. Four to eight is plenty.",
                apps = apps,
                initialPicked = pinnedIds,
                onContinue = onEssentials,
                onSkip = { onSkip(step) },
                modifier = Modifier.weight(1f),
                pillModifier = Modifier.focusOnOpen(),
            )
            OnboardingStep.Friction -> OnboardingPickerStep(
                headline = "Which apps deserve a pause?",
                support = "Each pick gets a moment's check before it opens. Three to start, change any of it later.",
                apps = apps,
                cap = 3,
                initialPicked = ruledIds.toList(),
                onContinue = onFriction,
                onSkip = { onSkip(step) },
                modifier = Modifier.weight(1f),
                pillModifier = Modifier.focusOnOpen(),
            )
            OnboardingStep.FirstIntention -> {
                Spacer(Modifier.weight(1f))
                OnboardingIntentionStep(
                    onContinue = onIntention,
                    onSkip = { onSkip(step) },
                    // The shipped arrival-focus modifier, IME suppressed (#139, PR #131).
                    fieldModifier = Modifier.focusOnOpen(),
                )
                Spacer(Modifier.weight(1f))
            }
            OnboardingStep.BecomeHome -> {
                Spacer(Modifier.weight(1f))
                OnboardingBecomeHomeStep(
                    onRequestRole = onRequestHomeRole,
                    onSkip = { onSkip(step) },
                    pillModifier = Modifier.focusOnOpen(),
                )
                Spacer(Modifier.weight(1f))
            }
        }
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

/**
 * Steps two and three share this picker (#137, #138): essentials is the uncapped
 * call, friction the capped one — the second caller that earned the component.
 *
 * The list is alphabetical because there is nothing to rank by yet, and nothing
 * gates: Continue is enabled with zero picks and with twenty. At the [cap], a
 * further pick is simply not offerable — the row disables rather than any limit
 * being enforced in copy. Picks keep the order they were made in, which is the
 * order they land as pins.
 */
@Composable
internal fun OnboardingPickerStep(
    headline: String,
    support: String,
    apps: List<HomeAction>,
    onContinue: (List<String>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    cap: Int? = null,
    initialPicked: List<String> = emptyList(),
    pillModifier: Modifier = Modifier,
) {
    val colors = LocalBodhaColors.current
    val picked = remember { initialPicked.toMutableStateList() }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = headline,
            color = colors.ink,
            style = BodhaType.voicePassage,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = support,
            color = colors.inkMuted,
            style = BodhaType.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(apps, key = { it.id }) { app ->
                val isPicked = app.id in picked
                MultiSelectRow(
                    title = app.label,
                    picked = isPicked,
                    enabled = isPicked || cap == null || picked.size < cap,
                    onToggle = { if (isPicked) picked.remove(app.id) else picked.add(app.id) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            BodhaPill(label = "Skip", onClick = onSkip)
            Spacer(Modifier.weight(1f))
            BodhaPill(
                label = "Continue",
                onClick = { onContinue(picked.toList()) },
                emphasis = Emphasis.Solid,
                modifier = pillModifier,
            )
        }
    }
}

/**
 * Step four: the first intention (#139). The question is voice and the typed
 * text is the user's own (ADR 0021); the field is the vocabulary's field
 * component and carries a name of its own rather than relying on its contents.
 * Continue writes it under the 4am day key; Skip writes nothing.
 */
@Composable
internal fun OnboardingIntentionStep(
    onContinue: (String) -> Unit,
    onSkip: () -> Unit,
    fieldModifier: Modifier = Modifier,
) {
    val colors = LocalBodhaColors.current
    var text by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "What matters today?",
            color = colors.ink,
            style = BodhaType.voicePassage,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        BodhaField {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = BodhaType.voiceLine.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                modifier = fieldModifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Today's intention" }
                    // Stays last: ADR 0020's caveat in BodhaTheme.kt.
                    .touchTargetFloor(),
            )
        }
        Spacer(Modifier.height(48.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            BodhaPill(label = "Skip", onClick = onSkip)
            Spacer(Modifier.weight(1f))
            BodhaPill(
                label = "Continue",
                onClick = { onContinue(text.trim()) },
                emphasis = Emphasis.Solid,
                enabled = text.isNotBlank(),
            )
        }
    }
}

/**
 * Step five: become home (#136, ADR 0018). One tap opens the platform's role
 * request; granted or declined the flow completes, and a skip completes it
 * without the dialog, treated exactly as a decline. There is no retry and no
 * route back anywhere in the app — the future Settings row is the only one.
 */
@Composable
internal fun OnboardingBecomeHomeStep(
    onRequestRole: () -> Unit,
    onSkip: () -> Unit,
    pillModifier: Modifier = Modifier,
) {
    val colors = LocalBodhaColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Meet Bodha at unlock",
            color = colors.ink,
            style = BodhaType.voicePassage,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "As your home screen, Bodha is the first thing you see. Either way, everything you set up stays.",
            color = colors.inkMuted,
            style = BodhaType.body,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        BodhaPill(
            label = "Set as home",
            onClick = onRequestRole,
            emphasis = Emphasis.Solid,
            modifier = pillModifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        BodhaPill(label = "Skip", onClick = onSkip, modifier = Modifier.fillMaxWidth())
    }
}
