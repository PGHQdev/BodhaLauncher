package com.bodhalauncher.engine

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IntentPromptEngineTest {

    private val t0: Instant = Instant.parse("2026-08-04T09:00:00Z")
    private fun at(minutes: Long): Instant = t0.plusSeconds(minutes * 60)

    private fun started(n: Long, at: Instant) = Transition.SessionStarted(SessionId(n), at)

    /** Feeds n session starts spaced [gapMinutes] apart and returns the decisions per start. */
    private fun startSessions(
        engine: IntentPromptEngine,
        count: Int,
        gapMinutes: Long,
        suppression: SuppressionFlags = SuppressionFlags(),
    ): List<PromptDecision?> = (0 until count).map { i ->
        engine.onTransition(started(i + 1L, at(i * gapMinutes)), suppression)
    }

    @Test
    fun `third session within the window fires, second does not`() {
        val decisions = startSessions(IntentPromptEngine(), count = 3, gapMinutes = 5)

        assertNull(decisions[0])
        assertNull(decisions[1])
        val fired = assertNotNull(decisions[2])
        assertEquals(SessionId(3), fired.session)
        assertEquals(at(10), fired.at)
        assertEquals(TriggerSource.Reflexive, fired.trigger)
    }

    @Test
    fun `resumed sessions do not count`() {
        val engine = IntentPromptEngine()
        assertNull(engine.onTransition(started(1, at(0)), SuppressionFlags()))
        assertNull(engine.onTransition(Transition.SessionResumed(SessionId(1), at(2)), SuppressionFlags()))
        assertNull(engine.onTransition(Transition.SessionResumed(SessionId(1), at(4)), SuppressionFlags()))
        assertNull(engine.onTransition(started(2, at(6)), SuppressionFlags()))
    }

    @Test
    fun `peeks and session ends do not count`() {
        val engine = IntentPromptEngine()
        assertNull(engine.onTransition(started(1, at(0)), SuppressionFlags()))
        assertNull(engine.onTransition(Transition.PeekObserved(at(1)), SuppressionFlags()))
        assertNull(engine.onTransition(Transition.SessionEnded(SessionId(1), at(2)), SuppressionFlags()))
        assertNull(engine.onTransition(started(2, at(3)), SuppressionFlags()))
    }

    @Test
    fun `window slides - old session starts age out`() {
        val engine = IntentPromptEngine()
        assertNull(engine.onTransition(started(1, at(0)), SuppressionFlags()))
        assertNull(engine.onTransition(started(2, at(14)), SuppressionFlags()))
        // First start is now 16 minutes old — only two starts in the window.
        assertNull(engine.onTransition(started(3, at(16)), SuppressionFlags()))
        // Three within 15 minutes again: starts at 14, 16, 25.
        assertNotNull(engine.onTransition(started(4, at(25)), SuppressionFlags()))
    }

    @Test
    fun `cooldown blocks the next would-be trigger and then expires`() {
        val engine = IntentPromptEngine()
        startSessions(engine, count = 3, gapMinutes = 5) // fires at minute 10
        // Three more rapid sessions inside the 30-minute cooldown (ends at minute 40).
        assertNull(engine.onTransition(started(4, at(12)), SuppressionFlags()))
        assertNull(engine.onTransition(started(5, at(14)), SuppressionFlags()))
        assertNull(engine.onTransition(started(6, at(16)), SuppressionFlags()))
        // After the cooldown, a fresh reflexive pattern fires again.
        assertNull(engine.onTransition(started(7, at(41)), SuppressionFlags()))
        assertNull(engine.onTransition(started(8, at(43)), SuppressionFlags()))
        assertNotNull(engine.onTransition(started(9, at(45)), SuppressionFlags()))
    }

    @Test
    fun `each suppression flag independently blocks`() {
        val flags = listOf(
            SuppressionFlags(callActive = true),
            SuppressionFlags(navigationActive = true),
            SuppressionFlags(cameraViaShortcut = true),
            SuppressionFlags(emergencyFlow = true),
            SuppressionFlags(returningToFocusTask = true),
        )
        for (suppression in flags) {
            val decisions = startSessions(IntentPromptEngine(), count = 3, gapMinutes = 5, suppression = suppression)
            assertEquals(listOf(null, null, null), decisions, "expected $suppression to block")
        }
    }

    @Test
    fun `a suppressed trigger starts no cooldown`() {
        val engine = IntentPromptEngine()
        assertNull(engine.onTransition(started(1, at(0)), SuppressionFlags()))
        assertNull(engine.onTransition(started(2, at(5)), SuppressionFlags()))
        assertNull(engine.onTransition(started(3, at(10)), SuppressionFlags(callActive = true)))
        // Next session in the window fires — the suppressed one consumed nothing.
        assertNotNull(engine.onTransition(started(4, at(12)), SuppressionFlags()))
    }

    @Test
    fun `ask every time fires on every session start`() {
        val engine = IntentPromptEngine(config = IntentPromptConfig(askEveryTime = true))
        val first = assertNotNull(engine.onTransition(started(1, at(0)), SuppressionFlags()))
        assertEquals(TriggerSource.EverySession, first.trigger)
        // Even long after, and with no reflexive pattern.
        assertNotNull(engine.onTransition(started(2, at(120)), SuppressionFlags()))
    }

    @Test
    fun `ask every time ignores the cooldown a previous fire started`() {
        val engine = IntentPromptEngine(config = IntentPromptConfig(askEveryTime = true))
        assertNotNull(engine.onTransition(started(1, at(0)), SuppressionFlags()))
        // Well inside the 30-minute cooldown a reflexive fire would observe.
        assertNotNull(engine.onTransition(started(2, at(5)), SuppressionFlags()))
    }

    @Test
    fun `ask every time is still suppressed`() {
        val engine = IntentPromptEngine(config = IntentPromptConfig(askEveryTime = true))
        assertNull(engine.onTransition(started(1, at(0)), SuppressionFlags(callActive = true)))
    }

    @Test
    fun `state round-trip continues the count across reconstruction`() {
        val first = IntentPromptEngine()
        first.onTransition(started(1, at(0)), SuppressionFlags())
        first.onTransition(started(2, at(5)), SuppressionFlags())

        val second = IntentPromptEngine(initial = first.snapshot())
        assertNotNull(second.onTransition(started(3, at(10)), SuppressionFlags()))
    }

    @Test
    fun `state round-trip continues the cooldown across reconstruction`() {
        val first = IntentPromptEngine()
        startSessions(first, count = 3, gapMinutes = 5) // fires at minute 10, cooldown to 40

        val second = IntentPromptEngine(initial = first.snapshot())
        assertNull(second.onTransition(started(4, at(12)), SuppressionFlags()))
        assertNull(second.onTransition(started(5, at(14)), SuppressionFlags()))
        assertNull(second.onTransition(started(6, at(16)), SuppressionFlags()))
    }
}
