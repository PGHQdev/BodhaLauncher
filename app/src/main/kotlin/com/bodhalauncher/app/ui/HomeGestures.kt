package com.bodhalauncher.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
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
        // reachable: each swipe is also a custom action and a focus-revealed
        // affordance (HomeGestureAffordances).
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
        // reachable: long-press is "Edit layout" in the actions menu and in the
        // affordances; double-tap is unlabelled and reaches neither, see below.
        detectTapGestures(
            onDoubleTap = { gestures.doubleTapEmpty.perform() },
            onLongPress = { gestures.longPressEmpty.perform() },
        )
    }

/**
 * The gestures' keyboard route (ADR 0022): **focus-revealed affordances** — the
 * skip-link pattern. Nothing at rest, and the node draws itself when it takes
 * focus, so a docked user reaches every surface with Tab and Enter.
 *
 * A permanent visible affordance is the design change ADR 0011's radial model
 * exists to avoid; an invisible focusable node fails ADR 0020's floor or eats
 * 48dp of the swipe layer. Revealed, the node is real, drawn, named and floored,
 * so the floor needs no exception — and it takes the ring too (ADR 0026), since
 * appearing says *this control exists* and the ring says *this is where focus is*.
 *
 * **"Nothing at rest" is the composition, not a collapsed node.** A node that
 * stays in the tree and measures 0dp until focus arrives is the invisible
 * focusable node ADR 0020's floor rules out, and it is nameless besides, so the
 * two clauses fail on five nodes the moment anything renders Home's affordances
 * unfocused. So the affordances exist only while Compose is in keyboard input
 * mode, which ADR 0026's probe established a touch click never enters: a
 * touch-only user has no node at all — nothing to measure, nothing to name and
 * nothing over the swipe layer — and a docked user's nodes are floored and named
 * from the moment they exist, whether or not one of them holds focus.
 *
 * The cost is expected to be the first key press of a session: the mode flips as
 * that event is dispatched, so the nodes are composed after the focus search
 * that press already ran, and the skip links would lead the traversal from the
 * second Tab rather than the first. Tab wraps, so nothing is unreachable either
 * way. Unmeasured — the test environment starts in keyboard mode, so no fixture
 * here can see the transition; it needs a docked device, like the rest of this
 * ADR's residual.
 *
 * A gesture with no label gets no node, for the reason it gets no custom action:
 * a named affordance that only reports its own absence is worse than none. That
 * is `doubleTapEmpty` today, so lock has no keyboard route until it has a
 * mechanism — the same gap TalkBack has, rather than a second one.
 *
 * A consequence worth stating rather than discovering: these are real semantics
 * nodes, so a screen reader now finds each gesture twice — once as a button here
 * and once as a custom action on Home (#111). Suppressing the duplicate needs an
 * experimental accessibility API and a modifier chain that changes shape when
 * focus arrives, which is how a node loses the focus that just arrived. The
 * duplicate is the cheaper of the two.
 */
@Composable
fun HomeGestureAffordances(gestures: HomeGestures, modifier: Modifier = Modifier) {
    val docked = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    // The fixtures are the other reader of these nodes: a golden and a walk both
    // capture at rest, and neither presses a key first (ADR 0026).
    if (!docked && !LocalForceFocusRing.current) return
    Column(modifier.fillMaxWidth()) {
        gestures.all.forEach { action ->
            action.label?.let { GestureAffordance(label = it, perform = action.perform) }
        }
    }
}

@Composable
private fun GestureAffordance(label: String, perform: () -> Unit) {
    val colors = LocalBodhaColors.current
    val shape = RoundedCornerShape(percent = 50)
    var focused by remember { mutableStateOf(false) }
    val revealed = focusRingShown(focused)
    // Geometry and name are constant; only the stroke's colour and the label's
    // presence answer to focus. Both because ADR 0020 admits no unfloored or
    // unnamed actionable node, and because a node whose size changes underneath
    // the focus that just arrived is a node that loses it.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .touchTargetFloor()
            .border(
                width = FOCUS_RING_WIDTH,
                color = if (revealed) colors.accent else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = perform)
            .semantics { contentDescription = label }
            .padding(BodhaSpacing.s),
    ) {
        if (revealed) Text(text = label, style = BodhaType.action, color = colors.accent)
    }
}
