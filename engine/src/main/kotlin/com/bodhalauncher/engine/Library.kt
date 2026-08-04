package com.bodhalauncher.engine

/**
 * Everything the App Library may draw from. Grows as the library's features ship
 * (search #59, hidden section #62, layouts #66); absent inputs mean absent elements.
 */
data class LibraryInputs(
    val apps: List<HomeAction> = emptyList(),
    /** Search text; blank means no filtering. Matches anywhere in the label, ignoring case. */
    val query: String = "",
)

/** Resolved library content; the UI renders exactly this, in this order. */
data class LibraryState(
    val rows: List<HomeAction>,
)

/** Resolves what the App Library shows: every launchable app, alphabetical ignoring case. */
fun resolveLibrary(inputs: LibraryInputs): LibraryState {
    val query = inputs.query.trim()
    return LibraryState(
        rows = inputs.apps
            .filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) }
            // Locale-independent so ordering can't shift under a Turkish-ı style locale.
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }),
    )
}
