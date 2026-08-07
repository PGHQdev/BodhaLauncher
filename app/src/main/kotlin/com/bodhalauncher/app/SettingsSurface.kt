package com.bodhalauncher.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.bodhalauncher.app.ui.BodhaSpacing
import com.bodhalauncher.app.ui.BodhaType
import com.bodhalauncher.app.ui.CardRow
import com.bodhalauncher.app.ui.ChoiceRow
import com.bodhalauncher.app.ui.LocalBodhaColors
import com.bodhalauncher.app.ui.SectionOverline
import com.bodhalauncher.app.ui.focusOnOpen
import com.bodhalauncher.engine.ClockFormat
import com.bodhalauncher.engine.DateFormat
import com.bodhalauncher.engine.SETTINGS_ROWS
import com.bodhalauncher.engine.SettingsRow
import com.bodhalauncher.engine.SettingsRowId
import com.bodhalauncher.engine.ThemeChoice

/**
 * Settings (#140, ADR 0019), reached from Home's long-press edit mode: the
 * ungrouped home-role row, then Appearance's three format and theme controls
 * (#141). The remaining sections arrive in later slices, each adding rows to the
 * engine's catalogue rather than to a layout here.
 *
 * Rows are the vocabulary's card row (ADR 0025 rule 1). None draws a chevron:
 * every row here acts in place — the home-role one opens the system role
 * request, and the three choices settle where they stand (rule 3).
 *
 * Back and Escape come from the navigation model's single binding (#132); this
 * surface binds neither, and takes focus on arrival only because that is what
 * gives Escape a chain to travel up (ADR 0022). Where that arrival lands is
 * [target]: the row Search was asked for, or the first row.
 */
@Composable
fun SettingsSurface(
    /** Read on every resume, so losing the role while Settings is open shows here (#136). */
    homeRoleHeld: Boolean,
    onRequestHomeRole: () -> Unit,
    appearance: AppearanceChoices,
    /**
     * The row this arrival is about, when Search found it by name (#191). Null is
     * every other way in, and lands on the first row as it always did. An id this
     * build does not render falls back to the same place rather than to nowhere.
     */
    target: SettingsRowId? = null,
) {
    val colors = LocalBodhaColors.current
    val landing = SETTINGS_ROWS.firstOrNull { it.id == target } ?: SETTINGS_ROWS.first()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = BodhaSpacing.l),
    ) {
        // A surface name is a label, not a spoken line (ADR 0021).
        Text("Settings", color = colors.ink, style = BodhaType.title)
        Spacer(Modifier.height(BodhaSpacing.l))
        Column(verticalArrangement = Arrangement.spacedBy(BodhaSpacing.s)) {
            SETTINGS_ROWS.forEachIndexed { index, row ->
                // The overline appears where the section changes, so the
                // catalogue's order is the render order and there is no second
                // list of sections to keep in step with it.
                if (row.section != SETTINGS_ROWS.getOrNull(index - 1)?.section) {
                    row.section?.let { SectionOverline(it.title) }
                }
                val arriving = if (row.id == landing.id) Modifier.arriveHere() else Modifier
                when (row.id) {
                    SettingsRowId.HomeRole -> CardRow(
                        title = row.label,
                        // The state in the row's own words; Home carries the same
                        // fact as one line while the role is declined (#136).
                        subtitle = if (homeRoleHeld) "Bodha is your home app"
                        else "Bodha is an app you open",
                        // Declining leaves the row truthful and re-prompts nothing;
                        // holding the role already means the platform has nothing
                        // to ask, so the tap re-reads and the row says what it said.
                        onClick = onRequestHomeRole,
                        modifier = arriving,
                    )
                    SettingsRowId.Theme -> ChoiceRow(
                        title = row.label,
                        options = THEME_OPTIONS,
                        current = appearance.theme,
                        onPick = appearance.onTheme,
                        modifier = arriving,
                    )
                    SettingsRowId.ClockFormat -> ChoiceRow(
                        title = row.label,
                        options = CLOCK_OPTIONS,
                        current = appearance.clock,
                        onPick = appearance.onClock,
                        modifier = arriving,
                    )
                    SettingsRowId.DateFormat -> ChoiceRow(
                        title = row.label,
                        options = DATE_OPTIONS,
                        current = appearance.date,
                        onPick = appearance.onDate,
                        modifier = arriving,
                    )
                }
            }
        }
    }
}

