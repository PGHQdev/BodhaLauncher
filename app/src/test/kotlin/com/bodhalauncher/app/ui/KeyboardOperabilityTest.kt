package com.bodhalauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.HomeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Keyboard operability (ADR 0022), asserted by injecting the keys.
 *
 * The guard is the **tab traversal, walked**: extending ADR 0020's tree-walk with
 * "every node carrying `OnClick` is focusable" would be nearly vacuous, since
 * `clickable` confers focus and the real failure mode is a `pointerInput` that
 * publishes no node for a walk to see. Tab counts what a docked user can actually
 * get to, and it is compared against what ADR 0020's walk counts — clicks *and*
 * custom actions — so a gesture handed only a custom action fails here loudly.
 *
 * It runs over the design gallery for the reason every other guard does: a
 * component the fixture cannot see is a component nothing here covers.
 *
 * **Default graphics mode, deliberately.** Compose focus traversal does not run
 * under `@GraphicsMode(NATIVE)` — the same reason the goldens cannot photograph
 * real focus and use [LocalForceFocusRing] instead. This suite needs no pixels.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp", application = android.app.Application::class)
class KeyboardOperabilityTest {

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

    private fun SemanticsNode.name(): String? =
        config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

    /** Every string drawn anywhere. A clickable row merges its children, so a
     * label that is not the row's own first line only shows up here. */
    private fun drawnText(): List<String> = nodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
        .map { it.text }

    /**
     * Everything a touch or screen-reader user can activate — the same set ADR
     * 0020's walk measures. `OnClick` alone would miss the failure this guard
     * exists for: a gesture handler publishes no clickable node, so it would be
     * absent from both counts and the comparison would hold vacuously. It does
     * carry a custom action, because ADR 0020 requires one, and that is the
     * handle by which Tab's count can fall short.
     */
    private fun actionableNames() = nodes()
        .filter {
            SemanticsActions.OnClick in it.config || SemanticsActions.CustomActions in it.config
        }
        .mapNotNull { it.name() }

    private fun focusedName(): String? = nodes()
        .filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
        .mapNotNull { it.name() }
        .firstOrNull()

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    /** Every name Tab lands on, over [presses] stops — a set, so a wrap repeats nothing. */
    private fun tabbedNames(presses: Int): Set<String> {
        val reached = mutableSetOf<String>()
        repeat(presses) {
            press(Key.Tab)
            focusedName()?.let { reached += it }
        }
        return reached
    }

    @Test
    fun `tab reaches every actionable node in the gallery`() {
        compose.setContent { BodhaTheme { DesignGallery() } }

        // Two names are out, each for a stated reason rather than to make the
        // count come out. The actions node is what Right reaches and Tab must
        // not, which is what keeps the Library one stop per app; the rail is out
        // of the keyboard's reach by design, because a docked user types "m"
        // into the search field faster than they could scrub (ADR 0022). Both
        // are asserted below rather than merely dropped.
        val excluded = setOf(ACTIONS_LABEL, RAIL_LABEL)
        val expected = actionableNames().toSet() - excluded
        assertTrue("the fixture must render actionable nodes", expected.size >= 12)

        val reached = tabbedNames(expected.size * 2)
        assertEquals(emptySet<String>(), expected - reached)
        assertTrue("the rail is not a tab stop", RAIL_LABEL !in reached)
    }

    @Test
    fun `the gestures are reachable only because they are focus-revealed`() {
        compose.setContent { BodhaTheme { DesignGallery() } }

        assertEquals(emptySet<String>(), GESTURE_NAMES - tabbedNames(80))
    }

    @Test
    fun `right reveals the row's actions and enter performs them`() {
        var actions = 0
        compose.setContent { BodhaTheme { ActionRow(onLongClick = { actions++ }) } }

        press(Key.Tab)
        assertEquals("Instagram", focusedName())

        press(Key.DirectionRight)
        assertEquals(ACTIONS_LABEL, focusedName())

        press(Key.Enter)
        assertEquals(1, actions)
    }

    /** Tabbing the Library stays one stop per app: the actions node is not one. */
    @Test
    fun `tab passes over the actions node`() {
        compose.setContent { BodhaTheme { ActionRow(onLongClick = {}) } }

        press(Key.Tab)
        assertEquals("Instagram", focusedName())
        press(Key.Tab)
        assertEquals("After", focusedName())
    }

    /** The accelerator: one press instead of two, and never the only route. */
    @Test
    fun `the menu key performs the row's actions outright`() {
        var actions = 0
        compose.setContent { BodhaTheme { ActionRow(onLongClick = { actions++ }) } }

        press(Key.Tab)
        press(Key.Menu)
        assertEquals(1, actions)
    }

