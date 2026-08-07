package com.bodhalauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.OpenCheckLines
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * ADR 0011's one-sheet rule, asserted on screen rather than read off the code.
 *
 * Two real sheets go through one [SheetSlot] here, because the claim is about
 * what the slot does to sheets — that a new one replaces the open one and takes
 * its state with it — and a fixture that drew placeholders would prove nothing
 * about the sheets the app actually opens.
 *
 * Escape and back are not re-asserted: every sheet takes `escapeDismisses`, and
 * `KeyboardOperabilityTest` already injects the key against both window kinds.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp", application = android.app.Application::class)
class OneSheetAtATimeTest {

    @get:Rule
    val compose = createComposeRule()

    private val maps = HomeAction("maps", "Maps")
    private val notes = HomeAction("notes", "Notes")
    private val slot = SheetSlot()

    private var dismissed = 0
    private var opened = 0

    /** The two sheets, each rendered only while it is the one in the slot. */
    @Composable
    private fun Sheets() {
        BodhaTheme {
            slot.showing<Sheet.OpenCheck>()?.let { sheet ->
                OpenCheckSheet(
                    app = sheet.app,
                    icon = null,
                    lines = OpenCheckLines(null, null),
                    onContextNoteTap = null,
                    onOpen = { opened++ },
                    onOpenFor = { _, _ -> opened++ },
                    onDismiss = { dismissed++; slot.close(sheet) },
                )
            }
            slot.showing<Sheet.AppActions>()?.let { sheet ->
                AppActionsSheet(
                    app = sheet.app,
                    shortcuts = emptyList(),
                    isPinned = false,
                    isHidden = false,
                    openCheckMode = null,
                    openCheckOffered = true,
                    onOpen = {},
                    onShortcut = {},
                    onPin = {},
                    onUnpin = {},
                    onHide = {},
                    onUnhide = {},
                    onGroups = {},
                    onPause = {},
                    onOpenCheck = {},
                    onAppInfo = {},
                    onDismiss = { slot.close(sheet) },
                )
            }
        }
    }

    @Test
    fun `a sheet opened over another leaves exactly one on screen`() {
        compose.setContent { Sheets() }

        compose.runOnIdle { slot.open(Sheet.OpenCheck(maps)) }
        compose.onNodeWithText("Still want to open it?").assertIsDisplayed()

        compose.runOnIdle { slot.open(Sheet.AppActions(notes)) }

        compose.onNodeWithText("App info").assertIsDisplayed()
        compose.onAllNodesWithText("Still want to open it?").assertCountEquals(0)
    }

    @Test
    fun `the replaced sheet starts fresh when it comes back`() {
        compose.setContent { Sheets() }

        compose.runOnIdle { slot.open(Sheet.OpenCheck(maps)) }
        compose.onNodeWithContentDescription("What you want to do there")
            .performTextInput("reply to Jo")
        compose.onNodeWithText("reply to Jo").assertIsDisplayed()

        compose.runOnIdle { slot.open(Sheet.AppActions(notes)) }
        compose.runOnIdle { slot.open(Sheet.OpenCheck(maps)) }

        // Nothing typed carried over: the sheet left composition rather than
        // being hidden, so its state went with it.
        compose.onAllNodesWithText("reply to Jo").assertCountEquals(0)
        compose.onNodeWithText("What do you want to do there?").assertIsDisplayed()
    }

    /** Dismissal is not a bypass: turning back never becomes an opening (#8). */
    @Test
    fun `turning back from an Open Check dismisses it and opens nothing`() {
        compose.setContent { Sheets() }

        compose.runOnIdle { slot.open(Sheet.OpenCheck(maps)) }
        compose.onNodeWithText("Go back").performClick()

        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(0, opened)
            assertEquals(null, slot.current)
        }
    }
}
