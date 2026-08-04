package com.bodhalauncher.engine

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionEngineTest {

    private val t0: Instant = Instant.parse("2026-08-04T09:00:00Z")
    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)

    private fun run(vararg events: DeviceEvent): List<Transition> {
        val engine = SessionEngine()
        return events.flatMap { engine.onEvent(it) }
    }

    @Test
    fun `unlock starts a session and screen-off ends it with the same id`() {
        val transitions = run(
            DeviceEvent.ScreenOn(at(0)),
            DeviceEvent.Unlocked(at(1)),
            DeviceEvent.ScreenOff(at(60)),
        )

        val started = transitions.filterIsInstance<Transition.SessionStarted>().single()
        val ended = transitions.filterIsInstance<Transition.SessionEnded>().single()
        assertEquals(at(1), started.at)
        assertEquals(at(60), ended.at)
        assertEquals(started.session, ended.session)
    }

    @Test
    fun `screen-on without unlock is a peek, not a session`() {
        val transitions = run(
            DeviceEvent.ScreenOn(at(0)),
            DeviceEvent.ScreenOff(at(5)),
        )

        assertEquals(listOf<Transition>(Transition.PeekObserved(at(5))), transitions)
    }

    @Test
    fun `unlocked session produces no peek`() {
        val transitions = run(
            DeviceEvent.ScreenOn(at(0)),
            DeviceEvent.Unlocked(at(1)),
            DeviceEvent.ScreenOff(at(60)),
        )

        assertEquals(emptyList(), transitions.filterIsInstance<Transition.PeekObserved>())
    }

    @Test
    fun `every wake starts a session when every wake unlocks`() {
        // Lock screen set to "None": USER_PRESENT fires on every wake (ADR 0001 degraded mode).
        val transitions = run(
            DeviceEvent.ScreenOn(at(0)),
            DeviceEvent.Unlocked(at(0)),
            DeviceEvent.ScreenOff(at(30)),
            DeviceEvent.ScreenOn(at(300)),
            DeviceEvent.Unlocked(at(300)),
            DeviceEvent.ScreenOff(at(330)),
        )

        val started = transitions.filterIsInstance<Transition.SessionStarted>()
        assertEquals(2, started.size)
        assertEquals(2, started.map { it.session }.distinct().size)
    }

    @Test
    fun `unlock without a preceding screen-on still starts a session`() {
        val transitions = run(
            DeviceEvent.Unlocked(at(0)),
            DeviceEvent.ScreenOff(at(10)),
        )

        assertEquals(1, transitions.filterIsInstance<Transition.SessionStarted>().size)
        assertEquals(1, transitions.filterIsInstance<Transition.SessionEnded>().size)
    }

    @Test
    fun `duplicate unlock during an active session is ignored`() {
        val transitions = run(
            DeviceEvent.Unlocked(at(0)),
            DeviceEvent.Unlocked(at(5)),
            DeviceEvent.ScreenOff(at(10)),
        )

        assertEquals(1, transitions.filterIsInstance<Transition.SessionStarted>().size)
    }

    @Test
    fun `screen-off with no session and no screen-on emits nothing`() {
        val transitions = run(DeviceEvent.ScreenOff(at(0)))

        assertEquals(emptyList(), transitions)
    }

    @Test
    fun `same event sequence always produces the same transitions`() {
        val events = arrayOf(
            DeviceEvent.ScreenOn(at(0)),
            DeviceEvent.Unlocked(at(1)),
            DeviceEvent.ScreenOff(at(60)),
            DeviceEvent.ScreenOn(at(120)),
            DeviceEvent.ScreenOff(at(125)),
            DeviceEvent.Unlocked(at(200)),
            DeviceEvent.ScreenOff(at(260)),
        )

        assertEquals(run(*events), run(*events))
    }
}
