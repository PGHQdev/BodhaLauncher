package com.bodhalauncher.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.LibraryIndexEntry
import com.bodhalauncher.engine.OpenCheckLines
import com.bodhalauncher.engine.SearchInputs
import com.bodhalauncher.engine.resolveSearch
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
 * Fixtures rather than screens, because #26's stated home for these checks —
 * #27's critical-flow Compose UI tests — does not exist. That makes the fixture
 * set load-bearing: a component none of them renders is a component this test
 * cannot see, which is why the Library's rows, cells, rail and search field are
 * rendered in the gallery rather than left private to the screen, and why the
 * two sheets that already have fixtures are walked too.
 *
 * Text fields earn their place in that set: Compose gives a [BasicTextField]
 * click semantics, so it is an actionable node, and its own contents are not a
 * name — an unlabelled field reads as an edit box for nothing in particular.
 *
 * What this cannot prove, and so does not claim: that a *screen* composes a
 * compliant component without shrinking it. A 20dp box around a good row still
 * passes here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Tall enough for all the fixtures stacked. Nothing here scrolls, so anything past
// the window's height measures 0 tall and the touch-target clause fails for a reason
// that is not the floor. If this suite reports 0-height nodes, the fixtures outgrew
// the display — raise this, don't relax the assertion.
// Raised for #136–#139's specimens (the multi-select rows and four onboarding steps).
@Config(sdk = [35], qualifiers = "w411dp-h7000dp", application = android.app.Application::class)
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

    /** One composition: the rule allows a single setContent per test. */
    private fun setFixtures() = compose.setContent { BodhaTheme { AllFixtures() } }

    @Test
    fun `the fixtures render actionable components for the walk to find`() {
        setFixtures()
        // Guards the test itself: an empty walk would pass both clauses vacuously,
        // which is exactly how this check could rot into a no-op.
        assertTrue(
            "the fixtures must render actionable components, or this suite proves nothing",
            actionableNodes().size >= 12,
        )    }

    @Test
    fun `every actionable node meets the touch-target floor on both axes`() {
        setFixtures()
        val floorPx = with(compose.density) { TOUCH_TARGET_MIN.roundToPx() }
        val undersized = actionableNodes()
            .filter { it.size.width < floorPx || it.size.height < floorPx }
            .map { "${it.spokenName()} = ${it.size.width}x${it.size.height}px" }
        assertEquals(emptyList<String>(), undersized)
    }

    @Test
    fun `every actionable node carries a name`() {
        setFixtures()
        val unnamed = actionableNodes()
            .filter { it.spokenName().isNullOrBlank() }
            .map { "${it.size.width}x${it.size.height}px node" }
        assertEquals(emptyList<String>(), unnamed)
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
        compose.setContent {
            BodhaTheme { AlphabetScrubber(index = RAIL_INDEX, onJump = { jumped += it }) }
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
        compose.setContent {
            BodhaTheme { AlphabetScrubber(index = RAIL_INDEX, onJump = {}) }
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

/** Three letters is enough to prove one action each; firstRow strides by 10 so a jump is unambiguous. */
private val RAIL_INDEX = listOf('A', 'F', 'M')
    .mapIndexed { i, letter -> LibraryIndexEntry(letter = letter, firstRow = i * 10) }

/**
 * Every fixture that exists, composed together. The gallery holds the shared
 * components; the two sheets are here because they already had fixtures, and a
 * sheet is where several text fields and footer actions actually live.
 *
 * Search comes as a whole screen rather than as a gallery specimen: its field is
 * its own and not a shared component (#180), so the gallery — which holds the
 * roster, not the screens — is the wrong place for it, and this walk is the guard
 * it must not escape.
 */
@Composable
private fun AllFixtures() {
    Column {
        DesignGallery()
        OpenCheckSheetContent(
            appLabel = "Instagram",
            icon = null,
            lines = OpenCheckLines(
                lastOpened = "Last opened 8 minutes ago",
                usedToday = "Used 34 minutes today",
            ),
            onContextNoteTap = {},
            onOpen = {},
            onGoBack = {},
        )
        SessionEndSheetContent(
            phrase = "Your 10 minutes are complete.",
            onClose = {},
            onAddFive = {},
            onContinue = {},
        )
        // Last and height-bounded: SearchScreen fills what it is given, and what
        // is above it must keep the size it measured.
        Box(Modifier.height(SEARCH_FIXTURE_HEIGHT)) {
            SearchScreen(
                state = resolveSearch(SearchInputs(apps = listOf(SEARCH_APP), query = SEARCH_QUERY)),
                query = SEARCH_QUERY,
                onQueryChange = {},
                iconFor = { null },
                iconKey = Unit,
                onOpen = {},
            )
        }
    }
}

/** Enough for the field, the section overline and the one row beneath, which is all this fixture needs. */
private val SEARCH_FIXTURE_HEIGHT = 300.dp

/** Matches at a word boundary, so the fixture draws a result row as well as the field. */
private const val SEARCH_QUERY = "gallery"

private val SEARCH_APP = HomeAction(id = "fixture.search.app", label = "Gallery app")
