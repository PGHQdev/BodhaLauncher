package com.bodhalauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bodhalauncher.engine.EducationScreen
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.TimedSessionEnd

/**
 * The one sheet in the whole app (ADR 0011, #133).
 *
 * A sheet is one decision you make and leave, and at most one exists at any
 * moment: opening any sheet puts it here, and whatever was here is gone — its
 * state with it, because the composable leaves composition rather than being
 * hidden. That is what makes "no nested modal stacks" structural rather than a
 * discipline nobody can enforce: there is one variable, so two sheets is not a
 * state this app can express.
 *
 * Each variant is a class rather than a data class, so identity is the rule.
 * [close] takes the sheet it means to close and does nothing if that sheet has
 * already been replaced — a dismissal arriving late from a sheet that is gone
 * must not take the one standing in its place.
 *
 * The seven built `*Dialog` composables are sheets under CONTEXT.md's test and
 * subject to the same rule, but they do not move here in this slice; each moves
 * when its own spec next touches it (#133).
 */
sealed class Sheet {

    /** The Library's per-app actions (#7). */
    class AppActions(val app: HomeAction) : Sheet()

    /** The reflexive-use prompt on Home (#4). */
    class IntentPrompt : Sheet()

    /** The pause before a ruled app (#8). The launch is gated until this answers. */
    class OpenCheck(val app: HomeAction) : Sheet()

    /** A timed session's end moment (#75). */
    class SessionEnd(val end: TimedSessionEnd) : Sheet()

    /** What a capability is for, before any system screen (#18, #157). */
    class Education(val screen: EducationScreen) : Sheet()
}

/**
 * Where the one sheet lives. Held by the host, and reachable by every surface
 * that opens a sheet, so the rule holds across surfaces rather than within one.
 */
@Stable
class SheetSlot(initial: Sheet? = null) {

    var current: Sheet? by mutableStateOf(initial)
        private set

    private var onReplaced: (() -> Unit)? = null

    /**
     * Put [sheet] in the slot, taking the place of whatever was there.
     *
     * [onReplaced] is how a sheet backed by state outside composition goes with
     * it: the intent prompt has to be withdrawn from its runtime or the engine
     * keeps treating it as pending, and a session end has to be settled or the
     * expiry poller re-opens it a second later, over the sheet that replaced it.
     * It runs only on replacement — a sheet dismissed on its own terms has
     * already done its own cleanup, and running both would log the outcome twice.
     */
    fun open(sheet: Sheet, onReplaced: () -> Unit = {}) {
        val outgoing = this.onReplaced
        current = sheet
        this.onReplaced = onReplaced
        // After the swap, so a handler that reaches back in finds the new sheet.
        outgoing?.invoke()
    }

    /** Close [sheet], if it is still the one open. */
    fun close(sheet: Sheet) {
        if (current !== sheet) return
        current = null
        onReplaced = null
    }

    /** The open sheet if it is a [T] — how a render site asks for its own. */
    inline fun <reified T : Sheet> showing(): T? = current as? T

    companion object {
        /**
         * Only an in-flight Open Check is worth restoring: a check that vanished
         * on a rotation would leave its launch with no outcome logged, which is
         * the return rate #25 measures. The other four are re-derived or gone —
         * which is what they already did before the slot existed.
         */
        val Saver = listSaver<SheetSlot, String>(
            save = {
                (it.current as? Sheet.OpenCheck)
                    ?.let { sheet -> listOf(sheet.app.id, sheet.app.label) }
                    ?: emptyList()
            },
            restore = { SheetSlot(Sheet.OpenCheck(HomeAction(it[0], it[1]))) },
        )
    }
}

@Composable
fun rememberSheetSlot(): SheetSlot = rememberSaveable(saver = SheetSlot.Saver) { SheetSlot() }
