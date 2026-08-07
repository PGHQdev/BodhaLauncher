package com.bodhalauncher.app.session

import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.engine.DeviceEvent
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.PromptDecision
import com.bodhalauncher.engine.SessionEngine
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.Transition
import com.bodhalauncher.engine.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

/**
 * ADR 0011's "nothing survives a screen-off into the next session" (#134),
 * asserted at the seam where the session engine's own transitions meet the one
 * sheet slot. The real engine drives it, so the merge window is the engine's
 * 30 seconds rather than a number restated here — a test that made up its own
 * transitions could not tell a resumed session from a new one.
 */
class SessionBoundaryTest {

    private val engine = SessionEngine()
    private val slot = SheetSlot()
    private val start: Instant = Instant.parse("2026-01-01T09:00:00Z")

    private var arrivedOnRoot = 0
    private var turnedBack = 0

    private fun apply(transitions: List<Transition>) =
        transitions.forEach { applySessionBoundary(it, slot) { arrivedOnRoot++ } }

    private fun unlock(at: Instant) = apply(engine.onEvent(DeviceEvent.Unlocked(at)))
    private fun screenOn(at: Instant) = apply(engine.onEvent(DeviceEvent.ScreenOn(at)))
    private fun screenOff(at: Instant) = apply(engine.onEvent(DeviceEvent.ScreenOff(at)))
    private fun waitUntil(at: Instant) = apply(engine.advanceTo(at))

    /** An Open Check, wired to the outcome its own dismissal records (#8, #25). */
    private fun openCheck(): Sheet.OpenCheck {
        val sheet = Sheet.OpenCheck(HomeAction("maps", "Maps"))
        slot.open(sheet)
        slot.dismissedBy(sheet) { turnedBack++; slot.close(sheet) }
        return sheet
    }

    private fun seconds(n: Long): Instant = start.plusSeconds(n)

    @Test
    fun `a sheet open when the session ends is gone on the next session`() {
        unlock(start)
        openCheck()

        screenOff(seconds(60))
        waitUntil(seconds(91))
        unlock(seconds(120))

        assertNull(slot.current)
    }

    @Test
    fun `the end dismisses the sheet on its own terms, so the outcome is recorded once`() {
        unlock(start)
        openCheck()

        screenOff(seconds(60))
        waitUntil(seconds(91))

        // Turned back is the truth: the phone went dark on the check, so the
        // launch it gated never happened — the same outcome the user's own
        // dismissal records, through the same path (#134).
        assertEquals(1, turnedBack)
    }

    @Test
    fun `a sheet nobody claimed still goes when the session ends`() {
        unlock(start)
        slot.open(Sheet.IntentPrompt(PromptDecision(SessionId(1), start, TriggerSource.Reflexive)))

        screenOff(seconds(60))
        waitUntil(seconds(91))

        assertNull(slot.current)
    }

    @Test
    fun `a re-unlock inside the merge window leaves the sheet alone`() {
        unlock(start)
        val sheet = openCheck()

        screenOff(seconds(60))
        unlock(seconds(70))

        assertSame(sheet, slot.current)
        assertEquals(0, turnedBack)
    }

    @Test
    fun `a new session arrives on root, a resumed one leaves the surface alone`() {
        unlock(start)
        assertEquals(1, arrivedOnRoot)

        screenOff(seconds(60))
        unlock(seconds(70))
        assertEquals(1, arrivedOnRoot)

        screenOff(seconds(120))
        waitUntil(seconds(151))
        unlock(seconds(200))
        assertEquals(2, arrivedOnRoot)
    }

    @Test
    fun `a peek is not a boundary`() {
        unlock(start)
        val sheet = openCheck()
        screenOff(seconds(60))
        waitUntil(seconds(91))
        slot.open(sheet)

        screenOn(seconds(200))
        screenOff(seconds(210))

        assertSame(sheet, slot.current)
        assertEquals(1, arrivedOnRoot)
    }
}
