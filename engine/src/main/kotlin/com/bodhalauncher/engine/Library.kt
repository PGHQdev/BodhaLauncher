package com.bodhalauncher.engine

/** The library's view modes; [CompactIcons] renders the same rows denser, with icons. */
enum class LibraryLayout {
    Alphabetical,
    CompactIcons,
    Categories,
    Recent,
}

/** One titled group of the Categories layout. */
data class LibrarySection(val title: String, val rows: List<HomeAction>)

/**
 * Everything the App Library may draw from. Grows as the library's features ship
 * (recents #65, groups #67); absent inputs mean absent elements.
 */
data class LibraryInputs(
    val apps: List<HomeAction> = emptyList(),
    val layout: LibraryLayout = LibraryLayout.Alphabetical,
    /** App id to category title; apps without one group under "Other". */
    val categories: Map<String, String> = emptyMap(),
    /** Search text; blank means no filtering. Matches anywhere in the label, ignoring case. */
    val query: String = "",
    /** App ids the user has hidden; they collect in [LibraryState.hiddenRows]. */
    val hidden: Set<String> = emptySet(),
    /** Whether hidden apps may match a search; off, a query never surfaces them. */
    val hiddenSearchable: Boolean = false,
    /** App id to last-use epoch millis; null when usage access isn't granted. */
    val lastUsed: Map<String, Long>? = null,
    /** The current epoch millis, for phrasing [LibraryState.lastUsedLines]. */
    val now: Long = 0,
)

/** One scrubber letter and the first row it jumps to. Non-letter labels bucket under '#'. */
data class LibraryIndexEntry(val letter: Char, val firstRow: Int)

/** Resolved library content; the UI renders exactly this, in this order. */
data class LibraryState(
    val layout: LibraryLayout,
    /** The flat list; what Alphabetical and CompactIcons render. */
    val rows: List<HomeAction>,
    /** Scrubber entries for letters actually present in [rows]; empty for Categories. */
    val index: List<LibraryIndexEntry>,
    /** Titled groups; non-empty only for the Categories layout, "Other" last. */
    val sections: List<LibrarySection>,
    /** The hidden section: hidden apps, alphabetical; empty while a search excludes them. */
    val hiddenRows: List<HomeAction>,
    /** App id to subdued screen-time line ("Last used 8 minutes ago"); empty without usage access. */
    val lastUsedLines: Map<String, String>,
    /** A quiet line under the switcher when a layout can't fully deliver (Recent sans access). */
    val layoutNote: String?,
)

/** Resolves what the App Library shows: every launchable app, alphabetical ignoring case. */
fun resolveLibrary(inputs: LibraryInputs): LibraryState {
    val query = inputs.query.trim()
    fun matches(app: HomeAction) = query.isEmpty() || app.label.contains(query, ignoreCase = true)
    // Locale-independent so ordering can't shift under a Turkish-ı style locale.
    val alphabetical = compareBy(String.CASE_INSENSITIVE_ORDER, HomeAction::label)
    val (hiddenApps, visible) = inputs.apps.partition { it.id in inputs.hidden }
    val recency = inputs.lastUsed
    val ordering =
        if (inputs.layout == LibraryLayout.Recent && recency != null)
            compareByDescending<HomeAction> { recency[it.id] ?: Long.MIN_VALUE }.then(alphabetical)
        else alphabetical
    val rows = visible.filter(::matches).sortedWith(ordering)
    val hiddenRows = hiddenApps
        .filter { (query.isEmpty() || inputs.hiddenSearchable) && matches(it) }
        .sortedWith(alphabetical)
    val categorised = inputs.layout == LibraryLayout.Categories
    val recentOrdered = inputs.layout == LibraryLayout.Recent && recency != null
    val index = if (categorised || recentOrdered) emptyList() else rows.withIndex()
        .groupBy { (_, app) -> app.label.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#' }
        .map { (letter, entries) -> LibraryIndexEntry(letter, entries.first().index) }
        .sortedBy { it.firstRow }
    val sections = if (!categorised) emptyList() else rows
        .groupBy { inputs.categories[it.id] ?: OTHER_CATEGORY }
        .map { (title, apps) -> LibrarySection(title, apps) }
        .sortedWith(compareBy({ it.title == OTHER_CATEGORY }, { it.title }))
    return LibraryState(
        layout = inputs.layout,
        rows = rows,
        index = index,
        sections = sections,
        hiddenRows = hiddenRows,
        lastUsedLines = recency.orEmpty().mapValues { (_, then) -> lastUsedLabel(inputs.now, then) },
        layoutNote = "Recents need usage access"
            .takeIf { inputs.layout == LibraryLayout.Recent && recency == null },
    )
}

const val OTHER_CATEGORY = "Other"

private fun lastUsedLabel(now: Long, then: Long): String {
    val minutes = (now - then) / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "Last used $minutes minute${if (minutes == 1L) "" else "s"} ago"
        hours < 24 -> "Last used $hours hour${if (hours == 1L) "" else "s"} ago"
        else -> "Last used $days day${if (days == 1L) "" else "s"} ago"
    }
}
