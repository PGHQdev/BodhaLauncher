package com.bodhalauncher.engine

/**
 * The optional capabilities Bodha may ask for (#18). Core capabilities — the
 * home role and launchable-app queries — are never asked for and aren't modeled.
 * Accessibility Service and microphone are deliberately absent at v1.
 */
enum class Capability {
    UsageAccess,
    NotificationAccess,
    Contacts,
    Calendar,
    CoarseLocation,
    Documents,
}

/** How the education flow was reached; there are no other ways in (#18: no timed or launch-based re-asks). */
enum class EducationEntry {
    /** The user touched a feature that needs the capability. */
    FeatureTouch,

    /** The user explicitly asked — a "turn this on" affordance or Settings. */
    UserRequest,
}

/**
 * The Bodha explanation screen, rendered before any system dialog. The edge maps
 * [capability] to the matching Android system screen when the user continues.
 */
data class EducationScreen(
    val capability: Capability,
    /** Exactly what data is accessed. */
    val dataAccessed: String,
    /** Where processing happens — always on-device. */
    val processing: String,
    /** What stays unavailable without the grant. */
    val withoutIt: String,
    /** The feature asking. */
    val feature: String,
)

sealed interface CapabilityResolution {
    /** Granted: the dependent features run. */
    data object Live : CapabilityResolution

    /** Show the explanation screen. */
    data class Educate(val screen: EducationScreen) : CapabilityResolution

    /** Not granted and already asked once: show the designed degraded state, no prompt. */
    data object Degraded : CapabilityResolution
}

private const val ON_DEVICE = "Processed on this phone. Nothing leaves it."

private val screens = mapOf(
    Capability.UsageAccess to EducationScreen(
        capability = Capability.UsageAccess,
        dataAccessed = "Which apps you open and when, from Android's usage statistics.",
        processing = ON_DEVICE,
        withoutIt = "Screen-time context, recently-used ordering, Awareness and Open Check's context lines stay off.",
        feature = "Awareness and Open Check",
    ),
    Capability.NotificationAccess to EducationScreen(
        capability = Capability.NotificationAccess,
        dataAccessed = "Your notifications — which app, who, and when.",
        processing = ON_DEVICE,
        withoutIt = "The notifications inbox and its digest stay off.",
        feature = "Notifications inbox",
    ),
    Capability.Contacts to EducationScreen(
        capability = Capability.Contacts,
        dataAccessed = "Contact names and the ways to reach them.",
        processing = ON_DEVICE,
        withoutIt = "Contact search and direct call or message actions stay off.",
        feature = "Search",
    ),
    Capability.Calendar to EducationScreen(
        capability = Capability.Calendar,
        dataAccessed = "Your upcoming events' times and titles.",
        processing = ON_DEVICE,
        withoutIt = "Next-event surfaces stay off.",
        feature = "Today",
    ),
    Capability.CoarseLocation to EducationScreen(
        capability = Capability.CoarseLocation,
        dataAccessed = "Approximate location, used to pick a weather report.",
        processing = ON_DEVICE,
        withoutIt = "Weather uses a city you choose by hand instead.",
        feature = "Weather",
    ),
    Capability.Documents to EducationScreen(
        capability = Capability.Documents,
        dataAccessed = "Documents you explicitly pick, and nothing else.",
        processing = ON_DEVICE,
        withoutIt = "Document resume stays off.",
        feature = "Resume",
    ),
)

/**
 * Resolves what a capability touchpoint shows (#18). A grant is live regardless
 * of history. Ungranted: the education screen appears once per capability on a
 * feature touch and always on an explicit user request; any other touch after
 * that first showing degrades quietly — a past "no" is an answer.
 */
fun resolveCapability(
    capability: Capability,
    granted: Boolean,
    educationShown: Boolean,
    entry: EducationEntry,
): CapabilityResolution = when {
    granted -> CapabilityResolution.Live
    entry == EducationEntry.UserRequest -> CapabilityResolution.Educate(educationScreen(capability))
    !educationShown -> CapabilityResolution.Educate(educationScreen(capability))
    else -> CapabilityResolution.Degraded
}

/** The explanation screen for a capability, also usable as its fact sheet (#24's permission rows). */
fun educationScreen(capability: Capability): EducationScreen = screens.getValue(capability)
