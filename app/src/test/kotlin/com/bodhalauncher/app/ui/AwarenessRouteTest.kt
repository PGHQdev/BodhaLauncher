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
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.LaunchRecord
import com.bodhalauncher.engine.Place
import com.bodhalauncher.engine.SessionDetail
import com.bodhalauncher.engine.SessionRecord
import com.bodhalauncher.engine.Surface
import com.bodhalauncher.engine.resolveAppOpens
import com.bodhalauncher.engine.resolveBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
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

    /**
     * Which session is open, and which app inside it, live inside this branch,
     * exactly as they live inside `AwarenessSurface` — so leaving for root is what
     * drops both, and there is no state anywhere for a second back press to walk
     * down. Two levels of drill-down, still no stack.
     */
    @Composable
    private fun AwarenessBranch() {
        var open by remember { mutableStateOf<Long?>(null) }
        var openApp by remember { mutableStateOf<String?>(null) }
        val appId = openApp
        when {
            appId != null -> AppOpensScreen(
                view = resolveAppOpens(appId, "Atlas", detail.launches),
                label = "Atlas",
            )

            open != null -> SessionDetailScreen(
                detail = detail,
                labelFor = { "Atlas" },
                iconFor = { null },
                onOpenApp = { openApp = it },
            )

            else -> AwarenessScreen(
                today = AwarenessToday.Sessions(finished = 1, running = false),
                sessions = sessions,
                onOpenSession = { open = it.record.id },
                onBack = {},
            )
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
}
