package com.bodhalauncher.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Home's gestures are the only route to every other surface, so each one must
 * also be an accessibility action (#111). Labels name the destination, never
 * the gesture: the menu is read aloud to someone who cannot perform the swipe,
 * and ADR 0011 lets the four assignments move.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeGesturesAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun gestures(record: (String) -> Unit) = HomeGestures(
        swipeDown = GestureAction("Open Search") { record("down") },
        swipeUp = GestureAction("Open App Library") { record("up") },
        swipeLeft = GestureAction("Open Awareness") { record("left") },
        swipeRight = GestureAction("Open Today") { record("right") },
        doubleTapEmpty = GestureAction("Lock screen") { record("lock") },
        longPressEmpty = GestureAction("Edit layout") { record("edit") },
    )

    private fun setContent(record: (String) -> Unit = {}) {
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("home").homeGestures(gestures(record)))
        }
    }

    private fun customActions() = compose.onNodeWithTag("home")
        .fetchSemanticsNode()
        .config[SemanticsActions.CustomActions]

    @Test
    fun `every gesture is exposed as a custom accessibility action`() {
        setContent()
        assertEquals(
            listOf(
                "Open Search",
                "Open App Library",
                "Open Awareness",
                "Open Today",
                "Lock screen",
                "Edit layout",
            ),
            customActions().map { it.label },
        )
    }

    @Test
    fun `invoking an action runs the gesture it stands for`() {
        val performed = mutableListOf<String>()
        setContent { performed += it }

        customActions().first { it.label == "Open Today" }.action()

        assertEquals(listOf("right"), performed)
    }

    @Test
    fun `labels follow the assignment rather than the direction`() {
        compose.setContent {
            Box(
                Modifier.fillMaxSize().testTag("home").homeGestures(
                    HomeGestures(
                        swipeDown = GestureAction("Open Today") {},
                        swipeUp = GestureAction("Open App Library") {},
                        swipeLeft = GestureAction("Open Awareness") {},
                        swipeRight = GestureAction("Open Search") {},
                        doubleTapEmpty = GestureAction("Lock screen") {},
                        longPressEmpty = GestureAction("Edit layout") {},
                    )
                )
            )
        }
        val labels = customActions().map { it.label }
        assertEquals("Open Today", labels.first())
        assertTrue(labels.none { it.startsWith("Swipe") })
    }
}
