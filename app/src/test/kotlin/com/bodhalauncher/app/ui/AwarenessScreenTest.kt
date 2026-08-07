package com.bodhalauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import com.bodhalauncher.engine.SessionRecord
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

    private fun setScreen(
        sessions: List<AwarenessSession>,
        today: AwarenessToday?,
        day: LocalDate = LocalDate.of(2026, 8, 7),
        isToday: Boolean = true,
    ) = compose.setContent {
        BodhaTheme {
            AwarenessScreen(
                today = today,
                sessions = sessions,
                day = day,
                isToday = isToday,
                onPickView = { picked += it },
                onOpenSession = { opened += it.record.id },
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
                            onPickView = {},
                            onOpenSession = {},
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

    @Test
    fun `nothing on the surface says unintentional`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        assertTrue(drawnText().none { it.lowercase().contains("unintentional") })
    }
}
