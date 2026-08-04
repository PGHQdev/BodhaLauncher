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

    /** Runs the events, then advances the clock far enough to finalize any provisional end. */
    private fun runAndSettle(vararg events: DeviceEvent): List<Transition> {
        val engine = SessionEngine()
        val transitions = events.flatMap { engine.onEvent(it) }
        return transitions + engine.advanceTo(events.last().at.plusSeconds(60))
    }

    @Test
    fun `unlock starts a session and screen-off ends it with the same id`() {
        val transitions = runAndSettle(
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
        val transitions = runAndSettle(
            DeviceEvent.ScreenOn(at(0)),
            DeviceEvent.Unlocked(at(1)),
            DeviceEvent.ScreenOff(at(60)),
        )

        assertEquals(emptyList(), transitions.filterIsInstance<Transition.PeekObserved>())
    }

    @Test
    fun `every wake starts a session when every wake unlocks`() {
        // Lock screen set to "None": USER_PRESENT fires on every wake (ADR 0001 degraded mode).
        val transitions = runAndSettle(
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
        val transitions = runAndSettle(
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
    fun `re-unlock within the merge window resumes the same session`() {
        val transitions = run(
            DeviceEvent.Unlocked(at(0)),
            DeviceEvent.ScreenOff(at(10)),
            DeviceEvent.Unlocked(at(20)),
        )

        val started = transitions.filterIsInstance<Transition.SessionStarted>().single()
        val resumed = transitions.filterIsInstance<Transition.SessionResumed>().single()
        assertEquals(started.session, resumed.session)
        assertEquals(at(20), resumed.at)
        assertEquals(emptyList(), transitions.filterIsInstance<Transition.SessionEnded>())
    }

    @Test
    fun `re-unlock at exactly 30s resumes — the window is inclusive`() {
        val transitions = run(
            DeviceEvent.Unlocked(at(0)),
            DeviceEvent.ScreenOff(at(10)),
            DeviceEvent.Unlocked(at(40)),
        )

        assertEquals(1, transitions.filterIsInstance<Transition.SessionResumed>().size)
        assertEquals(emptyList(), transitions.filterIsInstance<Transition.SessionEnded>())
    }

    @Test
    fun `re-unlock after the merge window ends the old session and starts a new one`() {
        val transitions = run(
            DeviceEvent.Unlocked(at(0)),
            DeviceEvent.ScreenOff(at(10)),
            DeviceEvent.Unlocked(at(41)),
        )

        val started = transitions.filterIsInstance<Transition.SessionStarted>()
        val ended = transitions.filterIsInstance<Transition.SessionEnded>().single()
        assertEquals(2, started.size)
        assertEquals(2, started.map { it.session }.distinct().size)
        assertEquals(started[0].session, ended.session)
        assertEquals(at(10), ended.at)
    }

    @Test
    fun `a chain of relocks inside the window stays one session`() {
        val transitions = runAndSettle(
            DeviceEvent.Unlocked(at(0)),
            DeviceEvent.ScreenOff(at(10)),
            DeviceEvent.Unlocked(at(25)),
            DeviceEvent.ScreenOff(at(30)),
            DeviceEvent.Unlocked(at(50)),
            DeviceEvent.ScreenOff(at(60)),
        )

        assertEquals(1, transitions.filterIsInstance<Transition.SessionStarted>().size)
        assertEquals(2, transitions.filterIsInstance<Transition.SessionResumed>().size)
        val ended = transitions.filterIsInstance<Transition.SessionEnded>().single()
        assertEquals(at(60), ended.at)
    }

    @Test
    fun `session end is final only once the merge window passes`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.ScreenOff(at(10)))

        assertEquals(emptyList(), engine.advanceTo(at(40)))

        val ended = engine.advanceTo(at(41)).filterIsInstance<Transition.SessionEnded>().single()
        assertEquals(at(10), ended.at)
    }

    @Test
    fun `finalization is also observable on the next device event`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.ScreenOff(at(10)))

        val transitions = engine.onEvent(DeviceEvent.ScreenOn(at(100)))
        val ended = transitions.filterIsInstance<Transition.SessionEnded>().single()
        assertEquals(at(10), ended.at)
    }

    @Test
    fun `a peek during the merge window does not disturb the resumable session`() {
        val transitions = run(
            DeviceEvent.Unlocked(at(0)),
            DeviceEvent.ScreenOff(at(10)),
            DeviceEvent.ScreenOn(at(12)),
            DeviceEvent.ScreenOff(at(14)),
            DeviceEvent.Unlocked(at(20)),
        )

        assertEquals(listOf(Transition.PeekObserved(at(14))), transitions.filterIsInstance<Transition.PeekObserved>())
        assertEquals(1, transitions.filterIsInstance<Transition.SessionResumed>().size)
        assertEquals(emptyList(), transitions.filterIsInstance<Transition.SessionEnded>())
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
