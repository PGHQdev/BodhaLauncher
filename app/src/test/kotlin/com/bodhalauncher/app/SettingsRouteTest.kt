package com.bodhalauncher.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.EditHomeDialog
import com.bodhalauncher.app.ui.GestureAction
import com.bodhalauncher.app.ui.HomeGestureAffordances
import com.bodhalauncher.app.ui.HomeGestures
import com.bodhalauncher.app.ui.escapeIsBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The route to Settings, walked with the keys (#140): Tab to the edit-layout
 * affordance, Enter, Tab to the Settings row, Enter — and Escape back out.
 *
 * Driven root by root, because a `Dialog` composes into a window of its own: a
 * key event travels up the focus chain of the root its focused node lives in, so
 * the edit-mode press and the surface press are not addressed to the same place
 * (ADR 0022).
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp", application = android.app.Application::class)
class SettingsRouteTest {

    @get:Rule
    val compose = createComposeRule()

    private fun rootAt(index: Int) = compose.onAllNodes(isRoot())[index]

    private fun press(rootIndex: Int, key: Key) =
        rootAt(rootIndex).performKeyInput { pressKey(key) }

    private fun nodes(rootIndex: Int): List<SemanticsNode> {
        val found = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            found += node
            node.children.forEach(::walk)
        }
        walk(rootAt(rootIndex).fetchSemanticsNode())
        return found
    }

    private fun focusedName(rootIndex: Int): String? = nodes(rootIndex)
        .filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
        .mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }
        .firstOrNull()

    /** Tabs until [name] holds focus, or gives up loudly rather than pressing Enter on the wrong node. */
    private fun tabTo(rootIndex: Int, name: String) {
        repeat(TAB_LIMIT) {
            if (focusedName(rootIndex) == name) return
            press(rootIndex, Key.Tab)
        }
        assertEquals("Tab never reached $name", name, focusedName(rootIndex))
    }

    @Test
    fun `edit mode reaches Settings, whose row reaches the role request, and Escape leaves`() {
        var requests = 0
        var backs = 0
        compose.setContent {
            BodhaTheme {
                var editing by remember { mutableStateOf(false) }
                var onSettings by remember { mutableStateOf(false) }
                BackHandler { backs++ }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    if (onSettings) {
                        SettingsSurface(
                            homeRoleHeld = false,
                            onRequestHomeRole = { requests++ },
                        )
                    } else {
                        HomeGestureAffordances(
                            HomeGestures(
                                swipeDown = GestureAction("Open Search") {},
                                swipeUp = GestureAction("Open App Library") {},
                                swipeLeft = GestureAction("Open Awareness") {},
                                swipeRight = GestureAction("Open Today") {},
                                doubleTapEmpty = GestureAction(label = null) {},
                                longPressEmpty = GestureAction("Edit layout") { editing = true },
                            )
                        )
                    }
                }
                if (editing) {
                    EditHomeDialog(
                        onAddPin = {},
                        onContextModes = {},
                        onSettings = { onSettings = true },
                        onDismiss = { editing = false },
                    )
                }
            }
        }

        tabTo(ACTIVITY, "Edit layout")
        press(ACTIVITY, Key.Enter)

        tabTo(DIALOG, "Settings")
        press(DIALOG, Key.Enter)

        // The surface's first row takes focus on arrival, which is also what gives
        // Escape a chain to reach the root binding along (ADR 0022).
        assertEquals("Home app", focusedName(ACTIVITY))
        press(ACTIVITY, Key.Enter)
        assertEquals(1, requests)

        press(ACTIVITY, Key.Escape)
        assertEquals(1, backs)
    }

    private companion object {
        const val ACTIVITY = 0

        /** The window a `Dialog` brings with it, above the activity's own. */
        const val DIALOG = 1

        /** More stops than either surface has, so a miss fails rather than loops. */
        const val TAB_LIMIT = 20
    }
}
