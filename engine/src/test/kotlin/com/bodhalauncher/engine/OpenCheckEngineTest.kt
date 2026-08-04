package com.bodhalauncher.engine

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenCheckEngineTest {

    private val t0: Instant = Instant.parse("2026-08-04T09:00:00Z")
    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)

    private val always = OpenCheckRule(OpenCheckMode.Always)
    private val never = OpenCheckRule(OpenCheckMode.Never)
    private val repeated = OpenCheckRule(OpenCheckMode.RepeatedOpening)

    @Test
    fun `no rule proceeds`() {
        val decision = OpenCheckEngine().onLaunchAttempt("app", rule = null, now = t0)
        assertEquals(OpenCheckDecision.Proceed, decision)
    }

    @Test
    fun `never mode proceeds`() {
        val decision = OpenCheckEngine().onLaunchAttempt("app", never, t0)
        assertEquals(OpenCheckDecision.Proceed, decision)
    }

    @Test
    fun `always mode shows the check`() {
        val decision = OpenCheckEngine().onLaunchAttempt("app", always, t0)
        val check = assertIs<OpenCheckDecision.ShowCheck>(decision)
        assertEquals("app", check.appId)
        assertEquals(t0, check.at)
    }

    @Test
    fun `proceeding grants the opening - the same app passes within the grant window`() {
        val engine = OpenCheckEngine()
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, t0))

        engine.onProceeded("app", t0)

        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", always, at(2)))
    }

    @Test
    fun `the grant expires - a later attempt shows the check again`() {
        val engine = OpenCheckEngine()
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, t0))
        engine.onProceeded("app", t0)

        val after = t0.plus(OpenCheckEngine.GRANT_WINDOW).plusSeconds(1)
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, after))
    }

    @Test
    fun `the grant is consumed by the attempt it covers`() {
        val engine = OpenCheckEngine()
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, t0))
        engine.onProceeded("app", t0)

        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", always, at(1)))
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, at(2)))
    }

    @Test
    fun `the grant is per app - another app is still checked`() {
        val engine = OpenCheckEngine()
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, t0))
        engine.onProceeded("app", t0)

        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("other", always, at(1)))
    }

    @Test
    fun `turning back grants nothing - an immediate retry shows the check`() {
        val engine = OpenCheckEngine()
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, t0))

        engine.onTurnedBack("app")

        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, at(1)))
    }

    @Test
    fun `state round-trips through a fresh instance`() {
        val engine = OpenCheckEngine()
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, t0))
        engine.onProceeded("app", t0)

        val restored = OpenCheckEngine(engine.snapshot())

        assertEquals(OpenCheckDecision.Proceed, restored.onLaunchAttempt("app", always, at(2)))
    }

    @Test
    fun `initial state round-trips`() {
        val restored = OpenCheckEngine(OpenCheckState.Initial)
        assertIs<OpenCheckDecision.ShowCheck>(restored.onLaunchAttempt("app", always, t0))
    }

    @Test
    fun `an always check is not a repeated-open detection`() {
        val check = assertIs<OpenCheckDecision.ShowCheck>(OpenCheckEngine().onLaunchAttempt("app", always, t0))
        assertEquals(false, check.repeatedOpen)
    }

    @Test
    fun `repeated mode - first and second launches proceed, the third shows the check`() {
        val engine = OpenCheckEngine()

        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", repeated, t0))
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", repeated, at(60)))

        val check = assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", repeated, at(120)))
        assertEquals(true, check.repeatedOpen)
    }

    @Test
    fun `the window rolls - a launch older than fifteen minutes no longer counts`() {
        val engine = OpenCheckEngine()
        engine.onLaunchAttempt("app", repeated, t0)
        engine.onLaunchAttempt("app", repeated, at(300))

        // t0 has aged out of [now - 15 min, now]; only the 5-minute launch counts.
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", repeated, at(960)))
        // Now two launches sit in the window; the next is the third.
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", repeated, at(970)))
    }

    @Test
    fun `counting is per app - another app's launches never mingle`() {
        val engine = OpenCheckEngine()
        engine.onLaunchAttempt("a", repeated, t0)
        engine.onLaunchAttempt("a", repeated, at(10))
        engine.onLaunchAttempt("b", repeated, at(20))
        engine.onLaunchAttempt("b", repeated, at(30))

        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("a", repeated, at(40)))
        // b has two launches in window; its third fires independently.
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("b", repeated, at(50)))
    }

    @Test
    fun `after firing the rule rests - launches during the cooldown proceed`() {
        val engine = OpenCheckEngine()
        engine.onLaunchAttempt("app", repeated, t0)
        engine.onLaunchAttempt("app", repeated, at(10))
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", repeated, at(20)))

        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", repeated, at(30)))
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", repeated, at(40)))
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", repeated, at(50)))
        // Still resting just before the half hour is up.
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", repeated, at(20 + 1799)))
    }

    @Test
    fun `the cooldown is per app - another app still fires while one rests`() {
        val engine = OpenCheckEngine()
        engine.onLaunchAttempt("a", repeated, t0)
        engine.onLaunchAttempt("a", repeated, at(10))
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("a", repeated, at(20)))

        engine.onLaunchAttempt("b", repeated, at(30))
        engine.onLaunchAttempt("b", repeated, at(40))
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("b", repeated, at(50)))
    }

    @Test
    fun `after the cooldown the window still rolls - recent launches count again`() {
        val engine = OpenCheckEngine()
        engine.onLaunchAttempt("app", repeated, t0)
        engine.onLaunchAttempt("app", repeated, at(10))
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", repeated, at(20)))

        // Two launches late in the cooldown stay inside the 15-minute window
        // once the rest ends, so continued autopilot is caught straight away.
        engine.onLaunchAttempt("app", repeated, at(20 + 1700))
        engine.onLaunchAttempt("app", repeated, at(20 + 1750))
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", repeated, at(20 + 1801)))
    }

    @Test
    fun `counting survives a snapshot restore`() {
        val engine = OpenCheckEngine()
        engine.onLaunchAttempt("app", repeated, t0)
        engine.onLaunchAttempt("app", repeated, at(10))

        val restored = OpenCheckEngine(engine.snapshot())

        assertIs<OpenCheckDecision.ShowCheck>(restored.onLaunchAttempt("app", repeated, at(20)))
    }

    @Test
    fun `the cooldown survives a snapshot restore`() {
        val engine = OpenCheckEngine()
        engine.onLaunchAttempt("app", repeated, t0)
        engine.onLaunchAttempt("app", repeated, at(10))
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", repeated, at(20)))

        val restored = OpenCheckEngine(engine.snapshot())

        assertEquals(OpenCheckDecision.Proceed, restored.onLaunchAttempt("app", repeated, at(30)))
    }
}
