package com.bodhalauncher.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.capability.CapabilityEdge
import com.bodhalauncher.app.capability.CapabilityEducation
import com.bodhalauncher.app.capability.EducationStateStore
import com.bodhalauncher.app.contacts.ContactsReader
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.app.focus.FocusStore
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.home.SearchDefaultStore
import com.bodhalauncher.app.today.CalendarReader
import com.bodhalauncher.app.ui.ACTIVITY_ROOT
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.DIALOG_ROOT
import com.bodhalauncher.app.ui.EditHomeDialog
import com.bodhalauncher.app.ui.GestureAction
import com.bodhalauncher.app.ui.HomeGestureAffordances
import com.bodhalauncher.app.ui.HomeGestures
import com.bodhalauncher.app.ui.SEARCH_FIELD_LABEL
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.app.ui.escapeIsBack
import com.bodhalauncher.app.ui.focusedNameIn
import com.bodhalauncher.app.ui.press
import com.bodhalauncher.app.ui.tabTo
import com.bodhalauncher.engine.ClockFormat
import com.bodhalauncher.engine.DateFormat
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.SettingsRowId
import com.bodhalauncher.engine.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The route to Settings, walked with the keys (#140): Tab to the edit-layout
 * affordance, Enter, Tab to the Settings row, Enter — and Escape back out.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp", application = android.app.Application::class)
class SettingsRouteTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `edit mode reaches Settings, whose row reaches the role request, and Escape leaves`() {
        var requests = 0
        var backs = 0
        var theme = ThemeChoice.System
        compose.setContent {
            BodhaTheme {
                var editing by remember { mutableStateOf(false) }
                var onSettings by remember { mutableStateOf(false) }
                BackHandler { backs++ }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    if (onSettings) {
                        SettingsSurface(
                            homeRoleHeld = false,
                            onRequestHomeRole = { requests++ },
                            appearance = AppearanceChoices(
                                theme = ThemeChoice.System, onTheme = { theme = it },
                                clock = ClockFormat.TwentyFourHour, onClock = {},
                                date = DateFormat.WeekdayAndMonth, onDate = {},
                            ),
                        )
                    } else {
                        HomeGestureAffordances(
                            HomeGestures(
                                swipeDown = GestureAction("Open Search") {},
                                swipeUp = GestureAction("Open App Library") {},
                                swipeLeft = GestureAction("Open Awareness") {},
                                swipeRight = GestureAction("Open Today") {},
                                doubleTapEmpty = GestureAction(label = null) {},
                                longPressEmpty = GestureAction("Edit layout") { editing = true },
                            )
                        )
                    }
                }
                if (editing) {
                    EditHomeDialog(
                        onAddPin = {},
                        onContextModes = {},
                        onSettings = { onSettings = true },
                        onDismiss = { editing = false },
                    )
                }
            }
        }

        compose.tabTo(ACTIVITY_ROOT, "Edit layout")
        compose.press(ACTIVITY_ROOT, Key.Enter)

        compose.tabTo(DIALOG_ROOT, "Settings")
        compose.press(DIALOG_ROOT, Key.Enter)

        // The surface's first row takes focus on arrival, which is also what gives
        // Escape a chain to reach the root binding along (ADR 0022).
        assertEquals("Home app", compose.focusedNameIn(ACTIVITY_ROOT))
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertEquals(1, requests)

        // A choice row's answers are ordinary targets in the same traversal, so
        // the theme is settable without ever leaving the keyboard (#141).
        compose.tabTo(ACTIVITY_ROOT, "Dark")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertEquals(ThemeChoice.Dark, theme)

        compose.press(ACTIVITY_ROOT, Key.Escape)
        assertEquals(1, backs)
    }

    /**
     * The other route in (#191): the row answers to its own label in Search, and
     * Settings opens standing on it rather than at the top. Driven through the
     * real [SearchSurface], so the catalogue reaching Search is part of what is
     * asserted — nothing here hands Search a list of rows.
     */
    @Test
    fun `a settings row found in Search opens Settings standing on that row`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sheets = SheetSlot()
        val events = EventLogger(BodhaDatabase.get(context).eventLog())
        compose.setContent {
            BodhaTheme {
                var target by remember { mutableStateOf<SettingsRowId?>(null) }
                var onSettings by remember { mutableStateOf(false) }
                if (onSettings) {
                    SettingsSurface(
                        homeRoleHeld = true,
                        onRequestHomeRole = {},
                        appearance = AppearanceChoices(
                            theme = ThemeChoice.System, onTheme = {},
                            clock = ClockFormat.TwentyFourHour, onClock = {},
                            // Not the first answer, so the assertion below says
                            // which of the two it is that focus lands on.
                            date = DateFormat.Numeric, onDate = {},
                        ),
                        target = target,
                    )
                } else {
                    SearchSurface(
                        pinStore = PinStore(context),
                        libraryStore = LibraryStore(context),
                        defaultStore = SearchDefaultStore(context),
                        launchRecords = BodhaDatabase.get(context).launchRecords(),
                        catalog = AppCatalog(context),
                        education = CapabilityEducation(
                            CapabilityEdge(context), EducationStateStore(context), events, sheets,
                        ),
                        calendar = CalendarReader(context),
                        contacts = ContactsReader(context),
                        focusStore = FocusStore(context, BodhaDatabase.get(context).focusRecords(), events),
                        session = SessionId(1),
                        launcherVisible = true,
                        surfaces = emptyList(),
                        sheets = sheets,
                        openApp = {},
                        openSurface = {},
                        openSettingsRow = { target = it; onSettings = true },
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(SEARCH_FIELD_LABEL).performTextInput("date")
        // Tabbed to rather than taken by Down: no grant stands in this environment,
        // so the contacts section's named state is the first row of the results.
        compose.tabTo(ACTIVITY_ROOT, "Date format")
        compose.press(ACTIVITY_ROOT, Key.Enter)

        // Arrival is the row that was searched for, not the first row of Settings —
        // and on a choice row it is the answer that holds, so what a reader hears
        // is the current value rather than an answer that is merely first (#213).
        assertEquals("Numeric", compose.focusedNameIn(ACTIVITY_ROOT))
    }
}
