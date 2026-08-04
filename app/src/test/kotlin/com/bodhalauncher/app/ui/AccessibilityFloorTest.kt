package com.bodhalauncher.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The accessibility floor (ADR 0020), asserted by walking the design gallery —
 * the same fixture the screenshot tests photograph. Two clauses, one walk:
 * every node that can be activated is at least [TOUCH_TARGET_MIN] on both axes
 * and carries a name.
 *
 * The gallery rather than the screens, because #26's stated home for these
 * checks — #27's critical-flow Compose UI tests — does not exist. That makes
 * the gallery's contents load-bearing: an actionable component it does not
 * render is a component this test cannot see, which is why the Library's rows,
 * cells and rail are rendered there rather than left private to the screen.
 *
 * What this cannot prove, and so does not claim: that a *screen* composes a
 * compliant component without shrinking it. A 20dp box around a good row still
 * passes here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A real display, or the host window has no height and every node measures 0 tall —
// which would let the touch-target clause fail for a reason that is not the floor.
@Config(sdk = [35], qualifiers = "w411dp-h891dp", application = android.app.Application::class)
class AccessibilityFloorTest {

    @get:Rule
    val compose = createComposeRule()

    private fun actionableNodes(): List<SemanticsNode> {
        val found = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            val activatable = SemanticsActions.OnClick in node.config ||
                SemanticsActions.OnLongClick in node.config ||
                SemanticsActions.CustomActions in node.config
            if (activatable) found += node
            node.children.forEach(::walk)
        }
        walk(compose.onRoot().fetchSemanticsNode())
        return found
    }

    /** Whatever a screen reader would read for this node, or null if it would read nothing. */
    private fun SemanticsNode.spokenName(): String? =
        config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

    private fun setGallery() = compose.setContent { BodhaTheme { DesignGallery() } }

    @Test
    fun `the gallery renders actionable components for the walk to find`() {
        setGallery()
        // Guards the test itself: an empty walk would pass both clauses vacuously,
        // which is exactly how this check could rot into a no-op.
        assertTrue(
            "the gallery must render actionable components, or this suite proves nothing",
            actionableNodes().size >= 4,
        )
    }

    @Test
    fun `every actionable node meets the touch-target floor on both axes`() {
        setGallery()
        val floorPx = with(compose.density) { TOUCH_TARGET_MIN.roundToPx() }
        val undersized = actionableNodes().filter { node ->
            node.size.width < floorPx || node.size.height < floorPx
        }
        assertEquals(
            undersized.map { "${it.spokenName()} = ${it.size.width}x${it.size.height}px" },
            emptyList<String>(),
        )
    }

    @Test
    fun `every actionable node carries a name`() {
        setGallery()
        val unnamed = actionableNodes().filter { it.spokenName().isNullOrBlank() }
        assertEquals(unnamed.map { it.size.toString() }, emptyList<String>())
    }

    /**
     * The rail is the floor's hard case (ADR 0020): ~27 slots over ~700dp gives
     * ~26dp each, so per-letter nodes cannot meet the floor and no phone is tall
     * enough for 27 that do. One named node with a custom action per letter is
     * the resolution, and each action must actually jump.
     */
    @Test
    fun `the letter rail is one named node with an action per letter`() {
        val jumped = mutableListOf<Int>()
        val index = listOf('A', 'F', 'M').mapIndexed { i, c ->
            com.bodhalauncher.engine.LibraryIndexEntry(letter = c, firstRow = i * 10)
        }
        compose.setContent {
            BodhaTheme { AlphabetScrubber(index = index, onJump = { jumped += it }) }
        }

        val rail = actionableNodes().single { it.spokenName() == RAIL_LABEL }
        val actions = rail.config[SemanticsActions.CustomActions]
        assertEquals(listOf("A", "F", "M"), actions.map { it.label })

        actions.first { it.label == "M" }.action()
        assertEquals(listOf(20), jumped)
    }

    /** The letters must not be focusable in their own right, or the rail reads as characters. */
    @Test
    fun `the rail's letters are not separately focusable`() {
        val index = listOf('A', 'F', 'M').mapIndexed { i, c ->
            com.bodhalauncher.engine.LibraryIndexEntry(letter = c, firstRow = i)
        }
        compose.setContent {
            BodhaTheme { AlphabetScrubber(index = index, onJump = {}) }
        }

        val texts = mutableListOf<String>()
        fun collect(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
            node.children.forEach(::collect)
        }
        collect(compose.onRoot().fetchSemanticsNode())

        assertEquals(emptyList<String>(), texts)
    }
}
