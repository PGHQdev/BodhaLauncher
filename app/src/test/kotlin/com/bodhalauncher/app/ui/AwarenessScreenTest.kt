package com.bodhalauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.AwarenessView
import com.bodhalauncher.engine.EntitlementSnapshot
import com.bodhalauncher.engine.Exclusions
import com.bodhalauncher.engine.FREE_AWARENESS_DAYS
import com.bodhalauncher.engine.GateDecision
import com.bodhalauncher.engine.GatedRequest
import com.bodhalauncher.engine.ProBoundary
import com.bodhalauncher.engine.SessionRecord
import com.bodhalauncher.engine.awarenessWindowTerminusLine
import com.bodhalauncher.engine.resolveAwarenessSessions
import com.bodhalauncher.engine.resolveAwarenessWindow
import com.bodhalauncher.engine.resolveEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Awareness's Today view as it renders (#172): the day's sessions in time order,
 * each classified by a word, and neither a zero nor a judgement anywhere on it.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp", application = android.app.Application::class)
class AwarenessScreenTest {

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

    private fun actionableNames(): List<String> = nodes()
        .filter { SemanticsActions.OnClick in it.config }
        .mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }

    private fun session(id: Long, from: Int, to: Int?, intentional: Boolean) = AwarenessSession(
        record = SessionRecord(
            id = id,
            start = LocalDateTime.of(2026, 8, 7, 9, from),
            end = to?.let { LocalDateTime.of(2026, 8, 7, 9, it) },
        ),
        intentional = intentional,
    )

    private val day = listOf(
        session(1, 12, 14, intentional = false),
        session(2, 41, 53, intentional = true),
        session(3, 58, null, intentional = false),
    )

    private val opened = mutableListOf<Long>()
    private val picked = mutableListOf<AwarenessView>()
    private val actioned = mutableListOf<Long>()
    private var stated = 0
    private var exclusionsOpened = 0

    /** The gate's own copy, read from the gate so a copy edit lands here too. */
    private val gateCopy = (
        resolveEntitlement(EntitlementSnapshot(), GatedRequest.AwarenessHistory) as GateDecision.Capped
        ).boundary
    private val terminus = awarenessWindowTerminusLine(FREE_AWARENESS_DAYS)

    private fun setScreen(
        sessions: List<AwarenessSession>,
        today: AwarenessToday?,
        day: LocalDate = LocalDate.of(2026, 8, 7),
        isToday: Boolean = true,
        boundary: ProBoundary? = null,
        exclusions: Exclusions = Exclusions(),
    ) = compose.setContent {
        BodhaTheme {
            AwarenessScreen(
                today = today,
                sessions = sessions,
                day = day,
                isToday = isToday,
                exclusions = exclusions,
                onPickView = { picked += it },
                onOpenSession = { opened += it.record.id },
                onSessionActions = { actioned += it.record.id },
                onOpenExclusions = { exclusionsOpened += 1 },
                boundary = boundary,
                boundaryTitle = awarenessWindowTerminusLine(FREE_AWARENESS_DAYS),
                onBoundary = { stated += 1 },
                onBack = {},
            )
        }
    }

    @Test
    fun `the day's sessions render in time order, each with its span and its word`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        val drawn = drawnText()
        assertEquals(
            listOf("9:12 · 2 minutes", "9:41 · 12 minutes", "9:58 · running now"),
            drawn.filter { it.startsWith("9:") },
        )
        assertEquals(
            listOf("Unclassified", "Intentional", "Unclassified"),
            drawn.filter { it == "Intentional" || it == "Unclassified" },
        )
    }

    @Test
    fun `a day with no sessions names its absence rather than showing a zero`() {
        setScreen(emptyList(), AwarenessToday.None)

        assertTrue("No sessions yet today" in drawnText())
        assertTrue(drawnText().none { it == "0" })
    }

    @Test
    fun `a read that has not landed renders no count at all`() {
        setScreen(emptyList(), today = null)

        assertEquals(listOf("Awareness"), drawnText())
    }

    /**
     * Every row opens its own session (#173) — one actionable node each, no
     * more. The two switch pills are the only other actionable nodes on the
     * view (#176), and they are named by the views they choose.
     */
    @Test
    fun `each session row opens that session`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        assertEquals(
            listOf("Today", "Week", "9:12 · 2 minutes", "9:41 · 12 minutes", "9:58 · running now"),
            actionableNames(),
        )
        compose.onNodeWithText("9:41 · 12 minutes").performClick()
        assertEquals(listOf(2L), opened)
    }

    /**
     * A day handed over by the Week view is read here, and the line says which
     * day it is: "6 sessions today" over Tuesday's records would be false (#176).
     */
    @Test
    fun `a picked past day names its date rather than saying today`() {
        setScreen(
            sessions = day,
            today = AwarenessToday.Sessions(finished = 3, running = false),
            day = LocalDate.of(2026, 8, 4),
            isToday = false,
        )

        val drawn = drawnText()
        assertTrue("Tuesday, 4 August · 3 sessions" in drawn)
        assertTrue(drawn.none { it.endsWith("today") })
    }

    @Test
    fun `the live day's line still says today`() {
        setScreen(day, AwarenessToday.Sessions(finished = 3, running = false))

        assertTrue("3 sessions today" in drawnText())
    }

    /** Picking the other view is one press on a named, tinted pill (ADR 0025 rule 2). */
    @Test
    fun `the switch offers both views and reports exactly one as current`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        assertEquals(
            listOf("Today" to true, "Week" to false),
            nodes().filter { SemanticsProperties.Selected in it.config }
                .map { it.spokenName() to it.config[SemanticsProperties.Selected] },
        )
        compose.onNodeWithText("Week").performClick()
        assertEquals(listOf(AwarenessView.Week), picked)
    }

    /**
     * One node, so a reader hears a row rather than two loose strings: the click
     * merges the span and the word, which is what the inert row of #172 needed a
     * hand-written description for.
     */
    @Test
    fun `a session row is named by its span and its word together`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        val rows = nodes()
            .filter { SemanticsActions.OnClick in it.config }
            .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        assertTrue(rows.any { it.take(2) == listOf("9:41 · 12 minutes", "Intentional") })
        assertTrue(rows.any { it.take(2) == listOf("9:58 · running now", "Unclassified") })
    }

    /** Rule 3: the chevron is on the row because the row navigates (ADR 0025). */
    @Test
    fun `every session row wears the navigate marker`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        assertEquals(3, drawnText().count { it == "›" })
    }

    /**
     * The first row takes focus on arrival, which is what gives Escape a chain to
     * travel up (ADR 0022). Arrived at by key rather than composed already-open,
     * because that is the only sequence in which arrival focus can happen at all:
     * a row is `clickable`, so it is focusable in non-touch mode only.
     */
    @Test
    fun `the first row takes focus on arrival, which gives the list a back key`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                var open by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    if (open) {
                        AwarenessScreen(
                            today = AwarenessToday.Sessions(finished = 2, running = true),
                            sessions = day,
                            day = LocalDate.of(2026, 8, 7),
                            isToday = true,
                            exclusions = Exclusions(),
                            onPickView = {},
                            onOpenSession = {},
                            onSessionActions = {},
                            onOpenExclusions = {},
                            onBack = {},
                        )
                    } else {
                        ListRow("Open Awareness", onClick = { open = true })
                    }
                }
            }
        }

        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertEquals(
            "9:12 · 2 minutes",
            nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
                ?.config?.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text,
        )

        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, backs)
    }

    /**
     * At the edge of the window, once, and beneath everything it clamped (#177).
     * Not on a row, because the reader crossed the edge once and marking every
     * row would be the same sentence repeated down the screen.
     */
    @Test
    fun `the boundary is stated once, beneath the rows, and on no row`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true), boundary = gateCopy)

        val drawn = drawnText()
        assertEquals(1, drawn.count { it == terminus })
        // Last of everything drawn, after the final session row.
        assertEquals(terminus, drawn.last())
        val rows = nodes()
            .filter { SemanticsActions.OnClick in it.config }
            .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        assertTrue(rows.none { it.size > 1 && terminus in it })
    }

    /**
     * A view that stopped for want of records has nothing true to say about Pro,
     * so nothing is said. A permanent line naming what Pro costs on a screen that
     * lost nothing is an upsell (#177, ADR 0005).
     */
    @Test
    fun `no boundary renders when the gate withheld nothing`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        assertTrue(drawnText().none { it == terminus })
        assertTrue(drawnText().none { it == gateCopy.explanation })
    }

    /**
     * Face is decided by authorship (ADR 0021, CONTEXT.md **Voice**). The row
     * names what happened to the list — machinery, in the sans a `CardRow` title
     * takes — and the sentence Bodha wrote stays in the dialog, where it is
     * already faced as voice.
     */
    @Test
    fun `the terminus row is machinery and the authored sentence stays in the dialog`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true), boundary = gateCopy)

        assertTrue(terminus in drawnText())
        assertTrue(drawnText().none { it == gateCopy.explanation })
        // One actionable node, named by the terminus, and never phrased as a loss.
        val row = nodes().single { SemanticsActions.OnClick in it.config && it.spokenName() == terminus }
        row.config[SemanticsActions.OnClick].action?.invoke()
        assertEquals(1, stated)
    }

    /** ADR 0020: the terminus is a control, so it owes the floor on both axes. */
    @Test
    fun `the boundary is one named node at the touch floor on both axes`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true), boundary = gateCopy)

        val floor = with(compose.density) { TOUCH_TARGET_MIN.roundToPx() }
        val row = nodes().single { SemanticsActions.OnClick in it.config && it.spokenName() == terminus }
        assertTrue(
            "${row.size.width}x${row.size.height}px",
            row.size.width >= floor && row.size.height >= floor,
        )
        // Rule 3: it opens a dialog rather than navigating, so no chevron.
        assertEquals(3, drawnText().count { it == "›" })
    }

    /**
     * A Pro flip is a recomposition over the list already in hand, and that whole
     * claim rests on `window` being a `remember` key where the resolution happens
     * (#177). There is no `AwarenessSurfaceTest`, so the key list is held here or
     * by nothing: the same records, one snapshot flip, a wider render and no
     * second read anywhere in the harness.
     */
    @Test
    fun `the window is a remember key, so a flipped snapshot re-resolves`() {
        val records = listOf(
            SessionRecord(
                id = 1,
                start = LocalDateTime.of(2026, 7, 8, 9, 12),
                end = LocalDateTime.of(2026, 7, 8, 9, 14),
            ),
            SessionRecord(
                id = 2,
                start = LocalDateTime.of(2026, 8, 7, 9, 41),
                end = LocalDateTime.of(2026, 8, 7, 9, 53),
            ),
        )
        val now = LocalDateTime.of(2026, 8, 7, 14, 0)
        compose.setContent {
            BodhaTheme {
                var snapshot by remember { mutableStateOf(EntitlementSnapshot(proActive = false)) }
                val window = resolveAwarenessWindow(snapshot, now)
                val render = remember(records, window) { window.sessions(records) }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        AwarenessScreen(
                            today = AwarenessToday.Sessions(render.records.size, running = false),
                            sessions = resolveAwarenessSessions(render.records, emptyList()),
                            day = LocalDate.of(2026, 8, 7),
                            isToday = true,
                            exclusions = Exclusions(),
                            onPickView = {},
                            onOpenSession = {},
                            onSessionActions = {},
                            onOpenExclusions = {},
                            boundary = render.boundary,
                            boundaryTitle = awarenessWindowTerminusLine(window.cap),
                            onBoundary = {},
                            onBack = {},
                        )
                    }
                    ListRow("Pro", onClick = { snapshot = EntitlementSnapshot(proActive = true) })
                }
            }
        }

        assertTrue("9:41 · 12 minutes" in drawnText())
        assertTrue("9:12 · 2 minutes" !in drawnText())
        assertTrue(terminus in drawnText())

        compose.onNodeWithText("Pro").performClick()

        // The older record renders from the same list, with no read in between —
        // and the boundary goes with it, because nothing is withheld any more.
        assertTrue("9:12 · 2 minutes" in drawnText())
        assertTrue(terminus !in drawnText())
    }

    /**
     * The route to the undo (#178): one row at the foot of the list, naming what
     * is currently taken out. It navigates, so it wears the chevron (ADR 0025
     * rule 3), and it is not there at all where nothing is excluded — a control
     * for undoing nothing under every day the reader excluded nothing from.
     */
    @Test
    fun `the excluded row names what is taken out and opens the list`() {
        setScreen(
            day,
            AwarenessToday.Sessions(finished = 2, running = true),
            exclusions = Exclusions(apps = setOf("atlas"), sessions = setOf(9)),
        )

        assertTrue("Excluded" in drawnText())
        assertTrue("1 app · 1 session" in drawnText())
        // Rule 3: it navigates, so it takes the fourth chevron on the view.
        assertEquals(4, drawnText().count { it == "›" })

        compose.onNodeWithText("Excluded").performClick()
        assertEquals(1, exclusionsOpened)
    }

    @Test
    fun `no excluded row renders where nothing is excluded`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        assertTrue(drawnText().none { it == "Excluded" })
    }

    /**
     * A day with nothing on it and something excluded still has a row, so arrival
     * lands on it rather than on the switch — the chain-wide rule read in order
     * (ADR 0022, #176).
     */
    @Test
    fun `a quiet day with an exclusion still renders a row, and the count is of the day`() {
        setScreen(
            emptyList(),
            AwarenessToday.None,
            exclusions = Exclusions(sessions = setOf(9)),
        )

        assertTrue("No sessions yet today" in drawnText())
        assertTrue("1 session" in drawnText())
        assertTrue(drawnText().none { it == "0" })
    }

    /**
     * The exclusion is offered on the row's own actions and nowhere else (#178,
     * ADR 0022, ADR 0023): the row keeps its one click for opening the session,
     * and long-press is what reaches the sheet.
     */
    @Test
    fun `a session row's actions are its own, and its click still opens it`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        val row = nodes().single {
            SemanticsActions.OnClick in it.config && it.spokenName() == "9:41 · 12 minutes"
        }
        row.config[SemanticsActions.OnLongClick]?.action?.invoke()
        assertEquals(listOf(2L), actioned)

        row.config[SemanticsActions.OnClick].action?.invoke()
        assertEquals(listOf(2L), opened)
    }

    @Test
    fun `nothing on the surface says unintentional`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        assertTrue(drawnText().none { it.lowercase().contains("unintentional") })
    }
}
