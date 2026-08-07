package com.bodhalauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.AwarenessDayFigures
import com.bodhalauncher.engine.AwarenessDuration
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.AwarenessUsage
import com.bodhalauncher.engine.AwarenessView
import com.bodhalauncher.engine.AwarenessWeek
import com.bodhalauncher.engine.EntitlementSnapshot
import com.bodhalauncher.engine.Exclusions
import com.bodhalauncher.engine.GateDecision
import com.bodhalauncher.engine.GatedRequest
import com.bodhalauncher.engine.LaunchRecord
import com.bodhalauncher.engine.Place
import com.bodhalauncher.engine.ProBoundary
import com.bodhalauncher.engine.SessionDetail
import com.bodhalauncher.engine.SessionRecord
import com.bodhalauncher.engine.Surface
import com.bodhalauncher.engine.UnavailableReason
import com.bodhalauncher.engine.resolveAppOpens
import com.bodhalauncher.engine.resolveBack
import com.bodhalauncher.engine.resolveEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The route into a session and out again, walked with the keys (#173): Tab to a
 * session row, Enter, and Escape back out.
 *
 * Where it comes out is the point. The Session view is a drill-down reached from
 * a row, and ADR 0011 refuses a stack — so back leaves for **root**, not for the
 * Today view, and the view Awareness reopens on is the list. The rule under test
 * is the engine's own [resolveBack]; the Session view is deliberately not a
 * [Place], which is what makes the depth question not arise.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp", application = android.app.Application::class)
class AwarenessRouteTest {

    @get:Rule
    val compose = createComposeRule()

    private val sessions = listOf(
        AwarenessSession(
            record = SessionRecord(
                id = 1,
                start = LocalDateTime.of(2026, 8, 7, 9, 41),
                end = LocalDateTime.of(2026, 8, 7, 9, 53),
            ),
            intentional = false,
        )
    )

    private val detail = SessionDetail(
        session = sessions[0],
        launches = listOf(LaunchRecord("atlas", LocalDateTime.of(2026, 8, 7, 9, 42), session = 1)),
        checks = 0,
        repeatedOpen = false,
        statement = null,
    )

    private val today: LocalDate = LocalDate.of(2026, 8, 7)

    private val week = AwarenessWeek(
        days = (6L downTo 0L).map {
            AwarenessDayFigures(
                day = today.minusDays(it),
                sessions = if (it == 0L) 1 else 2,
                intentional = 1,
            )
        },
        rate = AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
        previousRate = AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
    )

    /**
     * Which session is open, which app inside it, which view is showing and
     * which day that view is showing all live inside this branch, exactly as
     * they live inside `AwarenessSurface` — so leaving for root is what drops
     * every one of them, and there is no state anywhere for a second back press
     * to walk down. Two levels of drill-down and a switch, still no stack.
     */
    /**
     * The gate's own copy, which is what the edge of the window states (#177). A
     * free tier, so the Today branch below is standing on a day whose older
     * records the window withheld.
     */
    private val boundary = (
        resolveEntitlement(EntitlementSnapshot(), GatedRequest.AwarenessHistory) as GateDecision.Capped
        ).boundary

    @Composable
    private fun AwarenessBranch() {
        var open by remember { mutableStateOf<Long?>(null) }
        var openApp by remember { mutableStateOf<String?>(null) }
        var view by remember { mutableStateOf(AwarenessView.Today) }
        var picked by remember { mutableStateOf<LocalDate?>(null) }
        var boundaryShown by remember { mutableStateOf<ProBoundary?>(null) }
        val appId = openApp
        when {
            appId != null -> AppOpensScreen(
                view = resolveAppOpens(appId, "Atlas", detail.launches),
                label = "Atlas",
                // Access held, so the App view carries no way in to the
                // education and the route under test is the one this walks.
                usage = AwarenessUsage.Live,
                onTurnOn = {},
            )

            open != null -> SessionDetailScreen(
                detail = detail,
                labelFor = { "Atlas" },
                iconFor = { null },
                onOpenApp = { openApp = it },
                onAppActions = {},
            )

            view == AwarenessView.Week -> AwarenessWeekScreen(
                week = week,
                usage = AwarenessUsage.Ungranted(offersTurnOn = false),
                onPickView = { view = it; picked = null },
                onOpenDay = { picked = it; view = AwarenessView.Today },
                onTurnOnUsage = {},
            )

            else -> AwarenessScreen(
                today = AwarenessToday.Sessions(finished = 1, running = false),
                sessions = sessions,
                day = picked ?: today,
                isToday = (picked ?: today) == today,
                exclusions = Exclusions(),
                onPickView = { view = it; picked = null },
                onOpenSession = { open = it.record.id },
                onSessionActions = {},
                onOpenExclusions = {},
                boundary = boundary,
                onBoundary = { boundaryShown = boundary },
                onBack = {},
            )
        }
        // The tail the one `when` exists for: the dialog renders over whichever
        // branch is showing, exactly as it does on the surface (#177).
        boundaryShown?.let {
            ProBoundaryDialog(boundary = it, onDismiss = { boundaryShown = null })
        }
    }

    private fun setHost() = compose.setContent {
        BodhaTheme {
            // The host's own binding, over the engine's rule (#132).
            var place by remember { mutableStateOf(Place(Surface.Awareness)) }
            BackHandler(enabled = place.surface != Surface.Home) {
                resolveBack(place)?.let { place = it }
            }
            Box(Modifier.fillMaxSize().escapeIsBack()) {
                if (place.surface == Surface.Awareness) AwarenessBranch()
                else ListRow("Open Awareness", onClick = { place = Place(Surface.Awareness) })
            }
        }
    }

    @Test
    fun `Enter opens a session, and Escape leaves for root rather than for the list`() {
        setHost()

        compose.tabTo(ACTIVITY_ROOT, "9:41 · 12 minutes")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertTrue("Session" in compose.drawnTextIn(ACTIVITY_ROOT))
        assertTrue("Atlas" in compose.drawnTextIn(ACTIVITY_ROOT))

        compose.press(ACTIVITY_ROOT, Key.Escape)
        assertEquals(listOf("Open Awareness"), compose.drawnTextIn(ACTIVITY_ROOT))

        // Coming back lands on the list: nothing remembers which session was open.
        compose.tabTo(ACTIVITY_ROOT, "Open Awareness")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertTrue("9:41 · 12 minutes" in compose.drawnTextIn(ACTIVITY_ROOT))
        assertFalse("Atlas" in compose.drawnTextIn(ACTIVITY_ROOT))
    }

    /**
     * The second level, walked the same way (#174). Escape from the App view goes
     * to **root**, not back to the session it was opened from — the cost ADR 0011
     * accepts for refusing a stack, and the reason nothing here is a [Place].
     */
    @Test
    fun `Enter on a launch row opens the app, and Escape leaves for root rather than for the session`() {
        setHost()

        compose.tabTo(ACTIVITY_ROOT, "9:41 · 12 minutes")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        compose.tabTo(ACTIVITY_ROOT, "Atlas")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertTrue("1 open · 1 session" in compose.drawnTextIn(ACTIVITY_ROOT))
        assertFalse("Session" in compose.drawnTextIn(ACTIVITY_ROOT))

        compose.press(ACTIVITY_ROOT, Key.Escape)
        assertEquals(listOf("Open Awareness"), compose.drawnTextIn(ACTIVITY_ROOT))
    }

    /**
     * The switch and the day it hands over (#176). Picking a day opens it in the
     * Today view, and Escape from there leaves for **root** rather than back to
     * the Week — the switch is a view, not a place, so there is nothing to
     * return to and nothing remembering the day once the surface is left.
     */
    @Test
    fun `Enter on a day opens Today for that day, and Escape leaves for root rather than for Week`() {
        setHost()

        compose.tabTo(ACTIVITY_ROOT, "Week")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertTrue("Saturday, 1 August" in compose.drawnTextIn(ACTIVITY_ROOT))

        compose.tabTo(ACTIVITY_ROOT, "Saturday, 1 August")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertTrue("Saturday, 1 August · 1 session" in compose.drawnTextIn(ACTIVITY_ROOT))
        assertFalse("Sunday, 2 August" in compose.drawnTextIn(ACTIVITY_ROOT))

        compose.press(ACTIVITY_ROOT, Key.Escape)
        assertEquals(listOf("Open Awareness"), compose.drawnTextIn(ACTIVITY_ROOT))

        // Coming back lands on Today, on the live day: nothing remembers either.
        compose.tabTo(ACTIVITY_ROOT, "Open Awareness")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertTrue("1 session today" in compose.drawnTextIn(ACTIVITY_ROOT))
    }

    /**
     * The boundary is a control like any other (#177, ADR 0020, ADR 0022): Tab
     * reaches it and Enter opens the shared Pro site behind it. It is named by
     * the gate's own sentence, because that sentence is what the edge of the
     * window renders.
     */
    @Test
    fun `Tab reaches the boundary and Enter opens the gate behind it`() {
        setHost()

        compose.tabTo(ACTIVITY_ROOT, boundary.explanation)
        compose.press(ACTIVITY_ROOT, Key.Enter)

        assertEquals(listOf(boundary.explanation), compose.drawnTextIn(DIALOG_ROOT))

        compose.press(DIALOG_ROOT, Key.Escape)
        assertTrue(boundary.explanation in compose.drawnTextIn(ACTIVITY_ROOT))
    }

    /** ADR 0022: focus, then Enter, for every one of the seven. */
    @Test
    fun `Tab reaches every day row and Enter opens the day it names`() {
        setHost()

        compose.tabTo(ACTIVITY_ROOT, "Week")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        // The last of the seven is the live day, which opens as today rather
        // than as a date — the same day read by the same view.
        compose.tabTo(ACTIVITY_ROOT, "Friday, 7 August")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertTrue("1 session today" in compose.drawnTextIn(ACTIVITY_ROOT))

        // And back out to the Week, which arrives on its own first row.
        compose.tabTo(ACTIVITY_ROOT, "Week")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertEquals("Saturday, 1 August", compose.focusedNameIn(ACTIVITY_ROOT))
    }
}
