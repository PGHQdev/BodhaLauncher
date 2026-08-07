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
import com.bodhalauncher.engine.LaunchRecord
import com.bodhalauncher.engine.SessionDetail
import com.bodhalauncher.engine.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Awareness's Session view as it renders (#173): one session opened up — what it
 * launched and in what order, what was stated in it, and what else it held.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp", application = android.app.Application::class)
class SessionDetailScreenTest {

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

    private val session = AwarenessSession(
        record = SessionRecord(
            id = 1,
            start = LocalDateTime.of(2026, 8, 7, 9, 41),
            end = LocalDateTime.of(2026, 8, 7, 9, 53),
        ),
        intentional = true,
    )

    private fun launch(appId: String, minute: Int) =
        LaunchRecord(appId, LocalDateTime.of(2026, 8, 7, 9, minute), session = 1)

    private fun detail(
        launches: List<LaunchRecord> = listOf(launch("atlas", 42), launch("ledger", 45)),
        checks: Int = 0,
        repeatedOpen: Boolean = false,
        statement: String? = null,
    ) = SessionDetail(session, launches, checks, repeatedOpen, statement)

    private fun setScreen(detail: SessionDetail?) = compose.setContent {
        BodhaTheme {
            SessionDetailScreen(
                detail = detail,
                labelFor = { id -> if (id == "atlas") "Atlas" else id },
                iconFor = { null },
            )
        }
    }

    @Test
    fun `the session's launches render in the order they happened, each with its time`() {
        setScreen(detail())

        val drawn = drawnText()
        assertEquals(listOf("Atlas", "ledger"), drawn.filter { it == "Atlas" || it == "ledger" })
        assertEquals(listOf("9:42", "9:45"), drawn.filter { it.startsWith("9:4") && it.length == 4 })
    }

    /** An app uninstalled since has no name left to give; its id is what Bodha holds. */
    @Test
    fun `an app with no name left falls back to the id rather than to a blank`() {
        setScreen(detail(launches = listOf(launch("com.example.gone", 42))))

        assertTrue("com.example.gone" in drawnText())
    }

    @Test
    fun `a session that opened nothing says so, and renders no launch row`() {
        setScreen(detail(launches = emptyList(), checks = 1))

        assertTrue("Nothing was opened in this session" in drawnText())
        assertTrue("1 Open Check fired" in drawnText())
        assertEquals(emptyList<String>(), drawnText().filter { it == "Atlas" })
    }

    @Test
    fun `what was stated is read back, and the classification is a word`() {
        setScreen(detail(statement = "finish the reading"))

        assertTrue("finish the reading" in drawnText())
        assertTrue("Intentional" in drawnText())
    }

    @Test
    fun `the checks and the repeated open appear only when they happened`() {
        setScreen(detail(checks = 2, repeatedOpen = true))

        assertTrue("2 Open Checks fired" in drawnText())
        assertTrue("A repeated open was noticed" in drawnText())

        compose.runOnIdle { }
    }

    /**
     * The launches are history, not a way to relaunch: the rows publish no click,
     * so neither ADR 0020's floor nor ADR 0022's traversal has one to measure.
     */
    @Test
    fun `the launch rows are read rather than activated`() {
        setScreen(detail())

        assertEquals(
            emptyList<String>(),
            nodes().filter { SemanticsActions.OnClick in it.config }.mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            },
        )
    }

    /** Which is why the surface takes focus itself — Escape travels up from it. */
    @Test
    fun `the surface takes focus on arrival, so back has a key`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    SessionDetailScreen(
                        detail = detail(),
                        labelFor = { it },
                        iconFor = { null },
                    )
                }
            }
        }

        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, backs)
    }

    @Test
    fun `a read that has not landed renders nothing at all`() {
        setScreen(null)

        assertEquals(emptyList<String>(), drawnText())
    }

    @Test
    fun `nothing on the view says unintentional`() {
        setScreen(detail(checks = 2, repeatedOpen = true, statement = "finish the reading"))

        assertTrue(drawnText().none { it.lowercase().contains("unintentional") })
    }
}
