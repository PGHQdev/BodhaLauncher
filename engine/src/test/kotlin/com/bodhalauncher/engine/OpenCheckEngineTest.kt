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
}
