package com.bodhalauncher.app.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** How far a drag must travel to count as a swipe, everywhere a surface hand-rolls one. */
val SWIPE_THRESHOLD = 72.dp

/**
 * One gesture: what it does, and the name read aloud for it. The label names the
 * destination, never the gesture — the accessibility menu is read to someone who
 * cannot perform the swipe, and ADR 0011 lets the four assignments move, so a
 * direction-named label would eventually lie.
 *
 * A null label still performs but is never announced. Offering a named action
 * that does nothing is worse than offering none, so a gesture whose mechanism is
 * still pending stays out of the menu until it works.
 */
data class GestureAction(val label: String?, val perform: () -> Unit)

/**
 * The spec's gesture fan-out from Home; taps on actions are consumed by their rows first.
 *
 * These gestures are the only route to every other surface, so each is also a
 * custom accessibility action (#111). `pointerInput` exposes nothing on its own —
 * Compose derives semantics only for `clickable`/`combinedClickable` — so without
 * them a screen-reader user reaches nothing beyond Home.
 */
data class HomeGestures(
    val swipeDown: GestureAction,
    val swipeUp: GestureAction,
    val swipeLeft: GestureAction,
    val swipeRight: GestureAction,
    val doubleTapEmpty: GestureAction,
    val longPressEmpty: GestureAction,
) {
    /** Declaration order is the order the actions are announced in. */
    val all: List<GestureAction>
        get() = listOf(swipeDown, swipeUp, swipeLeft, swipeRight, doubleTapEmpty, longPressEmpty)
}

fun Modifier.homeGestures(gestures: HomeGestures): Modifier = this
    .semantics {
        // Custom actions are offered only on the node holding accessibility
        // focus, and a node with no description is not reliably focusable when
        // its children are. Naming the surface and marking it a traversal group
        // is what makes the actions actually reachable rather than merely present.
        contentDescription = "Home"
        isTraversalGroup = true
        customActions = gestures.all.mapNotNull { action ->
            action.label?.let { label ->
                CustomAccessibilityAction(label) {
                    action.perform()
                    true
                }
            }
        }
    }
    .pointerInput(gestures) {
        val threshold = SWIPE_THRESHOLD.toPx()
        var drag = Offset.Zero
        detectDragGestures(
            onDragStart = { drag = Offset.Zero },
            onDrag = { _, amount -> drag += amount },
            onDragEnd = {
                val (x, y) = drag
                when {
                    abs(y) >= abs(x) && y > threshold -> gestures.swipeDown.perform()
                    abs(y) >= abs(x) && y < -threshold -> gestures.swipeUp.perform()
                    abs(x) > abs(y) && x < -threshold -> gestures.swipeLeft.perform()
                    abs(x) > abs(y) && x > threshold -> gestures.swipeRight.perform()
                }
            },
        )
    }
    .pointerInput(gestures) {
        detectTapGestures(
            onDoubleTap = { gestures.doubleTapEmpty.perform() },
            onLongPress = { gestures.longPressEmpty.perform() },
        )
    }
