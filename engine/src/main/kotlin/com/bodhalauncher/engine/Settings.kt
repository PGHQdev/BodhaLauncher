package com.bodhalauncher.engine

/**
 * Settings' six sections (ADR 0019), in the order they render.
 *
 * The home-role row sits ungrouped above all of them — whether Bodha is the home
 * app is the app's most consequential state rather than a member of a category —
 * which is why [SettingsRow.section] is nullable instead of this enum carrying a
 * seventh entry for "no section".
 */
enum class SettingsSection(val title: String) {
    Appearance("Appearance"),
    Navigation("Navigation"),
    Intentionality("Intentionality"),
    PrivacyAndData("Privacy and data"),
    BodhaPro("Bodha Pro"),
    About("About"),
}

/**
 * Which row this is, and so what it targets.
 *
 * An enum rather than free text, because the surface renders a row through an
 * exhaustive `when` on it: a row added to [SETTINGS_ROWS] with no rendering
 * fails to compile rather than failing to draw. Same move as `openCheckModeLabel`
 * — every future entry has to bring its own words.
 */
enum class SettingsRowId { HomeRole, Theme, ClockFormat, DateFormat }

/**
 * One row of Settings, defined once (#140): its label, the section it sits in,
 * and — through [id] — what it targets.
 *
 * ADR 0019 makes each row a Search target matched on its label, and the
 * catalogue that domain reads is [SETTINGS_ROWS] itself. A later slice therefore
 * adds its search entry by adding its row, rather than by remembering to
 * register it a second time somewhere else.
 */
data class SettingsRow(
    val id: SettingsRowId,
    val label: String,
    /** Null for the ungrouped home-role row above every section (ADR 0019). */
    val section: SettingsSection? = null,
)

/**
 * Every Settings row there is, in render order — the catalogue and the render
 * list are this one value, which is what makes "the rendered rows and the
 * catalogue are the same set" a fact rather than two lists agreeing.
 *
 * The remaining sections' rows arrive in later slices (#142–#153), including the
 * privacy dashboard's — which sit here flat, beside the rows of the Settings
 * root, because a row is a search target wherever it is drawn (#191, ADR 0019).
 *
 * Labels are what someone types (ADR 0019): "Theme", "Clock format", "Date
 * format", so a prefix at a word boundary finds each by the word it is about.
 * This list is what [SearchInputs.settingsRows] is handed, so a row added here is
 * findable without registering it a second time.
 */
val SETTINGS_ROWS: List<SettingsRow> = listOf(
    SettingsRow(SettingsRowId.HomeRole, "Home app"),
    // Accent is #142's; clay and slate have no colour values yet.
    SettingsRow(SettingsRowId.Theme, "Theme", SettingsSection.Appearance),
    SettingsRow(SettingsRowId.ClockFormat, "Clock format", SettingsSection.Appearance),
    SettingsRow(SettingsRowId.DateFormat, "Date format", SettingsSection.Appearance),
)
