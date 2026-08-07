package com.bodhalauncher.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bodhalauncher.app.ui.BodhaSpacing
import com.bodhalauncher.app.ui.BodhaType
import com.bodhalauncher.app.ui.CardRow
import com.bodhalauncher.app.ui.LocalBodhaColors
import com.bodhalauncher.app.ui.focusOnOpen
import com.bodhalauncher.engine.SETTINGS_ROWS
import com.bodhalauncher.engine.SettingsRow
import com.bodhalauncher.engine.SettingsRowId

/**
 * Settings (#140, ADR 0019), reached from Home's long-press edit mode: the
 * ungrouped home-role row and, for now, nothing else. The six sections arrive in
 * later slices, each adding rows to the engine's catalogue rather than to a
 * layout here.
 *
 * Rows are the vocabulary's card row (ADR 0025 rule 1) and draw no chevron: this
 * one opens the system role request, which is acting in place rather than
 * navigating (rule 3).
 *
 * Back and Escape come from the navigation model's single binding (#132); this
 * surface binds neither, and takes focus on arrival only because that is what
 * gives Escape a chain to travel up (ADR 0022).
 */
@Composable
fun SettingsSurface(
    /** Read on every resume, so losing the role while Settings is open shows here (#136). */
    homeRoleHeld: Boolean,
    onRequestHomeRole: () -> Unit,
) {
    val colors = LocalBodhaColors.current
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
                val arriving = if (index == 0) Modifier.focusOnOpen() else Modifier
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
                }
            }
        }
    }
}