    @Test
    fun `escape presses back`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                Box(Modifier.fillMaxSize().escapeIsBack()) { ListRow("Row", onClick = {}) }
            }
        }

        press(Key.Tab)
        press(Key.Escape)
        assertEquals(1, backs)
    }

    /**
     * The measurement the root binding rests on: a key event walks up from the
     * focused node, so with nothing focused it never reaches the root. This is
     * why every surface focuses something on arrival — the clause below is not a
     * convenience, it is what gives a placeholder surface a back key at all.
     */
    @Test
    fun `escape needs something focused, and nothing is until it is asked for`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                Box(Modifier.fillMaxSize().escapeIsBack()) { ListRow("Row", onClick = {}) }
            }
        }
        press(Key.Escape)
        assertEquals("nothing focused, nothing reaches the root", 0, backs)
    }

    /**
     * So a surface with no field focuses its own control on arrival, and Escape
     * works there without the user hunting for focus first.
     *
     * Opened by Enter rather than composed already-open, because that is the only
     * sequence in which arrival focus can happen at all: `clickable` is focusable
     * *in non-touch mode*, so nothing outside a text field takes focus until a
     * key has put Compose in keyboard input mode.
     */
    @Test
    fun `a surface takes focus on arrival, which gives it a back key`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                var open by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    if (open) PlaceholderSurface(title = "Today", onBack = {})
                    else ListRow("Open Today", onClick = { open = true })
                }
            }
        }

        press(Key.Tab)
        press(Key.Enter)
        assertEquals("Today — coming soon. Tap to go back.", focusedName())

        press(Key.Escape)
        assertEquals(1, backs)
    }

    /** Down enters the list rather than driving a selection distinct from focus. */
    @Test
    fun `down from the search field enters the first row`() {
        compose.setContent {
            BodhaTheme {
                Column {
                    LibrarySearchField(query = "", onQueryChange = {})
                    ListRow("Instagram", onClick = {})
                    ListRow("After", onClick = {})
                }
            }
        }

        press(Key.Tab)
        assertEquals("Search apps", focusedName())

        press(Key.DirectionDown)
        assertEquals("Instagram", focusedName())
    }

    /**
     * ADR 0023: the hint teaches one convention, and stops when the key is used —
     * by the press, not by the hint having been shown.
     */
    @Test
    fun `the hint shows on a focused row and the right key retires it`() {
        var used = 0
        compose.setContent {
            BodhaTheme {
                CompositionLocalProvider(
                    LocalActionsKeyHint provides ActionsKeyHint(shown = true, onKeyUsed = { used++ })
                ) { ActionRow(onLongClick = {}) }
            }
        }

        assertTrue("nothing at rest", "→ for actions" !in drawnText())

        press(Key.Tab)
        assertTrue("on the focused row", "→ for actions" in drawnText())
        assertEquals(0, used)

        press(Key.DirectionRight)
        assertEquals(1, used)
    }

    /**
     * The Icons layout's cell and the Groups layout's header are the two other
     * long-press-only outcomes, and neither is a row: without this they are
     * reachable by Tab and inert to everything a keyboard can do afterwards.
     */
    @Test
    fun `right reaches an icon cell's actions`() {
        var actions = 0
        compose.setContent {
            BodhaTheme {
                Column {
                    IconCell(
                        app = HomeAction(id = "gallery.app", label = "Instagram"),
                        iconKey = Unit,
                        iconFor = { null },
                        onOpen = {},
                        onLongPress = { actions++ },
                    )
                    ListRow("After", onClick = {})
                }
            }
        }

        press(Key.Tab)
        assertEquals("Instagram", focusedName())
        press(Key.DirectionRight)
        assertEquals(ACTIONS_LABEL, focusedName())
        press(Key.Enter)
        assertEquals(1, actions)
    }

    @Test
    fun `right reaches a section overline's actions`() {
        var actions = 0
        compose.setContent {
            BodhaTheme {
                Column {
                    SectionOverline("Work", onLongClick = { actions++ })
                    ListRow("After", onClick = {})
                }
            }
        }

        press(Key.Tab)
        assertEquals("Work", focusedName())
        press(Key.DirectionRight)
        assertEquals(ACTIONS_LABEL, focusedName())
        press(Key.Enter)
        assertEquals(1, actions)
    }

    /**
     * Every sheet and dialog composes into a window of its own, so the activity's
     * binding never sees their keys — the root's `onKeyEvent` is reached along the
     * focused node's own root. Both surfaces are checked because they answer
     * differently: a `Dialog` presses its own back dispatcher, a `ModalBottomSheet`
     * registers no callback the composition can see, which is why the binding
     * takes the dismissal rather than the dispatcher.
     */
    @Test
    fun `escape dismisses a dialog`() {
        var dismissed = 0
        compose.setContent {
            BodhaTheme {
                Dialog(onDismissRequest = { dismissed++ }) {
                    Column(Modifier.escapeDismisses { dismissed++ }.focusOnOpen()) {
                        ListRow("Inside", onClick = {})
                    }
                }
            }
        }

        compose.onAllNodes(isRoot())[1].performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, dismissed)
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test
    fun `escape dismisses a sheet`() {
        var dismissed = 0
        compose.setContent {
            BodhaTheme {
                ModalBottomSheet(onDismissRequest = { dismissed++ }) {
                    Column(Modifier.escapeDismisses { dismissed++ }.focusOnOpen()) {
                        ListRow("Inside", onClick = {})
                    }
                }
            }
        }

        compose.onAllNodes(isRoot())[1].performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, dismissed)
    }

    /**
     * The affordances **at rest**, which is the state Home ships and the one the
     * gallery's forced ring can never photograph: floored and named before focus
     * has reached any of them, because ADR 0020 admits no unfloored or unnamed
     * actionable node and a collapsed one is exactly the invisible focusable node
     * it rules out.
     */
    @Test
    fun `a gesture affordance is floored and named before focus reaches it`() {
        compose.setContent { BodhaTheme { GestureFixture() } }

        assertEquals(null, focusedName())
        val floorPx = with(compose.density) { TOUCH_TARGET_MIN.roundToPx() }
        val affordances = nodes().filter { it.name() in GESTURE_NAMES }
        assertEquals(GESTURE_NAMES.size, affordances.size)
        assertEquals(
            emptyList<String>(),
            affordances.filter { it.size.width < floorPx || it.size.height < floorPx }
                .map { "${it.name()} = ${it.size.width}x${it.size.height}px" },
        )
    }

    /**
     * And nothing at all for a touch user, which is what lets them be floored:
     * five 48dp targets over Home's swipe layer is what ADR 0022 rejected.
     *
     * The input mode is provided rather than acted out, because the test
     * environment starts in keyboard mode — that default is also why every other
     * case here sees the affordances without pressing a key first.
     */
    @Test
    fun `the affordances do not exist for a touch user`() {
        compose.setContent {
            BodhaTheme {
                CompositionLocalProvider(LocalInputModeManager provides TouchOnly) {
                    GestureFixture()
                }
            }
        }

        assertEquals(emptyList<String>(), nodes().mapNotNull { it.name() }.filter { it in GESTURE_NAMES })
    }

    @Test
    fun `a retired hint draws nothing`() {
        compose.setContent { BodhaTheme { ActionRow(onLongClick = {}) } }

        press(Key.Tab)
        assertTrue("→ for actions" !in drawnText())
        // The actions node itself is unaffected: the hint teaches the key, it is
        // not the route to it.
        assertTrue(ACTIONS_LABEL in nodes().mapNotNull { it.name() })
    }
}

