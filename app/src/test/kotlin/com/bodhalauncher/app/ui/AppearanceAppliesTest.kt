package com.bodhalauncher.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.ClockFormat
import com.bodhalauncher.engine.DateFormat
import com.bodhalauncher.engine.HomeState
import com.bodhalauncher.engine.ThemeChoice
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/**
 * That a choice made in Settings reaches the surfaces (#141). The choices ride
 * on the theme, so what these assert is that a screen *reads* them — a screen
 * holding a formatter of its own would render the default here whatever was
 * chosen, and that is the failure worth catching.
 *
 * Home reads the wall clock and a fixture cannot hand it one, so the clock is
 * asserted by its shape rather than against a time this test also computes: four
 * digits and no separator is what NATO looks like at every hour, and it is
 * exactly what "renders as NATO, not as 24-hour with a different label" means.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h1200dp", application = android.app.Application::class)
class AppearanceAppliesTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var original: Locale

    @Before
    fun pinLocale() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @After
    fun restoreLocale() = Locale.setDefault(original)

    private val day = LocalDate.of(2026, 8, 5)

    private fun renderedTexts(): List<String> {
        val found = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { found += it.text }
            node.children.forEach(::walk)
        }
        walk(compose.onRoot().fetchSemanticsNode())
        return found
    }

    @Composable
    private fun Home() = HomeScreen(
        state = HomeState(
            dailyIntention = null,
            contextLabel = null,
            actions = emptyList(),
            inboxDigest = null,
            focusActive = false,
            sessionIntent = null,
        )
    )

    @Composable
    private fun Today() = TodayScreen(
        day = day,
        intention = null,
        onEditIntention = {},
        daySlot = null,
        onEventTap = {},
        onDayTurnOn = {},
        digestSlot = null,
        onDigestTap = {},
        onDigestTurnOn = {},
    )

    @Test
    fun `Home's clock reads NATO as NATO`() {
        compose.setContent {
            BodhaTheme(formats = BodhaFormats(clock = ClockFormat.Nato)) { Home() }
        }

        val texts = renderedTexts()
        assertTrue("no four-digit clock in $texts", texts.any { it.matches(Regex("""\d{4}""")) })
        assertTrue("a separated clock in $texts", texts.none { it.matches(Regex("""\d{1,2}:\d{2}""")) })
    }

    @Test
    fun `Home's clock reads twenty-four hour as twenty-four hour`() {
        compose.setContent {
            BodhaTheme(formats = BodhaFormats(clock = ClockFormat.TwentyFourHour)) { Home() }
        }

        assertTrue(renderedTexts().any { it.matches(Regex("""\d{1,2}:\d{2}""")) })
    }

    @Test
    fun `Home's date is written in the chosen format`() {
        compose.setContent {
            BodhaTheme(formats = BodhaFormats(date = DateFormat.Numeric)) { Home() }
        }

        assertTrue(renderedTexts().any { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) })
    }

    @Test
    fun `the day key is written in the chosen format`() {
        compose.setContent {
            BodhaTheme(formats = BodhaFormats(date = DateFormat.Short)) { Today() }
        }

        compose.onNodeWithText("5 Aug 2026").assertExists()
    }

    /**
     * Changing the format re-renders what is already on screen, which is the
     * whole of "without a restart": the same composition, a new value.
     */
    @Test
    fun `a change lands on a surface already standing`() {
        var format by mutableStateOf(DateFormat.WeekdayAndMonth)
        compose.setContent { BodhaTheme(formats = BodhaFormats(date = format)) { Today() } }
        compose.onNodeWithText("Wednesday, 5 August").assertExists()

        format = DateFormat.Numeric

        compose.onNodeWithText("2026-08-05").assertExists()
    }

    @Test
    fun `Light and Dark answer for themselves and System asks the platform`() {
        var resolved: List<Boolean> = emptyList()
        compose.setContent {
            resolved = listOf(
                ThemeChoice.Light.isDark(),
                ThemeChoice.Dark.isDark(),
                ThemeChoice.System.isDark(),
            )
        }

        assertFalse(resolved[0])
        assertTrue(resolved[1])
        // The qualifiers above are the not-night ones, so System agrees with
        // them — the point being that it reads the platform at all.
        assertFalse(resolved[2])
    }

    /**
     * System follows a change made while a surface is standing (#141) — it reads
     * the configuration on every composition rather than resolving once.
     *
     * The configuration is provided here rather than changed on the device: a
     * qualifier change under Robolectric does not reach a view that is already
     * composed, so driving it that way would assert the harness. What the
     * activity contributes is `uiMode` in `configChanges`, which makes the real
     * change a repaint instead of a recreation — and so keeps the user on the
     * surface they were on rather than returning them to root.
     */
    @Test
    fun `System re-reads the configuration rather than resolving once`() {
        var night by mutableStateOf(false)
        var dark = false
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configurationFor(night)) {
                dark = ThemeChoice.System.isDark()
            }
        }
        assertFalse(dark)

        night = true
        compose.waitForIdle()

        assertTrue("the theme did not follow the configuration", dark)
    }

    private fun configurationFor(night: Boolean) = Configuration().apply {
        uiMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
}
