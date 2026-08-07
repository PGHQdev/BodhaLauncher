package com.bodhalauncher.app.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals

/**
 * Driving the keys **root by root**, which a `Dialog` makes necessary: it
 * composes into a window of its own, and a key event travels up the focus chain
 * of the root its focused node lives in — so a press meant for a dialog and one
 * meant for the surface beneath it are not addressed to the same place
 * (ADR 0022).
 *
 * Root 0 is the activity's; each dialog or sheet open over it adds the next.
 *
 * **A `BasicTextField` inside a `Dialog` cannot be driven here at all** —
 * measured on this toolchain rather than assumed: Compose never reaches idle, so
 * every read times out after a minute, whether or not anything focuses the
 * field, and whether or not the test clock auto-advances. A dialog holding one
 * has to expose its content as its own composable and be driven at
 * [ACTIVITY_ROOT], which is what `ModeEditorContent` exists for.
 */
const val ACTIVITY_ROOT = 0
const val DIALOG_ROOT = 1
const val NESTED_DIALOG_ROOT = 2

/** More stops than any surface here has, so a miss fails rather than loops. */
private const val TAB_LIMIT = 24

@OptIn(ExperimentalTestApi::class)
fun ComposeContentTestRule.press(root: Int, key: Key) =
    onAllNodes(isRoot())[root].performKeyInput { pressKey(key) }

fun ComposeContentTestRule.nodesIn(root: Int): List<SemanticsNode> {
    val found = mutableListOf<SemanticsNode>()
    fun walk(node: SemanticsNode) {
        found += node
        node.children.forEach(::walk)
    }
    walk(onAllNodes(isRoot())[root].fetchSemanticsNode())
    return found
}

/** Whatever a screen reader would read for this node, or null if it would read nothing. */
fun SemanticsNode.spokenName(): String? =
    config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
        ?: config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

fun ComposeContentTestRule.focusedNameIn(root: Int): String? = nodesIn(root)
    .filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
    .mapNotNull { it.spokenName() }
    .firstOrNull()

fun ComposeContentTestRule.namesIn(root: Int): List<String> =
    nodesIn(root).mapNotNull { it.spokenName() }

/**
 * Every string drawn in [root]. A clickable row merges its children, so a label
 * that is not the row's own first line shows up only here.
 */
fun ComposeContentTestRule.drawnTextIn(root: Int): List<String> = nodesIn(root)
    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
    .map { it.text }

/** Tabs until [name] holds focus, or gives up loudly rather than pressing Enter on the wrong node. */
fun ComposeContentTestRule.tabTo(root: Int, name: String) {
    repeat(TAB_LIMIT) {
        if (focusedNameIn(root) == name) return
        press(root, Key.Tab)
    }
    assertEquals("Tab never reached $name", name, focusedNameIn(root))
}
