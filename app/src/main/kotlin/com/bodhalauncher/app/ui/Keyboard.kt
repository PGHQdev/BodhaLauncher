package com.bodhalauncher.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Keyboard operability (ADR 0022), and the one convention Bodha teaches about it
 * (ADR 0023).
 *
 * What Android already gives is left alone: Tab and Shift+Tab, arrow-key focus
 * search, Enter and Space on anything `clickable`, PageUp/PageDown in a lazy
 * list, and the whole text-editing set inside a `BasicTextField`. What is here
 * is only the four things nothing gives — the row's actions, the Menu
 * accelerator, Escape, and focus on arrival — plus [Modifier.downEntersList],
 * which exists because a single-line field swallows Down as a cursor command.
 *
 * The letter rail is deliberately absent: it is `clearAndSetSemantics` over
 * `pointerInput` with no `clickable`, so it is already unfocusable, and ADR 0022
 * makes that the intended state. A docked user types "m" into the search field
 * faster than they could scrub, so the speed test passes without a mirror of
 * twenty-seven letters.
 */

/**
 * A row's per-item actions, from the keyboard.
 *
 * Long-press is what a keyboard cannot do, and it is the only other route to a
 * Home pin's options or an app's actions — so this is the gap with no second
 * route, which is why it is worth a mechanism and Tab, Enter and Escape are not.
 *
 * **Right rather than Tab**, because tabbing a three-hundred-row Library must
 * stay one stop per app. Compose has no way to keep a focusable node out of the
 * tab order, so the node is focusable *only once Right has asked for it*: at
 * rest [RowActions.revealed] is false, `canFocus` is false and Tab passes over
 * it. That inverts the ADR's mechanism — it assumed arrows and Tab traverse
 * different sets, and in Compose they traverse the same one — while keeping both
 * things it asked for.
 */

/** The actions node's spoken name, and what the traversal guard finds it by. */
internal const val ACTIONS_LABEL = "Actions"

/** Names the key and what it does, and nothing else fits on a row (ADR 0023). */
private const val ACTIONS_HINT = "→ for actions"

/**
 * Whether a focused row still teaches the actions key, and what to call when the
 * key is used. Provided once at the root so every row answers the same way;
 * the default teaches nothing, so a fixture composing a row in isolation draws
 * no hint unless it asks for one.
 */
@Stable
class ActionsKeyHint(val shown: Boolean, val onKeyUsed: () -> Unit = {})

val LocalActionsKeyHint = staticCompositionLocalOf { ActionsKeyHint(shown = false) }

/** One row's actions node, and whether Right has asked for it yet. */
@Stable
internal class RowActions {
    var revealed by mutableStateOf(false)
    val requester = FocusRequester()
}

/** Null for a row with no actions, so the slot and the keys both cost nothing. */
@Composable
internal fun rememberRowActions(onLongClick: (() -> Unit)?): RowActions? =
    if (onLongClick == null) null else remember { RowActions() }

/**
 * The keys a focused row answers: Right reveals its actions node, and
 * `KEYCODE_MENU` performs them outright.
 *
 * Menu is an **accelerator on the same actions** — one press instead of two —
 * and never the only route, which is what keeps it from being the first crack
 * in focus-plus-Enter: a Chromebook has no Menu key and reaches the same place
 * with Right (ADR 0022).
 */
@Composable
internal fun Modifier.actionsKeys(actions: RowActions?, onActions: (() -> Unit)?): Modifier {
    if (actions == null || onActions == null) return this
    val hint = LocalActionsKeyHint.current
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.DirectionRight -> {
                // Retired by the press, not by the hint having been seen.
                hint.onKeyUsed()
                actions.revealed = true
                true
            }
            Key.Menu -> {
                hint.onKeyUsed()
                onActions()
                true
            }
            else -> false
        }
    }
}

/**
 * The trailing keyboard slot: the hint, then the actions node, both drawn only
 * while the row holds focus. Touch focuses nothing, so a touch user never sees
 * either (ADR 0026), and neither exists in the tree to be walked or measured
 * until focus arrives.
 */
@Composable
internal fun ActionsSlot(actions: RowActions?, onActions: (() -> Unit)?, rowFocused: Boolean) {
    if (actions == null || onActions == null) return
    if (!focusRingShown(rowFocused || actions.revealed)) return
    if (LocalActionsKeyHint.current.shown) {
        // Not an actionable node: it draws on the row, is not focusable and
        // carries no click, so neither the floor nor the traversal applies to it.
        Text(ACTIONS_HINT, style = BodhaType.caption, color = LocalBodhaColors.current.inkMuted)
    }
    ActionsNode(actions, onActions)
}