/** Home's six, five of them labelled — the unlabelled one draws no node. */
@androidx.compose.runtime.Composable
private fun GestureFixture() {
    Column {
        ListRow("Somewhere else", onClick = {})
        HomeGestureAffordances(
            HomeGestures(
                swipeDown = GestureAction("Open Search") {},
                swipeUp = GestureAction("Open App Library") {},
                swipeLeft = GestureAction("Open Awareness") {},
                swipeRight = GestureAction("Open Today") {},
                doubleTapEmpty = GestureAction(label = null) {},
                longPressEmpty = GestureAction("Edit layout") {},
            )
        )
    }
}

/** A device with no keyboard: Compose never leaves touch mode on its own. */
private val TouchOnly = object : InputModeManager {
    override val inputMode = InputMode.Touch
    override fun requestInputMode(inputMode: InputMode) = false
}

/** Named for the destination, never the direction (ADR 0011 may reassign). */
private val GESTURE_NAMES = setOf(
    "Open Search", "Open App Library", "Open Awareness", "Open Today", "Edit layout",
)

/** A row with per-item actions, and one after it so Tab has somewhere to go. */
@androidx.compose.runtime.Composable
private fun ActionRow(onLongClick: () -> Unit) {
    Column {
        ListRow("Instagram", onClick = {}, onLongClick = onLongClick)
        ListRow("After", onClick = {})
    }
}
