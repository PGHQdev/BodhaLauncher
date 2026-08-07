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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.Exclusions
import com.bodhalauncher.engine.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * The exclusions list as it renders (#178): everything currently taken out of
 * Awareness, one press each to put it back.
 *
 * It is the reason an exclusion survives the disappearance of the row that made
 * it — the row is gone by definition, so without this screen there is nowhere
 * left to undo from.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp", application = android.app.Application::class)
class ExclusionsScreenTest {

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

    private fun record(id: Long, day: Int, minute: Int) = SessionRecord(
        id = id,
        start = LocalDateTime.of(2026, 8, day, 9, minute),
        end = LocalDateTime.of(2026, 8, day, 9, minute + 12),
    )

    private val includedApps = mutableListOf<String>()
    private val includedSessions = mutableListOf<Long>()

    private fun setScreen(
        apps: List<String> = listOf("atlas", "com.example.gone"),
        sessions: List<SessionRecord> = listOf(record(41, day = 7, minute = 41)),
        exclusions: Exclusions = Exclusions(
            apps = apps.toSet(),
            sessions = sessions.map { it.id }.toSet(),
        ),
    ) = compose.setContent {
        BodhaTheme {
            ExclusionsScreen(
                apps = apps,
                sessions = sessions,
                exclusions = exclusions,
                labelFor = { id -> if (id == "atlas") "Atlas" else id },
                iconFor = { null },
                onIncludeApp = { includedApps += it },
                onIncludeSession = { includedSessions += it },
            )
        }
    }

    @Test
    fun `every excluded app and session is one named actionable row`() {
        setScreen()

        assertEquals(
            listOf("Atlas", "com.example.gone", "Friday, 7 August · 9:41 · 12 minutes"),
            actionable().mapNotNull { it.spokenName() },
        )
        assertEquals(3, drawnText().count { it == "Include" })
        // Every row acts in place, so none of them wears the navigate marker.
        assertTrue(drawnText().none { it == "›" })
    }

    @Test
    fun `tapping an app row includes it, by the id the store is keyed by`() {
        setScreen()

        compose.onNodeWithText("Atlas").performClick()
        assertEquals(listOf("atlas"), includedApps)
    }

    @Test
    fun `tapping a session row includes it, by the id the record holds`() {
        setScreen()

        compose.onNodeWithText("Friday, 7 August · 9:41 · 12 minutes").performClick()
        assertEquals(listOf(41L), includedSessions)
    }

    /**
     * Two sessions at the same hour on different days are two rows a reader can
     * tell apart, which is what the date on the line is for.
     */
    @Test
    fun `two sessions from different days render as two distinguishable rows`() {
        setScreen(
            apps = emptyList(),
            sessions = listOf(record(41, day = 6, minute = 41), record(42, day = 7, minute = 41)),
        )

        assertEquals(
            listOf(
                "Thursday, 6 August · 9:41 · 12 minutes",
                "Friday, 7 August · 9:41 · 12 minutes",
            ),
            actionable().mapNotNull { it.spokenName() },
        )
    }

    /**
     * A prune can empty the list while the reader is looking at it. What renders
     * then is the named absence rather than a blank screen — and the surface
     * itself is focusable, so Escape still has a chain (ADR 0022).
     */
    @Test
    fun `an empty list renders the named absence rather than a blank screen`() {
        setScreen(apps = emptyList(), sessions = emptyList())

        assertEquals(listOf("Excluded", "Nothing is excluded"), drawnText())
        assertEquals(emptyList<String>(), actionable().mapNotNull { it.spokenName() })
    }

    /**
     * An id retention has taken the record for has no row. The line above still
     * counts it, because it is still excluded — the prune is what reconciles the
     * two, and it runs off this very read.
     */
    @Test
    fun `an excluded session with no record left is not listed`() {
        setScreen(
            apps = emptyList(),
            sessions = emptyList(),
            exclusions = Exclusions(sessions = setOf(41L, 42L)),
        )

        assertEquals(listOf("Excluded", "2 sessions"), drawnText())
        assertEquals(emptyList<String>(), actionable().mapNotNull { it.spokenName() })
    }

    @Test
    fun `each row publishes exactly one named actionable node at the touch floor`() {
        setScreen()

        val floor = with(compose.density) { TOUCH_TARGET_MIN.roundToPx() }
        assertEquals(3, actionable().size)
        actionable().forEach { row ->
            assertTrue(row.spokenName() != null)
            assertTrue(
                "${row.size.width}x${row.size.height}px",
                row.size.width >= floor && row.size.height >= floor,
            )
        }
    }

    /**
     * The state this screen exists for arrives with its first row focused, which
     * is what gives Escape a chain to travel up (ADR 0022's build amendment).
     * Arrived at by key rather than composed already-open, because a row is
     * focusable in non-touch mode only.
     */
    @Test
    fun `the list arrives with its first row focused, and Escape leaves for root`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                var open by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    if (open) {
                        ExclusionsScreen(
                            apps = listOf("atlas"),
                            sessions = listOf(record(41, day = 7, minute = 41)),
                            exclusions = Exclusions(apps = setOf("atlas"), sessions = setOf(41L)),
                            labelFor = { "Atlas" },
                            iconFor = { null },
                            onIncludeApp = {},
                            onIncludeSession = {},
                        )
                    } else {
                        ListRow("Open exclusions", onClick = { open = true })
                    }
                }
            }
        }

        compose.press(ACTIVITY_ROOT, Key.Tab)
        compose.press(ACTIVITY_ROOT, Key.Enter)
        assertEquals("Atlas", compose.focusedNameIn(ACTIVITY_ROOT))

        compose.press(ACTIVITY_ROOT, Key.Escape)
        assertEquals(1, backs)
    }
}
