package com.bodhalauncher.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.engine.ClockFormat
import com.bodhalauncher.engine.DateFormat
import com.bodhalauncher.engine.SETTINGS_ROWS
import com.bodhalauncher.engine.SettingsRowId
import com.bodhalauncher.engine.SettingsSection
import com.bodhalauncher.engine.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Settings' shape (#140, #141): one list defines the rows and the catalogue, so
 * this asserts the surface renders exactly the catalogue — a row dropped by a
 * condition, or drawn under a different string than Search would match, fails
 * here. A row added with no rendering does not reach this test at all: the
 * surface's `when` on [com.bodhalauncher.engine.SettingsRowId] is exhaustive, so
 * it fails to compile first.
 *
 * A row's label and its controls are now two different things — a choice row
 * names itself in text and publishes a click per answer — so the set-equality
 * clause runs over [settingsRowControls], which is the same values the surface
 * renders. Two lists agreeing is what that function exists to prevent.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp", application = android.app.Application::class)
class SettingsSurfaceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun actionableNames(): List<String> {
        val found = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            if (SemanticsActions.OnClick in node.config) found += node
            node.children.forEach(::walk)
        }
        walk(compose.onRoot().fetchSemanticsNode())
        return found.mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }
    }

    private fun setSurface(
        homeRoleHeld: Boolean = true,
        onRequestHomeRole: () -> Unit = {},
        target: SettingsRowId? = null,
    ) =
        compose.setContent {
            BodhaTheme {
                SettingsSurface(
                    homeRoleHeld = homeRoleHeld,
                    onRequestHomeRole = onRequestHomeRole,
                    appearance = fixedChoices(),
                    target = target,
                )
            }
        }

    /** Nothing changes: the tests that only read do not need a store behind them. */
    private fun fixedChoices() = AppearanceChoices(
        theme = ThemeChoice.System, onTheme = {},
        clock = ClockFormat.TwentyFourHour, onClock = {},
        date = DateFormat.WeekdayAndMonth, onDate = {},
    )

    @Test
    fun `every catalogue row is rendered under the label Search would match`() {
        setSurface()

        SETTINGS_ROWS.forEach { compose.onNodeWithText(it.label).assertExists() }
    }

    @Test
    fun `the rendered controls and the catalogue's are the same set`() {
        setSurface()

        assertEquals(
            SETTINGS_ROWS.flatMap(::settingsRowControls).toSet(),
            actionableNames().toSet(),
        )
    }

    @Test
    fun `a section names itself once, above its rows`() {
        setSurface()

        compose.onNodeWithText(SettingsSection.Appearance.title).assertExists()
    }

    @Test
    fun `the home-role row states that Bodha holds the role`() {
        setSurface(homeRoleHeld = true)

        compose.onNodeWithText("Bodha is your home app").assertExists()
    }

    @Test
    fun `and states the declined case without re-prompting`() {
        var requests = 0
        setSurface(homeRoleHeld = false, onRequestHomeRole = { requests++ })

        compose.onNodeWithText("Bodha is an app you open").assertExists()
        assertEquals("arriving on Settings asks for nothing", 0, requests)
    }

    @Test
    fun `tapping the row re-opens the system role request`() {
        var requests = 0
        setSurface(homeRoleHeld = false, onRequestHomeRole = { requests++ })

        compose.onNodeWithText("Home app").performClick()
        assertEquals(1, requests)
    }

    /**
     * The tint is a colour, and a colour is what a screen reader cannot see. The
     * held answer therefore carries the selected state in its semantics, and this
     * is what asserts it — the fill alone would pass no accessibility clause.
     */
    @Test
    fun `the held answer is the selected one, and picking another moves it`() {
        var theme by mutableStateOf(ThemeChoice.System)
        compose.setContent {
            BodhaTheme {
                SettingsSurface(
                    homeRoleHeld = true,
                    onRequestHomeRole = {},
                    appearance = fixedChoices().copy(theme = theme, onTheme = { theme = it }),
                )
            }
        }

        compose.onNodeWithText("System").assertIsSelected()
        compose.onNodeWithText("Dark").assertIsNotSelected()

        compose.onNodeWithText("Dark").performClick()

        assertEquals(ThemeChoice.Dark, theme)
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("System").assertIsNotSelected()
    }

    /**
     * Rule 2's tint means the current item and nothing else, so exactly one
     * answer per choice row may claim it — three across the section, and none on
     * the home-role row, which holds no current value.
     */
    @Test
    fun `exactly one answer holds per choice row, and no other row claims the fill`() {
        setSurface()

        assertEquals(3, compose.onAllNodes(isSelected()).fetchSemanticsNodes().size)
    }

    @Test
    fun `each of the three settings reports the answer that was picked`() {
        var clock: ClockFormat? = null
        var date: DateFormat? = null
        compose.setContent {
            BodhaTheme {
                SettingsSurface(
                    homeRoleHeld = true,
                    onRequestHomeRole = {},
                    appearance = fixedChoices().copy(onClock = { clock = it }, onDate = { date = it }),
                )
            }
        }

        compose.onNodeWithText("NATO").performClick()
        compose.onNodeWithText("Numeric").performClick()

        assertEquals(ClockFormat.Nato, clock)
        assertEquals(DateFormat.Numeric, date)
    }

    /**
     * The touch half of arriving on a row (#191). Focus is the docked user's and
     * lands only in non-touch mode (ADR 0022), so a viewport short enough to
     * scroll is what shows the other half doing anything — and the first clause
     * is what says the row was off screen to begin with.
     */
    @Test
    @Config(qualifiers = "w411dp-h240dp")
    fun `opened on a row, that row is scrolled to`() {
        setSurface(target = SettingsRowId.DateFormat)

        compose.onNodeWithText("Date format").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h240dp")
    fun `and opened on nothing in particular, the first row still is`() {
        setSurface()

        compose.onNodeWithText("Home app").assertIsDisplayed()
        compose.onNodeWithText("Date format").assertIsNotDisplayed()
    }
}