@Composable
private fun ActionsNode(actions: RowActions, onActions: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    var focused by remember { mutableStateOf(false) }
    // Runs after the recomposition that made canFocus true, which is why the
    // reveal is a state flip rather than a request from inside the key handler.
    LaunchedEffect(actions.revealed) { if (actions.revealed) actions.requester.requestFocus() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .focusRequester(actions.requester)
            .focusProperties { canFocus = actions.revealed }
            .onFocusChanged {
                focused = it.isFocused
                // Leaving puts the node back out of the tab order, so the next
                // row is one stop again.
                if (!it.isFocused) actions.revealed = false
            }
            .touchTargetFloor()
            .then(if (focusRingShown(focused)) Modifier.focusRing(shape) else Modifier)
            .clickable(onClick = onActions)
            .semantics { contentDescription = ACTIONS_LABEL },
    ) {
        Text("⋯", style = BodhaType.title, color = LocalBodhaColors.current.inkMuted)
    }
}

/**
 * **Escape is back** — the single exception to focus-plus-Enter, because back has
 * no node to focus and nothing for the general mechanism to attach to.
 *
 * It presses the same back dispatcher `BackHandler` feeds, which is what makes
 * "no per-surface variation" true by construction rather than by two
 * implementations agreeing: on Home there is no enabled callback, so Escape does
 * nothing, exactly as system back does. It binds on the non-preview `onKeyEvent`
 * so a focused child may consume Escape first.
 *
 * **One binding per Compose root, not one per app.** A key event travels up the
 * focus chain of the root the focused node lives in, and every sheet and dialog
 * composes into a window of its own — so the activity's binding covers only the
 * activity's window, and a sheet needs [Modifier.escapeDismisses].
 *
 * **This depends on the surface having focused something.** Compose walks a key
 * event up from the focused node, and with nothing focused it never reaches the
 * root at all — measured, not assumed. That is why [Modifier.focusOnOpen] is not
 * only a convenience: a surface where nothing takes focus on arrival is a surface
 * with no back key until the user presses Tab.
 */
@Composable
fun Modifier.escapeIsBack(): Modifier {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    return onKeyEvent { event ->
        val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
        if (escape && dispatcher?.hasEnabledCallbacks() == true) {
            dispatcher.onBackPressed()
            true
        } else {
            false
        }
    }
}

/**
 * Escape on a surface that brings its own window: the same dismissal its scrim
 * and its back already perform (ADR 0011 — sheets dismiss first).
 *
 * It takes the lambda rather than pressing the back dispatcher, because pressing
 * it here is either nothing or the wrong thing — measured on this toolchain: a
 * `ModalBottomSheet` handles back inside its own window and registers no callback
 * the composition can see, so `hasEnabledCallbacks()` is false and Escape does
 * nothing; and where it is true, the enabled callback is the *screen's* — the
 * Library's back — which would leave the sheet open over a changed surface.
 *
 * A `Dialog` does press its own dispatcher correctly, and is bound this way
 * anyway: one mechanism on every surface that opens over another is worth more
 * than each one being bound by whichever plumbing happens to work.
 */
@Composable
fun Modifier.escapeDismisses(onDismiss: () -> Unit): Modifier = onKeyEvent { event ->
    val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
    if (escape) onDismiss()
    escape
}

/**
 * **Focus lands on arrival**: a surface's first field takes focus when the
 * surface opens, one behaviour for every input model — branching on
 * `hardKeyboardHidden` would have preserved touch byte-for-byte and made Bodha
 * behave differently by input model, which nothing else here does.
 *
 * The IME is suppressed by `hide()` on the focus gain, which is a hide *after* a
 * show and can flash on a slow device: Compose exposes no supported "focus
 * without showing the IME", and `windowSoftInputMode` governs window focus rather
 * than a composable's `requestFocus`. ADR 0022 accepts this and names it the
 * residual most likely to need revisiting.
 *
 * Only the arrival is suppressed. A later tap or Tab into the field shows the
 * keyboard as it always did, because hiding on *every* focus gain would take the
 * IME away from the touch user who just asked for it.
 *
 * On anything that is not a text field this lands only once Compose is in
 * keyboard input mode, because `clickable` is focusable *in non-touch mode*: a
 * touch-only user's surfaces therefore open exactly as they did, and a docked
 * user's have already had a key pressed by the time they arrive.
 */
@Composable
fun Modifier.focusOnOpen(): Modifier {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var arriving by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { requester.requestFocus() }
    return focusRequester(requester).onFocusChanged {
        if (it.isFocused && arriving) {
            arriving = false
            keyboard?.hide()
        }
    }
}

/**
 * **Down enters the list beneath a field.** Enter then activates the row through
 * its own `clickable`, for free.
 *
 * Bound explicitly and on the preview pass because a single-line `BasicTextField`
 * takes Up and Down as cursor commands and never passes them to focus search.
 * Unconsumed when there is nothing below, so the field keeps its own behaviour
 * at the end of a list.
 *
 * A true combobox — the field keeps focus, Down drives a highlighted row — was
 * rejected: it introduces a selection distinct from focus, which nothing in
 * Bodha has, and neither the traversal guard nor focus-plus-Enter would then
 * describe what is happening on the surface a docked user spends most time in.
 */
@Composable
fun Modifier.downEntersList(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        event.type == KeyEventType.KeyDown &&
            event.key == Key.DirectionDown &&
            focusManager.moveFocus(FocusDirection.Down)
    }
}
