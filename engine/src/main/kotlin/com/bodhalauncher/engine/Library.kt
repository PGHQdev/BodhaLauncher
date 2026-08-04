package com.bodhalauncher.engine

/**
 * Everything the App Library may draw from. Grows as the library's features ship
 * (search #59, hidden section #62, layouts #66); absent inputs mean absent elements.
 */
data class LibraryInputs(
    val apps: List<HomeAction> = emptyList(),
    /** Search text; blank means no filtering. Matches anywhere in the label, ignoring case. */
    val query: String = "",
    /** App ids the user has hidden; they collect in [LibraryState.hiddenRows]. */
    val hidden: Set<String> = emptySet(),
    /** Whether hidden apps may match a search; off, a query never surfaces them. */
    val hiddenSearchable: Boolean = false,
)

/** One scrubber letter and the first row it jumps to. Non-letter labels bucket under '#'. */
data class LibraryIndexEntry(val letter: Char, val firstRow: Int)

/** Resolved library content; the UI renders exactly this, in this order. */
data class LibraryState(
    val rows: List<HomeAction>,
    /** Scrubber entries for letters actually present in [rows], in row order. */
    val index: List<LibraryIndexEntry>,
    /** The hidden section: hidden apps, alphabetical; empty while a search excludes them. */
    val hiddenRows: List<HomeAction>,
)

/** Resolves what the App Library shows: every launchable app, alphabetical ignoring case. */
fun resolveLibrary(inputs: LibraryInputs): LibraryState {
    val query = inputs.query.trim()
    fun matches(app: HomeAction) = query.isEmpty() || app.label.contains(query, ignoreCase = true)
    // Locale-independent so ordering can't shift under a Turkish-ı style locale.
    val alphabetical = compareBy(String.CASE_INSENSITIVE_ORDER, HomeAction::label)
    val (hiddenApps, visible) = inputs.apps.partition { it.id in inputs.hidden }
    val rows = visible.filter(::matches).sortedWith(alphabetical)
    val hiddenRows = hiddenApps
        .filter { (query.isEmpty() || inputs.hiddenSearchable) && matches(it) }
        .sortedWith(alphabetical)
    val index = rows.withIndex()
        .groupBy { (_, app) -> app.label.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#' }
        .map { (letter, entries) -> LibraryIndexEntry(letter, entries.first().index) }
        .sortedBy { it.firstRow }
    return LibraryState(rows = rows, index = index, hiddenRows = hiddenRows)
}
