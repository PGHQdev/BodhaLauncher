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
import com.bodhalauncher.engine.LaunchRecord
import com.bodhalauncher.engine.resolveAppOpens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Awareness's App view as it renders (#174): one app's opens under the day they
 * fell in, the counts over what is drawn, and nothing claiming how long the app
 * was in front.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp", application = android.app.Application::class)
class AppOpensScreenTest {

    private companion object {
        val TIME = Regex("""\d{1,2}:\d{2}""")
        val DAY_HEADINGS = listOf("Friday, 7 August", "Thursday, 6 August")
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

    private fun opened(minute: Int, day: Int = 7, hour: Int = 9, session: Long? = 1) =
        LaunchRecord("atlas", LocalDateTime.of(2026, 8, day, hour, minute), session)

    private val launches = listOf(
        opened(minute = 15),
        opened(minute = 30, hour = 21, session = 2),
        opened(minute = 10, day = 6, session = 3),
    )

    private fun setScreen(
        label: String? = "Atlas",
        launches: List<LaunchRecord> = this.launches,
    ) = compose.setContent {
        BodhaTheme {
            AppOpensScreen(
                view = resolveAppOpens("atlas", label, launches),
                label = label ?: "atlas",
            )
        }
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
     * Foreground time needs usage access and arrives with it (#175). Nothing here
     * may stand in for it — least of all a 0, which would be a claim rather than
     * the absence of one.
     */
    @Test
    fun `no drawn string on the App view is a duration`() {
        setScreen()

        val durationWords = listOf("minute", "hour", "second", "foreground", "screen time")
        for (line in drawnText()) {
            for (word in durationWords) {
                assertTrue(
                    "\"$line\" reads as a duration before #175 can measure one",
                    !line.lowercase().contains(word),
                )
            }
            assertTrue("\"$line\" is a bare zero", line.trim() != "0")
        }
    }

    /** Rule 3: no chevron, because an open row navigates nowhere (ADR 0025). */
    @Test
    fun `an open row is read rather than activated and carries no chevron`() {
        setScreen()

        assertEquals(emptyList<String>(), drawnText().filter { it == "›" })
        assertEquals(
            emptyList<SemanticsNode>(),
            nodes().filter { SemanticsActions.OnClick in it.config },
        )
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
                    AppOpensScreen(view = null, label = "Atlas")
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
