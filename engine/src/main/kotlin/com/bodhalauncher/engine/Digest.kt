package com.bodhalauncher.engine

/** The four sections, in display order (ADR 0015). There is no Work section. */
enum class DigestSection(val label: String) {
    People("People"),
    TimeSensitive("Time-sensitive"),
    Updates("Updates"),
    Silent("Silent"),
}

/** Android's post-override IMPORTANCE_DEFAULT, the audible threshold (#161). */
const val IMPORTANCE_DEFAULT = 3

/** The categories that read as time-sensitive when they still alert at default importance (#161). */
private val TIME_SENSITIVE_CATEGORIES =
    setOf("alarm", "call", "event", "reminder", "navigation", "transport")

/** Updates' named signal (ADR 0015), even though Updates is also the catch-all. */
private val UPDATE_CATEGORIES = setOf("promo", "social", "status", "recommendation")

/**
 * What the edge reads off one notification and its ranking — signals only,
 * never content. [rankingSaysConversation] is null below API 31, where the
 * ranking cannot answer; min SDK 29 makes that fallback live.
 */
data class NotificationSignals(
    val rankingSaysConversation: Boolean?,
    val hasMessagingStyle: Boolean,
    val category: String?,
    /** Post-override importance, after any channel or user adjustment. */
    val importance: Int,
    val everAudiblyAlerted: Boolean,
)

/** Where one notification landed, and the one signal that put it there. */
data class Placement(val section: DigestSection, val signal: String)

/**
 * A pure, total function: every notification lands in exactly one section
 * (#161). Classification order differs from display order so the function is
 * total — conversation-ness wins over everything, including low importance;
 * time-sensitivity requires still alerting at default importance; silence is
 * low importance or never having audibly alerted; Updates catches the rest.
 */
fun classifyNotification(signals: NotificationSignals): Placement = when {
    signals.rankingSaysConversation == true ->
        Placement(DigestSection.People, "a conversation, by the system's ranking")
    signals.rankingSaysConversation == null && signals.hasMessagingStyle ->
        Placement(DigestSection.People, "a messaging-style notification (pre-API-31 fallback)")
    signals.category in TIME_SENSITIVE_CATEGORIES && signals.importance >= IMPORTANCE_DEFAULT ->
        Placement(DigestSection.TimeSensitive, "category ${signals.category}, still alerting")
    signals.importance < IMPORTANCE_DEFAULT ->
        Placement(DigestSection.Silent, "importance below default")
    !signals.everAudiblyAlerted ->
        Placement(DigestSection.Silent, "never audibly alerted")
    signals.category in UPDATE_CATEGORIES ->
        Placement(DigestSection.Updates, "category ${signals.category}")
    else ->
        Placement(DigestSection.Updates, "no named signal — the catch-all")
}

/**
 * The digest slot's state (#161, ADR 0017): four named degraded states rather
 * than a blank, and counts that keep describing the day when the listener drops
 * or the grant is revoked mid-session — the slot says why.
 */
sealed interface DigestSlot {
    /** Never granted. [offersTurnOn] follows the same decline rule as the day slot. */
    data class Ungranted(val offersTurnOn: Boolean) : DigestSlot

    /** Granted, connected, and nothing has arrived under this day key. */
    data object Empty : DigestSlot

    /** One count per non-empty section, in display order; zero sections are absent. */
    data class Counts(val counts: Map<DigestSection, Int>) : DigestSlot

    /** Granted but the listener is not connected; the day's counts stay. */
    data class Disconnected(val counts: Map<DigestSection, Int>) : DigestSlot

    /** Access revoked mid-session: the day's counts stay, and the slot says why. */
    data class Revoked(val counts: Map<DigestSection, Int>) : DigestSlot
}

/**
 * Resolves the digest slot (#161). [sectionCounts] is the day key's stored
 * tally; a revocation is told apart from never-granted by whether anything was
 * counted under this key while the grant held.
 */
fun resolveDigestSlot(
    granted: Boolean,
    educationShown: Boolean,
    listenerConnected: Boolean,
    sectionCounts: Map<DigestSection, Int>,
): DigestSlot {
    val counts = DigestSection.entries
        .mapNotNull { section -> sectionCounts[section]?.takeIf { it > 0 }?.let { section to it } }
        .toMap()
    return when {
        !granted && counts.isNotEmpty() -> DigestSlot.Revoked(counts)
        !granted -> DigestSlot.Ungranted(offersTurnOn = !educationShown)
        !listenerConnected -> DigestSlot.Disconnected(counts)
        counts.isEmpty() -> DigestSlot.Empty
        else -> DigestSlot.Counts(counts)
    }
}

/** The digest card's one line: "3 People · 1 Time-sensitive", in display order. */
fun digestLine(counts: Map<DigestSection, Int>): String =
    DigestSection.entries
        .mapNotNull { section -> counts[section]?.let { "$it ${section.label}" } }
        .joinToString(" · ")
