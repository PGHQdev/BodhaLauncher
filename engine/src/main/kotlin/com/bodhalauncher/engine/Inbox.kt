package com.bodhalauncher.engine

/**
 * One live notification, as much as the inbox needs to place and order it
 * (#162, ADR 0015). Rows describe now: this type mirrors the shade and is held
 * in memory only — the content the row displays stays on the app side, keyed by
 * [key], and nothing here is ever written down.
 */
data class InboxRow(
    val key: String,
    val appPackage: String,
    val section: DigestSection,
    val postedAtMillis: Long,
)

/** One of the four sections with something in it, newest row first. */
data class InboxSectionRows(val section: DigestSection, val rows: List<InboxRow>)

/**
 * The inbox's state (#162): three named absences rather than a blank list, or
 * the live rows grouped under their sections. After a reboot the shade re-read
 * arrives through the same rows, so an empty list is [Empty] — nothing waiting —
 * and never an error.
 */
sealed interface InboxState {
    /** Access is off — never granted, or revoked mid-session. The digest names which. */
    data object AccessOff : InboxState

    /** Granted but the system does not currently hold the listener bound. */
    data object Disconnected : InboxState

    /** Connected, and nothing is waiting. */
    data object Empty : InboxState

    /** The live rows, grouped in display order; a section with nothing is absent. */
    data class Sections(val sections: List<InboxSectionRows>) : InboxState
}

/**
 * Resolves the inbox from the live shade (#162). A muted source's rows are
 * filtered here rather than at the edge, so unmuting shows what is already
 * waiting without anything having to re-arrive (#164).
 */
fun resolveInbox(
    granted: Boolean,
    listenerConnected: Boolean,
    rows: List<InboxRow>,
    muted: Set<String> = emptySet(),
): InboxState {
    if (!granted) return InboxState.AccessOff
    if (!listenerConnected) return InboxState.Disconnected
    val visible = rows.filterNot { it.appPackage in muted }
    if (visible.isEmpty()) return InboxState.Empty
    return InboxState.Sections(
        DigestSection.entries.mapNotNull { section ->
            visible.filter { it.section == section }
                .sortedByDescending { it.postedAtMillis }
                .takeIf { it.isNotEmpty() }
                ?.let { InboxSectionRows(section, it) }
        }
    )
}

/** One entry in the snooze duration sheet (#163). */
data class SnoozeChoice(val label: String, val durationMillis: Long)

/** Three fixed durations, no custom picker — decided in #163, cheap to change. */
val SNOOZE_CHOICES = listOf(
    SnoozeChoice("15 minutes", 15L * 60_000),
    SnoozeChoice("1 hour", 60L * 60_000),
    SnoozeChoice("4 hours", 240L * 60_000),
)
