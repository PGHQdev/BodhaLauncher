package com.bodhalauncher.engine

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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
    fun `snapshot and restore at any cut point replays like an unkilled engine`() {
        val events = listOf(
            DeviceEvent.ScreenOn(at(0)),
            DeviceEvent.Unlocked(at(1)),
            DeviceEvent.ScreenOff(at(60)),
            DeviceEvent.Unlocked(at(80)),
            DeviceEvent.ScreenOff(at(120)),
            DeviceEvent.ScreenOn(at(200)),
            DeviceEvent.ScreenOff(at(205)),
            DeviceEvent.Unlocked(at(300)),
            DeviceEvent.ScreenOff(at(360)),
        )
        val unkilled = run(*events.toTypedArray())

        for (cut in 0..events.size) {
            val before = SessionEngine()
            val prefix = events.take(cut).flatMap { before.onEvent(it) }
            val after = SessionEngine(before.snapshot())
            val suffix = events.drop(cut).flatMap { after.onEvent(it) }
            assertEquals(unkilled, prefix + suffix, "cut at $cut diverged")
        }
    }

    @Test
    fun `restart while unlocked with an active session continues it`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        val restored = SessionEngine(engine.snapshot())

        val onRestart = restored.onEvent(DeviceEvent.Restarted(at(500), interactive = true, keyguardLocked = false))
        assertEquals(emptyList(), onRestart)

        val ended = (restored.onEvent(DeviceEvent.ScreenOff(at(600))) + restored.advanceTo(at(700)))
            .filterIsInstance<Transition.SessionEnded>().single()
        assertEquals(SessionId(1), ended.session)
    }

    @Test
    fun `restart while unlocked inside the merge window resumes the session`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.ScreenOff(at(10)))
        val restored = SessionEngine(engine.snapshot())

        val transitions = restored.onEvent(DeviceEvent.Restarted(at(30), interactive = true, keyguardLocked = false))
        assertEquals(listOf<Transition>(Transition.SessionResumed(SessionId(1), at(30))), transitions)
    }

    @Test
    fun `restart while unlocked past the merge window ends the old session and starts a new one`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.ScreenOff(at(10)))
        val restored = SessionEngine(engine.snapshot())

        val transitions = restored.onEvent(DeviceEvent.Restarted(at(500), interactive = true, keyguardLocked = false))
        assertEquals(
            listOf(
                Transition.SessionEnded(SessionId(1), at(10)),
                Transition.SessionStarted(SessionId(2), at(500)),
            ),
            transitions,
        )
    }

    @Test
    fun `restart while unlocked with no prior session starts one`() {
        val restored = SessionEngine(SessionEngine().snapshot())

        val transitions = restored.onEvent(DeviceEvent.Restarted(at(500), interactive = true, keyguardLocked = false))
        assertEquals(1, transitions.filterIsInstance<Transition.SessionStarted>().size)
    }

    @Test
    fun `restart on the lock screen ends an active session at the last observed point`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.Unlocked(at(40)))
        val restored = SessionEngine(engine.snapshot())

        val transitions = restored.onEvent(DeviceEvent.Restarted(at(500), interactive = true, keyguardLocked = true))
        assertEquals(listOf<Transition>(Transition.SessionEnded(SessionId(1), at(40))), transitions)
    }

    @Test
    fun `restart on the lock screen that goes dark without an unlock is a peek`() {
        val restored = SessionEngine(SessionEngine().snapshot())
        restored.onEvent(DeviceEvent.Restarted(at(500), interactive = true, keyguardLocked = true))

        val transitions = restored.onEvent(DeviceEvent.ScreenOff(at(505)))
        assertEquals(listOf<Transition>(Transition.PeekObserved(at(505))), transitions)
    }

    @Test
    fun `quick restart on the lock screen keeps the merge window open`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.Unlocked(at(40)))
        val restored = SessionEngine(engine.snapshot())

        // Pocket relock + lift-to-wake: restart finds the lock screen 10s after last observation.
        val onRestart = restored.onEvent(DeviceEvent.Restarted(at(50), interactive = true, keyguardLocked = true))
        assertEquals(emptyList(), onRestart)

        val resumed = restored.onEvent(DeviceEvent.Unlocked(at(55)))
        assertEquals(listOf<Transition>(Transition.SessionResumed(SessionId(1), at(55))), resumed)
    }

    @Test
    fun `restart with screen off and no keyguard reconciles like any screen-off restart`() {
        // Lock screen set to "None": non-interactive polls as keyguardLocked = false.
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.Unlocked(at(40)))
        val restored = SessionEngine(engine.snapshot())

        val onRestart = restored.onEvent(DeviceEvent.Restarted(at(500), interactive = false, keyguardLocked = false))
        assertEquals(listOf<Transition>(Transition.SessionEnded(SessionId(1), at(40))), onRestart)
    }

    @Test
    fun `restart with screen off confirms an interrupted peek`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.ScreenOn(at(0)))
        val restored = SessionEngine(engine.snapshot())

        val onRestart = restored.onEvent(DeviceEvent.Restarted(at(500), interactive = false, keyguardLocked = true))
        assertEquals(listOf<Transition>(Transition.PeekObserved(at(0))), onRestart)
    }

    @Test
    fun `restart with the screen off treats an active session as provisionally ended`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.Unlocked(at(40)))
        val restored = SessionEngine(engine.snapshot())

        // Quick restart: still within the merge window of the last observed point.
        val onRestart = restored.onEvent(DeviceEvent.Restarted(at(50), interactive = false, keyguardLocked = true))
        assertEquals(emptyList(), onRestart)

        val resumed = restored.onEvent(DeviceEvent.Unlocked(at(60)))
        assertEquals(listOf<Transition>(Transition.SessionResumed(SessionId(1), at(60))), resumed)
    }

    @Test
    fun `restart with the screen off finalizes a session whose window has passed`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.ScreenOff(at(10)))
        val restored = SessionEngine(engine.snapshot())

        val onRestart = restored.onEvent(DeviceEvent.Restarted(at(500), interactive = false, keyguardLocked = true))
        assertEquals(listOf<Transition>(Transition.SessionEnded(SessionId(1), at(10))), onRestart)
    }

    @Test
    fun `backfill reconstructs sessions missed during process death`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        val restored = SessionEngine(engine.snapshot())

        // While dead: screen went off at 100, a full missed session ran 200–260.
        val transitions = restored.backfill(
            listOf(
                UsageRecord.ScreenNonInteractive(at(100)),
                UsageRecord.ScreenInteractive(at(200)),
                UsageRecord.KeyguardHidden(at(201)),
                UsageRecord.ScreenNonInteractive(at(260)),
            )
        )

        assertEquals(
            listOf(
                Transition.SessionEnded(SessionId(1), at(100)),
                Transition.SessionStarted(SessionId(2), at(201)),
            ),
            transitions,
        )
        // The last screen-off stays provisional for the restart reconciliation to settle.
        assertEquals(SessionPhase.ProvisionalEnd(SessionId(2), at(260)), restored.snapshot().phase)
    }

    @Test
    fun `backfilled sessions respect the merge window`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        val restored = SessionEngine(engine.snapshot())

        val transitions = restored.backfill(
            listOf(
                UsageRecord.ScreenNonInteractive(at(100)),
                UsageRecord.KeyguardHidden(at(120)),
            )
        )

        assertEquals(listOf<Transition>(Transition.SessionResumed(SessionId(1), at(120))), transitions)
    }

    @Test
    fun `backfilled screen-on without unlock is a peek`() {
        val restored = SessionEngine(SessionEngine().snapshot())

        val transitions = restored.backfill(
            listOf(
                UsageRecord.ScreenInteractive(at(100)),
                UsageRecord.ScreenNonInteractive(at(105)),
            )
        )

        assertEquals(listOf<Transition>(Transition.PeekObserved(at(105))), transitions)
    }

    @Test
    fun `backfill never rewrites what the engine already observed`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        engine.onEvent(DeviceEvent.ScreenOff(at(50)))
        val restored = SessionEngine(engine.snapshot())

        // UsageStats replays history the engine already recorded live, plus one new record.
        val transitions = restored.backfill(
            listOf(
                UsageRecord.KeyguardHidden(at(0)),
                UsageRecord.ScreenNonInteractive(at(50)),
                UsageRecord.KeyguardHidden(at(60)),
            )
        )

        assertEquals(listOf<Transition>(Transition.SessionResumed(SessionId(1), at(60))), transitions)
    }

    @Test
    fun `backfill with nothing new leaves the engine untouched`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        val restored = SessionEngine(engine.snapshot())
        val before = restored.snapshot()

        assertEquals(emptyList(), restored.backfill(emptyList()))
        assertEquals(emptyList(), restored.backfill(listOf(UsageRecord.KeyguardHidden(at(0)))))
        assertEquals(before, restored.snapshot())
    }

    @Test
    fun `backfill sorts records and composes with restart reconciliation`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        val restored = SessionEngine(engine.snapshot())

        restored.backfill(
            listOf(
                UsageRecord.KeyguardHidden(at(201)),
                UsageRecord.ScreenNonInteractive(at(100)),
                UsageRecord.ScreenInteractive(at(200)),
            )
        )
        val onRestart = restored.onEvent(DeviceEvent.Restarted(at(210), interactive = true, keyguardLocked = false))

        assertEquals(emptyList(), onRestart)
        assertEquals(SessionPhase.Active(SessionId(2)), restored.snapshot().phase)
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

    @Test
    fun `session-scoped state keeps its key across the merge window and loses it at the end`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        val opened = engine.snapshot().phase.sessionOrNull

        engine.onEvent(DeviceEvent.ScreenOff(at(60)))
        assertEquals(opened, engine.snapshot().phase.sessionOrNull)

        engine.onEvent(DeviceEvent.Unlocked(at(80)))
        assertEquals(opened, engine.snapshot().phase.sessionOrNull)

        engine.onEvent(DeviceEvent.ScreenOff(at(140)))
        engine.advanceTo(at(200))
        assertNull(engine.snapshot().phase.sessionOrNull)
    }

    @Test
    fun `the next session is a different key`() {
        val engine = SessionEngine()
        engine.onEvent(DeviceEvent.Unlocked(at(0)))
        val first = engine.snapshot().phase.sessionOrNull

        engine.onEvent(DeviceEvent.ScreenOff(at(60)))
        engine.advanceTo(at(120))
        engine.onEvent(DeviceEvent.Unlocked(at(200)))

        assertNotEquals(first, engine.snapshot().phase.sessionOrNull)
    }
}
