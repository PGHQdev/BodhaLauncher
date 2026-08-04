package com.bodhalauncher.engine

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductMetricsTest {

    private val dayStart = LocalDateTime.parse("2026-08-03T04:00:00")
    private val weekLater = dayStart.plusDays(7)

    private fun at(minutes: Long) = dayStart.plusMinutes(minutes)
    private fun event(type: EventType, minutes: Long, valueMillis: Long? = null) =
        LoggedEvent(type, at(minutes), valueMillis)

    @Test
    fun `an empty log yields no metrics rather than zeros`() {
        val metrics = computeMetrics(emptyList(), dayStart, weekLater)

        assertNull(metrics.intentionalSessionRatio)
        assertNull(metrics.medianUsefulActionMillis)
        assertNull(metrics.focusCompletionRate)
        assertNull(metrics.openCheckReturnRate)
    }

    @Test
    fun `intentional-session ratio is answered prompts over sessions`() {
        val events = listOf(
            event(EventType.SessionStarted, 0),
            event(EventType.IntentPromptAnswered, 1),
            event(EventType.SessionStarted, 60),
            event(EventType.SessionStarted, 120),
            event(EventType.IntentPromptAnswered, 121),
            event(EventType.SessionStarted, 180),
        )

        val metrics = computeMetrics(events, dayStart, weekLater)

        assertEquals(0.5, metrics.intentionalSessionRatio)
    }

    @Test
    fun `median useful-action time spans session start to first app launch`() {
        val events = listOf(
            event(EventType.SessionStarted, 0),
            event(EventType.AppLaunched, 1),          // 60s
            event(EventType.AppLaunched, 2),          // ignored: not the first
            event(EventType.SessionStarted, 60),
            event(EventType.AppLaunched, 63),         // 180s
            event(EventType.SessionStarted, 120),     // no launch: no sample
        )

        val metrics = computeMetrics(events, dayStart, weekLater)

        assertEquals(120_000L, metrics.medianUsefulActionMillis)
    }

    @Test
    fun `focus completion is completed over started`() {
        val events = listOf(
            event(EventType.FocusStarted, 0),
            event(EventType.FocusCompleted, 25),
            event(EventType.FocusStarted, 60),
            event(EventType.FocusAbandoned, 70),
            event(EventType.FocusStarted, 120),
            event(EventType.FocusCompleted, 145),
            event(EventType.FocusStarted, 200),
        )

        val metrics = computeMetrics(events, dayStart, weekLater)

        assertEquals(0.5, metrics.focusCompletionRate)
    }

    @Test
    fun `open check return rate is turned-back over displayed`() {
        val events = listOf(
            event(EventType.OpenCheckDisplayed, 0),
            event(EventType.OpenCheckTurnedBack, 1),
            event(EventType.OpenCheckDisplayed, 10),
            event(EventType.OpenCheckProceeded, 11),
            event(EventType.OpenCheckDisplayed, 20),
            event(EventType.OpenCheckTurnedBack, 21),
            event(EventType.OpenCheckDisplayed, 30),
            event(EventType.OpenCheckProceeded, 31),
        )

        val metrics = computeMetrics(events, dayStart, weekLater)

        assertEquals(0.5, metrics.openCheckReturnRate)
    }

    @Test
    fun `repeated opens average per day across the window`() {
        val events = (0 until 14).map { event(EventType.RepeatedOpenDetected, it * 60L) }

        val metrics = computeMetrics(events, dayStart, weekLater)

        assertEquals(2.0, metrics.repeatedOpensPerDay)
    }

    @Test
    fun `four rapid launches form one switching burst`() {
        val base = listOf(
            event(EventType.AppLaunched, 0),
            LoggedEvent(EventType.AppLaunched, at(0).plusSeconds(15)),
            LoggedEvent(EventType.AppLaunched, at(0).plusSeconds(30)),
            LoggedEvent(EventType.AppLaunched, at(0).plusSeconds(45)),
        )
        val calm = listOf(
            event(EventType.AppLaunched, 300),
            event(EventType.AppLaunched, 400),
        )

        val metrics = computeMetrics(base + calm, dayStart, dayStart.plusDays(1))

        assertEquals(1.0, metrics.appSwitchingBurstsPerDay)
    }

    @Test
    fun `events outside the window are ignored`() {
        val events = listOf(
            LoggedEvent(EventType.SessionStarted, dayStart.minusMinutes(5)),
            LoggedEvent(EventType.SessionStarted, weekLater.plusMinutes(5)),
            event(EventType.SessionStarted, 10),
            event(EventType.IntentPromptAnswered, 11),
        )

        val metrics = computeMetrics(events, dayStart, weekLater)

        assertEquals(1.0, metrics.intentionalSessionRatio)
    }

    @Test
    fun `events carry no free text by construction`() {
        // The type has exactly: type, timestamp, optional duration. This test pins the shape.
        val event = LoggedEvent(EventType.PerformanceMark, dayStart, valueMillis = 12)

        assertEquals(EventType.PerformanceMark, event.type)
        assertEquals(12L, event.valueMillis)
    }
}
