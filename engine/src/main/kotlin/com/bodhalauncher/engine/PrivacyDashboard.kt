package com.bodhalauncher.engine

/** A capability's current grant, as read from the system by the edge. */
data class PermissionStatus(val capability: Capability, val granted: Boolean)

/** One stored data category: how much and how long (null = until deleted). */
data class DataCategorySummary(
    val category: RetentionCategory,
    val count: Int,
    val retentionDays: Int?,
)

data class DashboardInputs(
    val permissions: List<PermissionStatus> = emptyList(),
    val dataCategories: List<DataCategorySummary> = emptyList(),
    val connectors: List<String> = emptyList(),
    val cloudFeatures: List<String> = emptyList(),
    val signedIn: Boolean = false,
)

sealed interface DashboardRow {
    /** An empty section renders this explicitly — absence is shown, never assumed (#24). */
    data object None : DashboardRow

    data class Permission(val capability: Capability, val enables: String) : DashboardRow

    data class Data(val category: RetentionCategory, val count: Int, val retentionDays: Int?) : DashboardRow

    data class Named(val name: String) : DashboardRow
}

/** The rendered dashboard: the answer to "what does Bodha know and who can ask it". */
data class PrivacyDashboard(
    val localData: List<DashboardRow>,
    val permissions: List<DashboardRow>,
    val connectors: List<DashboardRow>,
    val cloudFeatures: List<DashboardRow>,
    val canExport: Boolean,
    val canDeleteLocalData: Boolean,
    val canDeleteAccount: Boolean,
)

/**
 * Resolves the privacy dashboard (#24). Only granted permissions list; each row
 * says what the grant enables. Empty sections yield an explicit none row so
 * "Connectors: none" is checkable UI, and it stays truthful automatically when
 * post-v1 features arrive.
 */
fun resolvePrivacyDashboard(inputs: DashboardInputs): PrivacyDashboard {
    fun orNone(rows: List<DashboardRow>) = rows.ifEmpty { listOf(DashboardRow.None) }

    val granted = inputs.permissions.filter { it.granted }.map { status ->
        DashboardRow.Permission(status.capability, enables = educationScreen(status.capability).feature)
    }

    // A category is one row however many stores fill it: session records and the
    // launch log both age out under RawUsageEvents (ADR 0013, ADR 0028), and two
    // rows carrying the same name and window would read as two retention answers.
    // The window is the category's, so the first summary's is every summary's.
    val data = inputs.dataCategories.groupBy { it.category }.map { (category, summaries) ->
        DashboardRow.Data(category, summaries.sumOf { it.count }, summaries.first().retentionDays)
    }

    return PrivacyDashboard(
        localData = orNone(data),
        permissions = orNone(granted),
        connectors = orNone(inputs.connectors.map { DashboardRow.Named(it) }),
        cloudFeatures = orNone(inputs.cloudFeatures.map { DashboardRow.Named(it) }),
        canExport = true,
        canDeleteLocalData = true,
        canDeleteAccount = inputs.signedIn,
    )
}
