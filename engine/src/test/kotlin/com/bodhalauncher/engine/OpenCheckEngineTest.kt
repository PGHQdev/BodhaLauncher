package com.bodhalauncher.engine

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
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

    // Timed sessions (#75): "Open for N minutes" grants the opening and records
    // the expiry; the session-end moment and its choices live in this seam.

    @Test
    fun `opening for a duration grants the opening like a plain proceed`() {
        val engine = OpenCheckEngine()
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, t0))

        engine.onProceededFor("app", t0, minutes = 10)

        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", always, at(2)))
    }

    @Test
    fun `no session-end before the time completes`() {
        val engine = OpenCheckEngine()
        engine.onProceededFor("app", t0, minutes = 10)

        assertEquals(null, engine.advanceTo(at(599)))
    }

    @Test
    fun `the session-end moment is due when the time completes`() {
        val engine = OpenCheckEngine()
        engine.onProceededFor("app", t0, minutes = 10)

        val due = engine.advanceTo(at(600))

        assertEquals("app", due?.timedSession?.appId)
        assertEquals(10, due?.timedSession?.plannedMinutes)
        assertEquals(0, due?.overByMillis)
    }

    @Test
    fun `a late moment reports how far past the boundary it is`() {
        val engine = OpenCheckEngine()
        engine.onProceededFor("app", t0, minutes = 10)

        val due = engine.advanceTo(at(600 + 23 * 60))

        assertEquals(23 * 60_000L, due?.overByMillis)
    }

    @Test
    fun `the expiry survives a snapshot restore`() {
        val engine = OpenCheckEngine()
        engine.onProceededFor("app", t0, minutes = 10)

        val restored = OpenCheckEngine(engine.snapshot())

        assertEquals("app", restored.advanceTo(at(600))?.timedSession?.appId)
    }

    @Test
    fun `add five extends from the moment of choice`() {
        val engine = OpenCheckEngine()
        engine.onProceededFor("app", t0, minutes = 10)
        assertEquals("app", engine.advanceTo(at(600))?.timedSession?.appId)

        engine.onSessionEndAddFive(at(660))

        assertEquals(null, engine.advanceTo(at(660 + 299)))
        val due = engine.advanceTo(at(660 + 300))
        assertEquals(15, due?.timedSession?.plannedMinutes)
    }

    @Test
    fun `add five grants the reopening`() {
        val engine = OpenCheckEngine()
        engine.onProceededFor("app", t0, minutes = 10)
        engine.advanceTo(at(600))

        engine.onSessionEndAddFive(at(660))

        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", always, at(661)))
    }

    @Test
    fun `continue without a timer clears the session and grants the reopening`() {
        val engine = OpenCheckEngine()
        engine.onProceededFor("app", t0, minutes = 10)
        engine.advanceTo(at(600))

        engine.onSessionEndContinue(at(660))

        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", always, at(661)))
        assertEquals(null, engine.advanceTo(at(7200)))
    }

    @Test
    fun `close clears the session and grants nothing`() {
        val engine = OpenCheckEngine()
        engine.onProceededFor("app", t0, minutes = 10)
        engine.advanceTo(at(600))

        engine.onSessionEndClose()

        assertEquals(null, engine.advanceTo(at(7200)))
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", always, at(700)))
    }

    @Test
    fun `a plain proceed for the same app supersedes a pending timed session`() {
        val engine = OpenCheckEngine()
        engine.onProceededFor("app", t0, minutes = 10)

        engine.onProceeded("app", at(300))

        assertEquals(null, engine.advanceTo(at(7200)))
    }

    @Test
    fun `the session-end phrase is calm on time and honest when late`() {
        assertEquals("Your 10 minutes are complete.", sessionEndPhrase(10, overByMillis = 0))
        assertEquals("Your 10 minutes are complete.", sessionEndPhrase(10, overByMillis = 59_000))
        assertEquals(
            "Your 10 minutes ended 23 minutes ago.",
            sessionEndPhrase(10, overByMillis = 23 * 60_000L),
        )
    }

    // Daily-usage-threshold trigger (#73). Usage is sampled by the adapter
    // since the 4am boundary; null means no usage access and an inert trigger.

    private val thirtyDaily = OpenCheckRule(OpenCheckMode.DailyThreshold, dailyThreshold = Duration.ofMinutes(30))

    @Test
    fun `below today's threshold the app just opens`() {
        val context = OpenCheckContext(usedTodayMillis = Duration.ofMinutes(29).toMillis())
        assertEquals(OpenCheckDecision.Proceed, OpenCheckEngine().onLaunchAttempt("app", thirtyDaily, t0, context))
    }

    @Test
    fun `at the threshold the check shows`() {
        val context = OpenCheckContext(usedTodayMillis = Duration.ofMinutes(30).toMillis())
        assertIs<OpenCheckDecision.ShowCheck>(OpenCheckEngine().onLaunchAttempt("app", thirtyDaily, t0, context))
    }

    @Test
    fun `without usage access the threshold trigger is inert`() {
        val context = OpenCheckContext(usedTodayMillis = null)
        assertEquals(OpenCheckDecision.Proceed, OpenCheckEngine().onLaunchAttempt("app", thirtyDaily, t0, context))
    }

    @Test
    fun `a threshold rule without a duration is inert`() {
        val rule = OpenCheckRule(OpenCheckMode.DailyThreshold)
        val context = OpenCheckContext(usedTodayMillis = Duration.ofHours(9).toMillis())
        assertEquals(OpenCheckDecision.Proceed, OpenCheckEngine().onLaunchAttempt("app", rule, t0, context))
    }

    @Test
    fun `today begins at 4am - late-night use belongs to the evening`() {
        // The adapter samples usage since dayStart; these pin the boundary (ADR 0003).
        assertEquals(
            LocalDateTime.of(2026, 8, 3, 4, 0),
            dayStart(LocalDateTime.of(2026, 8, 4, 1, 30)),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 4, 4, 0),
            dayStart(LocalDateTime.of(2026, 8, 4, 5, 0)),
        )
    }

    // Schedule trigger (#74): one daily window, evaluated purely from the
    // minute-of-day the adapter passes in; it may cross midnight.

    private val evenings = OpenCheckRule(
        OpenCheckMode.Schedule,
        window = ScheduleWindow(startMinute = 21 * 60, endMinute = 23 * 60),
    )

    @Test
    fun `inside the window the check shows`() {
        val context = OpenCheckContext(minuteOfDay = 22 * 60)
        assertIs<OpenCheckDecision.ShowCheck>(OpenCheckEngine().onLaunchAttempt("app", evenings, t0, context))
    }

    @Test
    fun `outside the window the app opens unchanged`() {
        val context = OpenCheckContext(minuteOfDay = 12 * 60)
        assertEquals(OpenCheckDecision.Proceed, OpenCheckEngine().onLaunchAttempt("app", evenings, t0, context))
    }

    @Test
    fun `the window starts inclusive and ends exclusive`() {
        val engine = OpenCheckEngine()
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", evenings, t0, OpenCheckContext(minuteOfDay = 21 * 60)))
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", evenings, t0, OpenCheckContext(minuteOfDay = 23 * 60)))
    }

    @Test
    fun `a midnight-crossing window checks on both sides of midnight`() {
        val lateNight = OpenCheckRule(
            OpenCheckMode.Schedule,
            window = ScheduleWindow(startMinute = 21 * 60, endMinute = 2 * 60),
        )
        val engine = OpenCheckEngine()
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", lateNight, t0, OpenCheckContext(minuteOfDay = 23 * 60)))
        assertIs<OpenCheckDecision.ShowCheck>(engine.onLaunchAttempt("app", lateNight, t0, OpenCheckContext(minuteOfDay = 1 * 60)))
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", lateNight, t0, OpenCheckContext(minuteOfDay = 3 * 60)))
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", lateNight, t0, OpenCheckContext(minuteOfDay = 12 * 60)))
    }

    @Test
    fun `a schedule rule without a window is inert`() {
        val rule = OpenCheckRule(OpenCheckMode.Schedule)
        assertEquals(OpenCheckDecision.Proceed, OpenCheckEngine().onLaunchAttempt("app", rule, t0, OpenCheckContext(minuteOfDay = 0)))
    }

    // Bypass (#77): friction never stands between the user and a call or alarm.

    @Test
    fun `a bypassed app always proceeds whatever its rule`() {
        val context = OpenCheckContext(bypass = true)
        val engine = OpenCheckEngine()
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", always, t0, context))
        assertEquals(OpenCheckDecision.Proceed, engine.onLaunchAttempt("app", thirtyDaily, t0,
            OpenCheckContext(usedTodayMillis = Duration.ofHours(5).toMillis(), bypass = true)))
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
