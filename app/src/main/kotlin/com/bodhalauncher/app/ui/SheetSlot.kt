package com.bodhalauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bodhalauncher.app.focus.PendingFocusEnd
import com.bodhalauncher.engine.EducationScreen
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.PromptDecision
import com.bodhalauncher.engine.SearchContact
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.SessionRecord
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

    /** A search result's actions — the Library's hide and pin, met in Search (#184). */
    class ResultActions(val app: HomeAction) : Sheet()

    /** A contact result's actions — call and message live here, never on the tap (#186). */
    class ContactActions(val contact: SearchContact) : Sheet()

    /**
     * The reflexive-use prompt on Home (#4). It carries the decision it was
     * opened for, so its outcome can be recorded against that decision even
     * once the runtime has stopped treating it as pending (#134).
     */
    class IntentPrompt(val decision: PromptDecision) : Sheet()

    /**
     * The pause before a ruled app (#8). The launch is gated until this answers.
     * [raisedByFocus] marks a check the session's allowed list fired (#168), so
     * the proceed is counted against the session rather than a rule.
     */
    class OpenCheck(val app: HomeAction, val raisedByFocus: Boolean = false) : Sheet()

    /** A timed session's end moment (#75). */
    class SessionEnd(val end: TimedSessionEnd) : Sheet()

    /** What a capability is for, before any system screen (#18, #157). */
    class Education(val screen: EducationScreen) : Sheet()

    /** Today's intention editor (#158) — the one place the intention is set. */
    class IntentionEditor : Sheet()

    /** The inbox row's actions (#163) — the system key names the notification, never its content. */
    class NotificationActions(val key: String) : Sheet()

    /** The snooze duration, one decision after [NotificationActions] replaces it (#163). */
    class SnoozeDurations(val key: String) : Sheet()

    /**
     * One session's actions in Awareness (#178). It carries the **record**
     * rather than the id, because the sheet names what it is about and a
     * session's name is when it ran — which only the record holds.
     */
    class AwarenessSessionActions(val record: SessionRecord) : Sheet()

    /**
     * One app's actions in Awareness (#178), carrying only the id. The name is
     * resolved where every other Awareness view resolves it, so an app
     * uninstalled since the launch is named as uninstalled in one place rather
     * than in two that have to agree.
     */
    class AwarenessAppActions(val appId: String) : Sheet()

    /** Focus setup (#166) — one decision; dismissing starts nothing. */
    class FocusSetup : Sheet()

    /** A Focus session's end moment (#170), owed to the next arrival at root. */
    class FocusEnd(val moment: PendingFocusEnd) : Sheet()
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
    private var onDismiss: (() -> Unit)? = null

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
        this.onDismiss = null
        // After the swap, so a handler that reaches back in finds the new sheet.
        outgoing?.invoke()
    }

    /** Close [sheet], if it is still the one open. */
    fun close(sheet: Sheet) {
        if (current !== sheet) return
        current = null
        onReplaced = null
        onDismiss = null
    }

    /**
     * How [sheet] dismisses on its own terms — recorded here and handed straight
     * back, so the render site passes this very lambda to its sheet composable
     * and there is one dismissal path rather than a copy of one (#134).
     *
     * Ignored for a sheet that is no longer the one open: a render site leaving
     * composition late must not hand its dismissal to the sheet that replaced it.
     */
    fun dismissedBy(sheet: Sheet, dismiss: () -> Unit): () -> Unit {
        if (current === sheet) onDismiss = dismiss
        return dismiss
    }

    /**
     * Dismiss whatever is open, exactly as the user would (ADR 0011, #134): the
     * session ended, so an Open Check records the turn-back it always records
     * and an intent prompt records its dismissal — the phone going dark on a
     * decision is that decision going unmade, which is what dismissal already
     * means. No outcome kind exists for "cleared by the session".
     *
     * A sheet whose render site has not composed yet has no dismissal to run,
     * so it is simply closed; the ADR's rule holds either way, only its outcome
     * is unrecordable.
     */
    fun dismissCurrent() {
        val sheet = current ?: return
        val dismiss = onDismiss
        if (dismiss != null) dismiss() else close(sheet)
    }

    /** The open sheet if it is a [T] — how a render site asks for its own. */
    inline fun <reified T : Sheet> showing(): T? = current as? T

    companion object {
        /**
         * Only an in-flight Open Check is worth restoring: a check that vanished
         * on a rotation would leave its launch with no outcome logged, which is
         * the return rate #25 measures. The other four are re-derived or gone —
         * which is what they already did before the slot existed.
         *
         * [session] is the phone session the check is saved under, because a
         * restore is the one path the session boundary cannot reach: after
         * process death the engine reconciles and publishes its end and the next
         * start before composition exists, so no listener hears it (#134). A
         * check saved under a session that is no longer the one running is
         * therefore dropped rather than restored — which is the same rule
         * [dismissCurrent] applies to a live one, minus the outcome, since the
         * process that owed it is gone.
         */
        fun saver(session: () -> SessionId?) = listSaver<SheetSlot, String>(
            save = {
                (it.current as? Sheet.OpenCheck)
                    ?.let { sheet ->
                        listOf(
                            sheet.app.id, sheet.app.label, sessionKey(session),
                            // Focus-raised survives too, or the restored proceed
                            // would go uncounted against the session (#168).
                            if (sheet.raisedByFocus) "1" else "0",
                        )
                    }
                    ?: emptyList()
            },
            restore = {
                if (it[2] == sessionKey(session)) {
                    SheetSlot(Sheet.OpenCheck(HomeAction(it[0], it[1]), raisedByFocus = it[3] == "1"))
                } else SheetSlot()
            },
        )

        private fun sessionKey(session: () -> SessionId?) = session()?.value?.toString() ?: ""
    }
}

@Composable
fun rememberSheetSlot(session: () -> SessionId?): SheetSlot =
    rememberSaveable(saver = SheetSlot.saver(session)) { SheetSlot() }
