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
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
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

    private fun setScreen(sessions: List<AwarenessSession>, today: AwarenessToday?) =
        compose.setContent {
            BodhaTheme {
                AwarenessScreen(today = today, sessions = sessions, onBack = {})
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
     * The rows are read, not activated: nothing opens a session until #173, and a
     * named action that only reports its own absence is worse than none. So the
     * list contributes no actionable node, and neither ADR 0020's floor nor ADR
     * 0022's traversal has anything to measure here.
     */
    @Test
    fun `a session row carries no action`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        assertEquals(emptyList<String>(), actionableNames())
    }

    /** Which is why the surface takes focus itself — Escape travels up from it. */
    @Test
    fun `the surface takes focus on arrival, which gives the list a back key`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    AwarenessScreen(
                        today = AwarenessToday.Sessions(finished = 2, running = true),
                        sessions = day,
                        onBack = {},
                    )
                }
            }
        }

        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, backs)
    }

    @Test
    fun `nothing on the surface says unintentional`() {
        setScreen(day, AwarenessToday.Sessions(finished = 2, running = true))

        assertTrue(drawnText().none { it.lowercase().contains("unintentional") })
    }
}
