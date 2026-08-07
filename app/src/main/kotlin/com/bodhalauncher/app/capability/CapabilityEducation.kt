package com.bodhalauncher.app.capability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.app.ui.EducationSheet
import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.CapabilityResolution
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.EducationScreen
import com.bodhalauncher.engine.EventType
import com.bodhalauncher.engine.resolveCapability

/**
 * The one way in to a capability's education flow (#157). Every touchpoint —
 * Open Check's context note, the Library's layout note and threshold rule, and
 * Today, Settings and Awareness as they land — calls [ask] and nothing else; the
 * sheet, the system screen, the denial memory and the two events live here.
 *
 * The rule itself stays in the engine's `resolveCapability`; what this owns is
 * the Android edge around it — preferences, lifecycle, intents, composition.
 */
@Stable
class CapabilityEducation(
    private val edge: CapabilityEdge,
    private val store: EducationStateStore,
    private val events: EventLogger,
    private val sheets: SheetSlot,
) {

    /**
     * The education sheet goes through the app's one sheet slot like every other
     * (ADR 0011), so this reads the slot rather than holding a second answer to
     * what is on screen. Opening a check's explanation therefore replaces the
     * check: the launch stays gated and the user comes back from system settings
     * to no sheet rather than a stale one (#133).
     */
    val showing: EducationScreen?
        get() = sheet?.screen

    /**
     * This flow's own sheet, and only while it is still the one open. Every
     * answer below goes through it, so an answer arriving from a sheet that has
     * already been replaced acts on nothing rather than on its successor.
     */
    private val sheet: Sheet.Education?
        get() = sheets.showing()

    /**
     * Bumped on every resume. Returning from system settings is the only moment
     * a grant becomes observable (#18), so anything read from the grant — the
     * Open Check context lines, the Library's recency order — keys on this.
     */
    var resumeTick by mutableIntStateOf(0)
        private set

    fun onResume() {
        resumeTick++
    }

    /** Read on demand and never cached across resumes (ADR 0009). */
    @Composable
    fun granted(capability: Capability): Boolean =
        remember(capability, resumeTick) { edge.granted(capability) }

    /**
     * A touchpoint asking for [capability]. Granted or already-answered touches
     * pass through silently; the sheet counts as delivered the moment it opens,
     * so a first feature touch educates once and never again.
     */
    fun ask(capability: Capability, entry: EducationEntry) {
        val resolution = resolveCapability(
            capability = capability,
            granted = edge.granted(capability),
            educationShown = store.shown(capability),
            entry = entry,
        )
        if (resolution is CapabilityResolution.Educate) {
            sheets.open(Sheet.Education(resolution.screen))
            store.markShown(capability)
        }
    }

    /** The grant isn't known until observed on return; only the skip is terminal here. */
    fun onContinue() {
        val open = sheet ?: return
        edge.openSystemScreen(open.screen.capability)
        sheets.close(open)
    }

    fun onSkip() {
        val open = sheet ?: return
        events.log(EventType.PermissionSkipped)
        sheets.close(open)
    }

    /**
     * The surface that asked has gone — the system Home button landing on root
     * (#132). Nothing was answered, so nothing is logged and the denial memory
     * is untouched; the next touch resolves the same way it would have.
     */
    fun close() {
        sheet?.let(sheets::close)
    }

    /** The education-then-grant outcome, recorded once per capability (#25). */
    fun logGrantsObserved() {
        for (capability in Capability.entries) {
            if (store.shown(capability) &&
                !store.grantLogged(capability) &&
                edge.granted(capability)
            ) {
                events.log(EventType.PermissionEnabled)
                store.markGrantLogged(capability)
            }
        }
    }
}

/**
 * The handle, plus the two things that must run for the whole app rather than
 * per surface: the resume observer and the grant log. Emits nothing — the sheet
 * is [CapabilityEducationHost], so the host decides where it sits.
 */
@Composable
fun rememberCapabilityEducation(events: EventLogger, sheets: SheetSlot): CapabilityEducation {
    val context = LocalContext.current
    val education = remember {
        CapabilityEducation(CapabilityEdge(context), EducationStateStore(context), events, sheets)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) education.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(education.resumeTick) { education.logGrantsObserved() }
    return education
}

/** The one education sheet, for every touchpoint that ever calls [CapabilityEducation.ask]. */
@Composable
fun CapabilityEducationHost(education: CapabilityEducation) {
    education.showing?.let { screen ->
        EducationSheet(
            screen = screen,
            onContinue = education::onContinue,
            onDismiss = education::onSkip,
        )
    }
}
