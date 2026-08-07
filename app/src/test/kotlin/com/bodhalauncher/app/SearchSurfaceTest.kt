package com.bodhalauncher.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
import com.bodhalauncher.app.today.CalendarReader
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.home.SearchDefaultStore
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.SEARCH_FIELD_LABEL
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.engine.SEARCH_CONTACTS_OFF
import com.bodhalauncher.engine.SessionId
import org.junit.Rule
import org.robolectric.Shadows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The query is session-scoped (ADR 0014: Search opens empty), which the surface
 * expresses by hanging it off the session key. That the key is the *same* one
 * across a merge-window resume is [com.bodhalauncher.engine.SessionEngineTest]'s;
 * here only the surface's half is driven — a different key, a cleared field.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp", application = android.app.Application::class)
class SearchSurfaceTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a new session opens Search empty rather than on the last one's query`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pinStore = PinStore(context)
        val libraryStore = LibraryStore(context)
        val catalog = AppCatalog(context)
        var session by mutableStateOf(SessionId(1))
        val sheets = SheetSlot()
        val events = EventLogger(BodhaDatabase.get(context).eventLog())
        compose.setContent {
            BodhaTheme {
                SearchSurface(
                    pinStore = pinStore,
                    libraryStore = libraryStore,
                    defaultStore = SearchDefaultStore(context),
                    catalog = catalog,
                    education = CapabilityEducation(
                        CapabilityEdge(context), EducationStateStore(context), events, sheets,
                    ),
                    calendar = CalendarReader(context),
                    contacts = ContactsReader(context),
                    focusStore = FocusStore(context, BodhaDatabase.get(context).focusRecords(), events),
                    session = session,
                    surfaces = emptyList(),
                    sheets = sheets,
                    openApp = {},
                    openSurface = {},
                )
            }
        }

        compose.onNodeWithContentDescription(SEARCH_FIELD_LABEL).performTextInput("insta")
        compose.onNodeWithText("insta").assertIsDisplayed()

        session = SessionId(2)

        compose.onNodeWithText("insta").assertDoesNotExist()
    }

    /**
     * The grant is observed at the query, not at the grant (#186): granting
     * while Search is open changes nothing already drawn, and the next
     * keystroke is what brings the section back.
     */
    @Test
    fun `granting contacts mid-session lands on the next query, not retroactively`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sheets = SheetSlot()
        val events = EventLogger(BodhaDatabase.get(context).eventLog())
        compose.setContent {
            BodhaTheme {
                SearchSurface(
                    pinStore = PinStore(context),
                    libraryStore = LibraryStore(context),
                    defaultStore = SearchDefaultStore(context),
                    catalog = AppCatalog(context),
                    education = CapabilityEducation(
                        CapabilityEdge(context), EducationStateStore(context), events, sheets,
                    ),
                    calendar = CalendarReader(context),
                    contacts = ContactsReader(context),
                    focusStore = FocusStore(context, BodhaDatabase.get(context).focusRecords(), events),
                    session = SessionId(1),
                    surfaces = emptyList(),
                    sheets = sheets,
                    openApp = {},
                    openSurface = {},
                )
            }
        }

        compose.onNodeWithContentDescription(SEARCH_FIELD_LABEL).performTextInput("jo")
        compose.onNodeWithText(SEARCH_CONTACTS_OFF).assertIsDisplayed()

        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.READ_CONTACTS)
        compose.waitForIdle()
        // Nothing already drawn re-renders on the grant alone.
        compose.onNodeWithText(SEARCH_CONTACTS_OFF).assertIsDisplayed()

        compose.onNodeWithContentDescription(SEARCH_FIELD_LABEL).performTextInput("h")
        compose.onNodeWithText(SEARCH_CONTACTS_OFF).assertDoesNotExist()
    }
}
