package com.bodhalauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.Exclusions
import com.bodhalauncher.engine.LaunchRecord
import com.bodhalauncher.engine.SessionDetail
import com.bodhalauncher.engine.SessionRecord
import com.bodhalauncher.engine.awarenessSessionLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The route to an exclusion (#178): the shared row-actions affordance, consumed
 * rather than rebuilt.
 *
 * Three ways in and one place they arrive — long-press on touch, the focused
 * row's Actions node on Right, and Menu as an accelerator that is never the only
 * route (ADR 0022, ADR 0023, #131). What this fixture owns is the wiring: the row
 * hands `onLongClick` to `ListRow` and nothing else, so if the affordance ever
 * stops working these three tests are what say so from Awareness's side.
 *
 * The slot is real, and both sheet render sites sit in one tail exactly as they
 * do on the surface, because ADR 0011's rule is about what two sheets do to each
 * other and a fixture with one of them proves nothing about that.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp", application = android.app.Application::class)
class AwarenessActionsRouteTest {

    @get:Rule
    val compose = createComposeRule()

    private val slot = SheetSlot()

    private val record = SessionRecord(
        id = 1,
        start = LocalDateTime.of(2026, 8, 7, 9, 41),
        end = LocalDateTime.of(2026, 8, 7, 9, 53),
    )
    private val session = AwarenessSession(record, intentional = false)
    private val sessionName = awarenessSessionLine(record)

    private val detail = SessionDetail(
        session = session,
        launches = listOf(LaunchRecord("atlas", LocalDateTime.of(2026, 8, 7, 9, 42), session = 1)),
        checks = 0,
        repeatedOpen = false,
        statement = null,
    )

    /** Snapshot state, so an exclusion made on the sheet reaches the screen behind it. */
    private var excluded by mutableStateOf(Exclusions())

    /**
     * The two branches that carry rows with actions, and the one tail both sheets
     * render from — the surface's own shape, minus the reads.
     */
    @Composable
    private fun Host() {
        var open by remember { mutableStateOf(false) }
        BodhaTheme {
            if (open) {
                SessionDetailScreen(
                    detail = detail,
                    labelFor = { "Atlas" },
                    iconFor = { null },
                    onOpenApp = {},
                    onAppActions = { slot.open(Sheet.AwarenessAppActions(it)) },
                )
            } else {
                AwarenessScreen(
                    today = AwarenessToday.Sessions(finished = 1, running = false),
                    sessions = listOf(session),
                    day = LocalDate.of(2026, 8, 7),
                    isToday = true,
                    exclusions = excluded,
                    onPickView = {},
                    onOpenSession = { open = true },
                    onSessionActions = { slot.open(Sheet.AwarenessSessionActions(it.record)) },
                    onOpenExclusions = {},
                    onBack = {},
                )
            }
            slot.showing<Sheet.AwarenessSessionActions>()?.let { sheet ->
                val dismiss = slot.dismissedBy(sheet) { slot.close(sheet) }
                AwarenessActionsSheet(
                    title = awarenessSessionLine(sheet.record),
                    onExclude = {
                        dismiss()
                        excluded = excluded.copy(sessions = excluded.sessions + sheet.record.id)
                    },
                    onDismiss = dismiss,
                )
            }
            slot.showing<Sheet.AwarenessAppActions>()?.let { sheet ->
                val dismiss = slot.dismissedBy(sheet) { slot.close(sheet) }
                AwarenessActionsSheet(
                    title = "Atlas",
                    onExclude = {
                        dismiss()
                        excluded = excluded.copy(apps = excluded.apps + sheet.appId)
                    },
                    onDismiss = dismiss,
                )
            }
        }
    }

    private fun openSessionView() {
        compose.tabTo(ACTIVITY_ROOT, sessionName)
        compose.press(ACTIVITY_ROOT, Key.Enter)
    }

    @Test
    fun `Right on a focused session row reveals its Actions node and Enter opens the sheet`() {
        compose.setContent { Host() }

        compose.tabTo(ACTIVITY_ROOT, sessionName)
        compose.press(ACTIVITY_ROOT, Key.DirectionRight)
        assertEquals(ACTIONS_LABEL, compose.focusedNameIn(ACTIVITY_ROOT))

        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertTrue(sessionName in compose.drawnTextIn(DIALOG_ROOT))
        assertTrue("Exclude" in compose.drawnTextIn(DIALOG_ROOT))
    }

    /** One press instead of two, on the same actions, and never the only route. */
    @Test
    fun `Menu on a focused launch row opens the sheet in one press`() {
        compose.setContent { Host() }

        openSessionView()
        compose.tabTo(ACTIVITY_ROOT, "Atlas")
        compose.press(ACTIVITY_ROOT, Key.Menu)

        assertEquals(listOf("Atlas", "Exclude"), compose.drawnTextIn(DIALOG_ROOT))
    }

    /** The touch route, on the node the row publishes for it. */
    @Test
    fun `long-press opens the same sheet`() {
        compose.setContent { Host() }

        compose.nodesIn(ACTIVITY_ROOT)
            .single { it.spokenName() == sessionName }
            .config[SemanticsActions.OnLongClick].action?.invoke()

        assertTrue(slot.current is Sheet.AwarenessSessionActions)
        assertTrue("Exclude" in compose.drawnTextIn(DIALOG_ROOT))
    }

    /**
     * ADR 0011, structurally: there is one variable, so two sheets is not a state
     * this app can express — and the second one arriving takes the first with it
     * rather than stacking on it (#133).
     */
    @Test
    fun `opening an app's actions over a session's actions leaves exactly one sheet`() {
        compose.setContent { Host() }

        compose.runOnIdle { slot.open(Sheet.AwarenessSessionActions(record)) }
        assertTrue(sessionName in compose.drawnTextIn(DIALOG_ROOT))

        compose.runOnIdle { slot.open(Sheet.AwarenessAppActions("atlas")) }

        assertTrue(slot.current is Sheet.AwarenessAppActions)
        assertEquals(listOf("Atlas", "Exclude"), compose.drawnTextIn(DIALOG_ROOT))
        assertEquals(1, compose.drawnTextIn(DIALOG_ROOT).count { it == "Exclude" })
    }

    /**
     * A sheet is dismissible without consequence (ADR 0011, CONTEXT.md **Sheet**):
     * leaving it excludes nothing, which is the whole difference between offering
     * an action and performing one.
     */
    @Test
    fun `dismissing the sheet changes no exclusion`() {
        compose.setContent { Host() }

        compose.runOnIdle { slot.open(Sheet.AwarenessSessionActions(record)) }
        compose.press(DIALOG_ROOT, Key.Escape)

        compose.runOnIdle {
            assertNull(slot.current)
            assertTrue(excluded.isEmpty)
        }
    }

    /** And performing it does exclude, then closes the one sheet behind it. */
    @Test
    fun `excluding from the sheet records it and leaves no sheet open`() {
        compose.setContent { Host() }

        compose.runOnIdle { slot.open(Sheet.AwarenessAppActions("atlas")) }
        compose.nodesIn(DIALOG_ROOT)
            .single { it.spokenName() == "Exclude" }
            .config[SemanticsActions.OnClick].action?.invoke()

        compose.runOnIdle {
            assertNull(slot.current)
            assertEquals(setOf("atlas"), excluded.apps)
        }
    }
}
