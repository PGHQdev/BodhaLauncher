package com.bodhalauncher.app.session

import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.engine.Transition

/**
 * What a session boundary does to what is on screen (ADR 0011, #134).
 *
 * A sheet is one decision you make and leave, so a decision the phone went dark
 * on is not waiting on the next unlock: the ending session takes it, dismissing
 * it on its own terms via [SheetSlot.dismissCurrent] so the outcome it always
 * records is the outcome recorded here too.
 *
 * Only the engine's own transitions are read — never a second reading of the
 * screen-off broadcast — which is what makes the merge window free: a re-unlock
 * inside it publishes [Transition.SessionResumed] rather than an end and a
 * start, so nothing here fires and the user comes back to the sheet, the
 * surface and the scroll position they left mid-decision (ADR 0001).
 *
 * [toRoot] is the new session's arrival: a session starts where the back gesture
 * and the system Home button already land, rather than wherever the last one was
 * abandoned. Root is Focus while a Focus session runs — the caller resolves it.
 */
fun applySessionBoundary(transition: Transition, sheets: SheetSlot, toRoot: () -> Unit) {
    when (transition) {
        is Transition.SessionEnded -> sheets.dismissCurrent()
        is Transition.SessionStarted -> toRoot()
        // A resume keeps the session, and a peek never had one.
        is Transition.SessionResumed, is Transition.PeekObserved -> Unit
    }
}
