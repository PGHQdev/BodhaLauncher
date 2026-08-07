package com.bodhalauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.AwarenessDuration
import com.bodhalauncher.engine.AwarenessUsage
import com.bodhalauncher.engine.EntitlementSnapshot
import com.bodhalauncher.engine.ForegroundEntry
import com.bodhalauncher.engine.LaunchRecord
import com.bodhalauncher.engine.ProBoundary
import com.bodhalauncher.engine.UnavailableReason
import com.bodhalauncher.engine.appOpensSourceLine
import com.bodhalauncher.engine.mergeLaunches
import com.bodhalauncher.engine.resolveAppOpens
import com.bodhalauncher.engine.resolveAwarenessWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Awareness's App view as it renders (#174, #175): one app's opens under the day
 * they fell in, the counts over what is drawn, and a foreground figure that is
 * either a span or the reason there is none — never a 0 filling in for an
 * unknown.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp", application = android.app.Application::class)
class AppOpensScreenTest {

    private companion object {
        val TIME = Regex("""\d{1,2}:\d{2}""")
        val DAY_HEADINGS = listOf("Friday, 7 August", "Thursday, 6 August")
        val SPAN = AwarenessDuration.Span(8_100_000)
        val NO_ACCESS = AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess)
        val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 7, 14, 0)
        /** A month back — outside any free window, inside what retention kept. */
        val OLD: LocalDateTime = LocalDateTime.of(2026, 7, 8, 9, 0)
    }

    @get:Rule
    val compose = createComposeRule()

    private fun nodes(): List<SemanticsNode> {
        val found = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            found += node
            node.children.forEach(::walk)
        }
        walk(compose.onRoot().fetchSemanticsNode())
        return found
    }

    private fun drawnText(): List<String> = nodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
        .map { it.text }

    private fun actionable(): List<SemanticsNode> =
        nodes().filter { SemanticsActions.OnClick in it.config }

    private fun opened(minute: Int, day: Int = 7, hour: Int = 9, session: Long? = 1) =
        LaunchRecord("atlas", LocalDateTime.of(2026, 8, day, hour, minute), session)

    private val launches = listOf(
        opened(minute = 15),
        opened(minute = 30, hour = 21, session = 2),
        opened(minute = 10, day = 6, session = 3),
    )

    private var turnedOn = 0

    private fun setScreen(
        label: String? = "Atlas",
        launches: List<LaunchRecord> = this.launches,
        // Access held by default, so a test that is about the opens draws no way
        // in to the education and does not have to say so.
        usage: AwarenessUsage = AwarenessUsage.Live,
        foreground: AwarenessDuration = SPAN,
        boundary: ProBoundary? = null,
    ) = compose.setContent {
        BodhaTheme {
            AppOpensScreen(
                view = resolveAppOpens("atlas", label, launches, foreground),
                label = label ?: "atlas",
                usage = usage,
                onTurnOn = { turnedOn++ },
                boundary = boundary,
                onBoundary = {},
            )
        }
    }

    /**
     * The App view reads the whole retained log and clamps it here (#177), which
     * is the only reason it can state a boundary at all — a query already
     * narrowed to seven days would have nothing withheld to compare against.
     * Once, at the foot, beneath the opens it cut.
     */
    @Test
    fun `the App view states the boundary once, beneath its opens`() {
        val window = resolveAwarenessWindow(EntitlementSnapshot(proActive = false), NOW)
        val render = window.launches(launches + opened(minute = 0, day = 6).copy(at = OLD))

        setScreen(launches = render.records, boundary = render.boundary)

        val drawn = drawnText()
        // The gate's own sentence, once, and last of everything drawn (#177).
        val terminus = requireNotNull(render.boundary).explanation
        assertEquals(1, drawn.count { it == terminus })
        assertEquals(terminus, drawn.last())
        // The old open is gone from the rows, and the counts are of what renders.
        assertTrue("3 opens · 3 sessions" in drawn)
    }

    @Test
    fun `each open renders under its day with its time, and the line counts them`() {
        setScreen()

        val drawn = drawnText()
        assertTrue("Atlas" in drawn)
        assertTrue("3 opens · 3 sessions" in drawn)
        // Newest day first, and the newest open at the top of its day.
        assertEquals(
            listOf("Friday, 7 August", "21:30", "9:15", "Thursday, 6 August", "9:10"),
            drawn.filter { it in DAY_HEADINGS || it.matches(TIME) },
        )
    }

    /** The record is what Bodha holds; an uninstalled app is named, not dropped. */
    @Test
    fun `an uninstalled app draws its id as the title, says so, and keeps every row`() {
        setScreen(label = null)

        val drawn = drawnText()
        assertTrue("atlas" in drawn)
        assertTrue("No longer installed · 3 opens · 3 sessions" in drawn)
        assertEquals(listOf("21:30", "9:15", "9:10"), drawn.filter { it.matches(TIME) })
    }

    /**
     * With access held the view draws the span, and the opens are both sources'
     * at once (#175): the 9:15 launch Bodha logged, and an 11:00 opening only
     * Android saw. The system's entry four seconds after a logged launch is the
     * same opening and is drawn once.
     */
    @Test
    fun `with usage access the App view draws the span and both sources' opens`() {
        val merged = mergeLaunches(
            appId = "atlas",
            logged = launches,
            entries = listOf(
                ForegroundEntry("atlas", LocalDateTime.of(2026, 8, 7, 9, 15, 4)),
                ForegroundEntry("ledger", LocalDateTime.of(2026, 8, 7, 10, 30)),
                ForegroundEntry("atlas", LocalDateTime.of(2026, 8, 7, 11, 0)),
            ),
        )
        setScreen(launches = merged)

        val drawn = drawnText()
        assertTrue("2 hours 15 minutes in the foreground" in drawn)
        assertTrue("4 opens · 3 sessions" in drawn)
        assertEquals(
            listOf("Friday, 7 August", "21:30", "11:00", "9:15", "Thursday, 6 August", "9:10"),
            drawn.filter { it in DAY_HEADINGS || it.matches(TIME) },
        )
        // Nothing at the foot: with access held there is no absence to name.
        assertEquals(null, appOpensSourceLine(AwarenessUsage.Live))
    }

    /**
     * Both absences named in words, and the one thing neither may be is a 0 —
     * that would say the app was never in front, which is a claim about the
     * reader built out of a gap in Bodha's reach (#175, ADR 0013).
     */
    @Test
    fun `without usage access the App view names the absence and draws no zero`() {
        setScreen(usage = AwarenessUsage.Ungranted(offersTurnOn = true), foreground = NO_ACCESS)

        val drawn = drawnText()
        assertTrue("Foreground time needs usage access" in drawn)
        assertTrue("Opens from notifications and other apps need usage access" in drawn)
        // The launch-log spine is untouched by the absence.
        assertTrue("3 opens · 3 sessions" in drawn)
        for (line in drawn) {
            assertTrue("\"$line\" is a bare zero", line.trim() != "0")
            assertTrue("\"$line\" counts nothing", !line.startsWith("0 "))
        }
    }

    /** No package to look up is no measurement, and says so rather than reporting nothing. */
    @Test
    fun `a work profile app names the profile limit rather than reporting nothing recorded`() {
        setScreen(foreground = AwarenessDuration.Unavailable(UnavailableReason.OtherProfile))

        val drawn = drawnText()
        assertTrue("Foreground time is recorded for the main profile only" in drawn)
        assertTrue("No foreground time recorded" !in drawn)
    }

    /** A revocation names what stopped; the record it did not touch keeps standing. */
    @Test
    fun `a revoked grant names what stopped and leaves the opens standing`() {
        setScreen(usage = AwarenessUsage.Revoked, foreground = NO_ACCESS)

        val drawn = drawnText()
        assertTrue("Foreground time stopped when usage access was turned off" in drawn)
        assertTrue(
            "Opens from notifications and other apps stopped when usage access was turned off" in drawn,
        )
        assertEquals(listOf("21:30", "9:15", "9:10"), drawn.filter { it.matches(TIME) })
        // A revocation is a state to rest on, not a second chance to ask.
        assertEquals(emptyList<String>(), actionable().mapNotNull { it.spokenName() })
    }

    /**
     * The route in is the one flow (#157) and it is entered by a single node —
     * a second would be a second copy of the wiring, which is what #157 exists
     * to prevent.
     */
    @Test
    fun `the turn-on row is one node that enters the education flow once`() {
        setScreen(usage = AwarenessUsage.Ungranted(offersTurnOn = true), foreground = NO_ACCESS)

        val row = actionable().single()
        assertEquals("Foreground time needs usage access", row.spokenName())
        row.config[SemanticsActions.OnClick].action?.invoke()
        assertEquals(1, turnedOn)
        // Rule 3: it opens a sheet, so it navigates nowhere and draws no chevron.
        assertEquals(emptyList<String>(), drawnText().filter { it == "›" })
    }

    /** ADR 0020's floor, on the one actionable node the degraded state publishes. */
    @Test
    fun `the turn-on row is at least 48dp on both axes and named`() {
        setScreen(usage = AwarenessUsage.Ungranted(offersTurnOn = true), foreground = NO_ACCESS)

        val floor = with(compose.density) { TOUCH_TARGET_MIN.roundToPx() }
        val row = actionable().single()
        assertTrue(
            "${row.spokenName()} = ${row.size.width}x${row.size.height}px",
            row.size.width >= floor && row.size.height >= floor,
        )
        assertTrue(!row.spokenName().isNullOrBlank())
    }

    /** ADR 0022: focus, then Enter, and no other way in is needed. */
    @Test
    fun `Tab reaches the turn-on row and Enter fires it`() {
        setScreen(usage = AwarenessUsage.Ungranted(offersTurnOn = true), foreground = NO_ACCESS)

        compose.tabTo(ACTIVITY_ROOT, "Foreground time needs usage access")
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertEquals(1, turnedOn)
    }

    /**
     * A past refusal degrades quietly (#175, ADR 0017): the state is still named
     * in full, and the ask is simply not made a second time.
     */
    @Test
    fun `a declined refusal draws a plain note and no actionable node`() {
        setScreen(usage = AwarenessUsage.Ungranted(offersTurnOn = false), foreground = NO_ACCESS)

        assertTrue("Foreground time needs usage access" in drawnText())
        assertEquals(emptyList<String>(), actionable().mapNotNull { it.spokenName() })
    }

    /** Rule 3: no chevron, because an open row navigates nowhere (ADR 0025). */
    @Test
    fun `an open row is read rather than activated and carries no chevron`() {
        setScreen()

        assertEquals(emptyList<String>(), drawnText().filter { it == "›" })
        assertEquals(emptyList<SemanticsNode>(), actionable())
    }

    /**
     * A read that failed draws the shell and not an empty branch: one named,
     * focusable node, which is the only thing giving Escape a chain to travel up
     * (ADR 0022). Back leaves for root, as it does from every Awareness branch.
     */
    @Test
    fun `a failed read still publishes one focusable named node and Escape leaves for root`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    AppOpensScreen(
                        view = null,
                        label = "Atlas",
                        // A read that failed has no figure to state either, so
                        // the shell says nothing at all rather than blaming a
                        // grant that is held.
                        usage = AwarenessUsage.Live,
                        onTurnOn = {},
                    )
                }
            }
        }

        assertTrue("Atlas" in drawnText())
        assertEquals(
            listOf("Atlas"),
            nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
                .mapNotNull { it.spokenName() },
        )

        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, backs)
    }
}