/**
 * Where an arrival lands: the row takes focus, and the row is scrolled to.
 *
 * Two halves, because they serve two people. Focus is ADR 0022's arrival and what
 * gives Escape a chain to travel up — but a row is focusable in non-touch mode
 * only, so for the touch user who just tapped a search result it never lands. The
 * scroll is that user's half, and once Settings runs past a screen it is the only
 * thing that makes "opens at that row" true for them (#191).
 *
 * The requester is foundation's experimental one because `compose.ui`'s stable
 * `bringIntoViewRequester` does not exist at this Compose version; the migration
 * when it does is an import.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.arriveHere(): Modifier {
    val bringIntoView = remember { BringIntoViewRequester() }
    // Asked once the row has been placed, not on composition: the request is
    // about where the row is, and until the first placement there is no answer.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(placed) { if (placed) bringIntoView.bringIntoView() }
    return focusOnOpen()
        .bringIntoViewRequester(bringIntoView)
        .onGloballyPositioned { placed = true }
}

/**
 * What Appearance holds and what changes it — the store's three values and three
 * setters, gathered so the surface takes one parameter per section rather than
 * six per section, and so a test can hand it plain state.
 */
data class AppearanceChoices(
    val theme: ThemeChoice,
    val onTheme: (ThemeChoice) -> Unit,
    val clock: ClockFormat,
    val onClock: (ClockFormat) -> Unit,
    val date: DateFormat,
    val onDate: (DateFormat) -> Unit,
)

/**
 * Everything a row publishes as an actionable node: itself, where the row is its
 * own control, or its answers, where the row is a choice.
 *
 * The rendering above and this list are the same values, so "the surface renders
 * exactly the catalogue" stays a fact rather than two lists agreeing — the
 * property #140 built [SETTINGS_ROWS] for, held now that a row's label and its
 * controls are no longer the same string. The `when` is exhaustive, so a row
 * added without declaring what it publishes fails to compile.
 */
internal fun settingsRowControls(row: SettingsRow): List<String> =
    when (row.id) {
        SettingsRowId.HomeRole -> listOf(row.label)
        SettingsRowId.Theme -> THEME_OPTIONS.map { it.second }
        SettingsRowId.ClockFormat -> CLOCK_OPTIONS.map { it.second }
        SettingsRowId.DateFormat -> DATE_OPTIONS.map { it.second }
    }

/**
 * The words on the pills, decoupled from the names the store persists — the same
 * move `openCheckModeLabel` makes, and for its reason: the exhaustive `when`
 * forces every future entry to bring its own.
 *
 * The date formats are named rather than shown as samples. A pill wide enough to
 * read "Wednesday, 5 August" leaves two answers off screen, and the result is one
 * tap away on Home and Today, where the date actually lives.
 */
private val THEME_OPTIONS: List<Pair<ThemeChoice, String>> = ThemeChoice.entries.map {
    it to when (it) {
        ThemeChoice.Light -> "Light"
        ThemeChoice.Dark -> "Dark"
        ThemeChoice.System -> "System"
    }
}

private val CLOCK_OPTIONS: List<Pair<ClockFormat, String>> = ClockFormat.entries.map {
    it to when (it) {
        ClockFormat.TwelveHour -> "12-hour"
        ClockFormat.TwentyFourHour -> "24-hour"
        ClockFormat.Nato -> "NATO"
    }
}

private val DATE_OPTIONS: List<Pair<DateFormat, String>> = DateFormat.entries.map {
    it to when (it) {
        DateFormat.WeekdayAndMonth -> "Weekday and month"
        DateFormat.Short -> "Short"
        DateFormat.Numeric -> "Numeric"
    }
}
