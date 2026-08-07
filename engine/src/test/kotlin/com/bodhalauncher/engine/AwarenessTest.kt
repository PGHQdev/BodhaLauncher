package com.bodhalauncher.engine

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AwarenessTest {

    private val now = LocalDateTime.of(2026, 8, 7, 14, 0)

    private fun record(id: Long, start: LocalDateTime, end: LocalDateTime?) =
        SessionRecord(id = id, start = start, end = end)

    @Test
    fun `no records resolves to the named absence, not zero`() {
        assertEquals(AwarenessToday.None, resolveAwarenessToday(emptyList(), now))
        assertEquals("No sessions yet today", awarenessTodayLine(AwarenessToday.None))
    }

    @Test
    fun `closed sessions today are counted`() {
        val records = (1L..6L).map {
            record(it, now.minusHours(it), now.minusHours(it).plusMinutes(10))
        }
        assertEquals(
            AwarenessToday.Sessions(finished = 6, running = false),
            resolveAwarenessToday(records, now),
        )
        assertEquals("6 sessions today", awarenessTodayLine(AwarenessToday.Sessions(6, running = false)))
        assertEquals("1 session today", awarenessTodayLine(AwarenessToday.Sessions(1, running = false)))
    }

    @Test
    fun `a running session shows as running, never as a zero-length one`() {
        val records = listOf(record(1, now.minusMinutes(5), end = null))
        assertEquals(
            AwarenessToday.Sessions(finished = 0, running = true),
            resolveAwarenessToday(records, now),
        )
        assertEquals("A session is running now", awarenessTodayLine(AwarenessToday.Sessions(0, running = true)))
        assertEquals(
            "2 sessions today · one running now",
            awarenessTodayLine(AwarenessToday.Sessions(2, running = true)),
        )
    }

    @Test
    fun `a session crossing 4am belongs to the day it started`() {
        // Started 3:30am on the 7th: under the 4am boundary that is still the 6th.
        val beforeBoundary = record(
            1,
            start = LocalDateTime.of(2026, 8, 7, 3, 30),
            end = LocalDateTime.of(2026, 8, 7, 4, 30),
        )
        assertEquals(AwarenessToday.None, resolveAwarenessToday(listOf(beforeBoundary), now))

        // Started 4:30am on the 7th: today's.
        val afterBoundary = record(
            2,
            start = LocalDateTime.of(2026, 8, 7, 4, 30),
            end = LocalDateTime.of(2026, 8, 7, 5, 0),
        )
        assertEquals(
            AwarenessToday.Sessions(finished = 1, running = false),
            resolveAwarenessToday(listOf(afterBoundary), now),
        )
    }

    @Test
    fun `a session still open from before the boundary still shows as running`() {
        val openSinceYesterday = record(1, start = LocalDateTime.of(2026, 8, 7, 3, 30), end = null)
        assertEquals(
            AwarenessToday.Sessions(finished = 0, running = true),
            resolveAwarenessToday(listOf(openSinceYesterday), now),
        )
    }

    @Test
    fun `a session is intentional exactly when one of the three signals is present`() {
        val session = record(1, now.minusMinutes(30), now.minusMinutes(20))
        val inside = IntentSignal(at = now.minusMinutes(25))
        val outside = IntentSignal(at = now.minusMinutes(5))

        assertEquals(
            listOf(true),
            resolveAwarenessSessions(listOf(session), listOf(inside)).map { it.intentional },
        )
        assertEquals(
            listOf(false),
            resolveAwarenessSessions(listOf(session), listOf(outside)).map { it.intentional },
        )
        assertEquals(
            listOf(false),
            resolveAwarenessSessions(listOf(session), emptyList()).map { it.intentional },
        )
    }

    @Test
    fun `a signal naming its session is attributed by that name, not by the clock`() {
        // A prompt answer carries the id it was asked under; the timestamp here
        // falls in the other session, and the id is what decides.
        val first = record(1, now.minusMinutes(40), now.minusMinutes(30))
        val second = record(2, now.minusMinutes(20), now.minusMinutes(10))
        val named = IntentSignal(at = now.minusMinutes(15), session = 1)

        assertEquals(
            listOf(true, false),
            resolveAwarenessSessions(listOf(first, second), listOf(named)).map { it.intentional },
        )
    }

    @Test
    fun `an Open Check intention carries no session and is attributed by the span it fell in`() {
        val first = record(1, now.minusMinutes(40), now.minusMinutes(30))
        val second = record(2, now.minusMinutes(20), now.minusMinutes(10))
        val unnamed = IntentSignal(at = now.minusMinutes(15))

        assertEquals(
            listOf(false, true),
            resolveAwarenessSessions(listOf(first, second), listOf(unnamed)).map { it.intentional },
        )
    }

    @Test
    fun `an open session takes everything stated after it started`() {
        val running = record(1, now.minusMinutes(10), end = null)

        assertEquals(
            listOf(true),
            resolveAwarenessSessions(listOf(running), listOf(IntentSignal(at = now))).map { it.intentional },
        )
        assertEquals(
            listOf(false),
            resolveAwarenessSessions(listOf(running), listOf(IntentSignal(at = now.minusMinutes(11))))
                .map { it.intentional },
        )
    }

    @Test
    fun `sessions come out in time order whatever order they arrive in`() {
        val later = record(2, now.minusMinutes(10), now.minusMinutes(5))
        val earlier = record(1, now.minusMinutes(40), now.minusMinutes(30))

        assertEquals(
            listOf(1L, 2L),
            resolveAwarenessSessions(listOf(later, earlier), emptyList()).map { it.record.id },
        )
    }

    @Test
    fun `a row says when it started and how long it ran, and marks nothing short`() {
        fun line(from: LocalDateTime, to: LocalDateTime?) =
            awarenessSessionLine(AwarenessSession(record(1, from, to), intentional = false))

        val start = LocalDateTime.of(2026, 8, 7, 9, 41)
        assertEquals("9:41 · 12 minutes", line(start, start.plusMinutes(12)))
        assertEquals("9:41 · 1 hour 5 minutes", line(start, start.plusMinutes(65)))
        assertEquals("9:41 · under a minute", line(start, start.plusSeconds(20)))
        assertEquals("9:41 · running now", line(start, null))
    }

    @Test
    fun `the classification is a word, and unintentional is never one of them`() {
        assertEquals("Intentional", awarenessIntentWord(intentional = true))
        assertEquals("Unclassified", awarenessIntentWord(intentional = false))
        listOf(true, false).forEach {
            assert(!awarenessIntentWord(it).lowercase().contains("unintentional")) {
                "the phone does not know (ADR 0013)"
            }
        }
    }

    // Awareness's Session view (#173): one session opened up.

    private val session = AwarenessSession(
        record(1, now.minusMinutes(30), now.minusMinutes(20)),
        intentional = true,
    )

    private fun launch(appId: String, minutesAgo: Long, session: Long?) =
        LaunchRecord(appId = appId, at = now.minusMinutes(minutesAgo), session = session)

    @Test
    fun `a launch belongs to the session it named, and to no other`() {
        val detail = resolveSessionDetail(
            session = session,
            launches = listOf(launch("mine", 25, session = 1), launch("theirs", 24, session = 2)),
            events = emptyList(),
            signals = emptyList(),
        )
        assertEquals(listOf("mine"), detail.launches.map { it.appId })
    }

    /** A launch made with no session open is recorded, and attributed to none. */
    @Test
    fun `a launch naming no session joins no session, whatever span it fell in`() {
        val detail = resolveSessionDetail(
            session = session,
            launches = listOf(launch("orphan", 25, session = null)),
            events = emptyList(),
            signals = emptyList(),
        )
        assertEquals(emptyList<String>(), detail.launches.map { it.appId })
    }

    @Test
    fun `launches come out in the order they happened`() {
        val detail = resolveSessionDetail(
            session = session,
            launches = listOf(launch("second", 22, 1), launch("first", 28, 1)),
            events = emptyList(),
            signals = emptyList(),
        )
        assertEquals(listOf("first", "second"), detail.launches.map { it.appId })
    }

    /** Events carry no session, so the span they fell inside is what attributes them. */
    @Test
    fun `the checks and the repeated open are the ones inside the session's span`() {
        val detail = resolveSessionDetail(
            session = session,
            launches = emptyList(),
            events = listOf(
                LoggedEvent(EventType.OpenCheckDisplayed, now.minusMinutes(25)),
                LoggedEvent(EventType.OpenCheckDisplayed, now.minusMinutes(22)),
                LoggedEvent(EventType.RepeatedOpenDetected, now.minusMinutes(22)),
                // After the session ended: another session's, not this one's.
                LoggedEvent(EventType.OpenCheckDisplayed, now.minusMinutes(5)),
                LoggedEvent(EventType.RepeatedOpenDetected, now.minusMinutes(5)),
            ),
            signals = emptyList(),
        )
        assertEquals(2, detail.checks)
        assertEquals(true, detail.repeatedOpen)

        val quiet = resolveSessionDetail(session, emptyList(), emptyList(), emptyList())
        assertEquals(0, quiet.checks)
        assertEquals(false, quiet.repeatedOpen)
    }

    @Test
    fun `the statement is the words the session's own signal carried`() {
        val detail = resolveSessionDetail(
            session = session,
            launches = emptyList(),
            events = emptyList(),
            signals = listOf(
                // Stated in another session, and stated in this one.
                IntentSignal(at = now.minusMinutes(5), text = "somewhere else"),
                IntentSignal(at = now.minusMinutes(25), text = "finish the reading"),
            ),
        )
        assertEquals("finish the reading", detail.statement)
    }

    /** A category picked from the prompt states an intent and spells out nothing. */
    @Test
    fun `a signal stated without words leaves the session with no statement`() {
        val detail = resolveSessionDetail(
            session = session,
            launches = emptyList(),
            events = emptyList(),
            signals = listOf(IntentSignal(at = now.minusMinutes(25))),
        )
        assertEquals(null, detail.statement)
    }

    @Test
    fun `a session that opened nothing says so, and never invents a row`() {
        val detail = resolveSessionDetail(
            session = session,
            launches = emptyList(),
            events = listOf(LoggedEvent(EventType.OpenCheckDisplayed, now.minusMinutes(25))),
            signals = emptyList(),
        )
        assertEquals(emptyList<LaunchRecord>(), detail.launches)
        assertEquals(
            listOf("Nothing was opened in this session", "1 Open Check fired"),
            sessionDetailNotes(detail),
        )
    }

    @Test
    fun `the notes count only what happened`() {
        fun notes(checks: Int, repeated: Boolean) = sessionDetailNotes(
            SessionDetail(
                session = session,
                launches = listOf(launch("app", 25, 1)),
                checks = checks,
                repeatedOpen = repeated,
                statement = null,
            )
        )
        assertEquals(emptyList<String>(), notes(checks = 0, repeated = false))
        assertEquals(listOf("1 Open Check fired"), notes(checks = 1, repeated = false))
        assertEquals(
            listOf("3 Open Checks fired", "A repeated open was noticed"),
            notes(checks = 3, repeated = true),
        )
    }

    @Test
    fun `a launch's line is the time it happened, spelled by the chosen clock`() {
        val opened = LaunchRecord("app", LocalDateTime.of(2026, 8, 7, 9, 42))
        assertEquals("9:42", launchTimeLine(opened))
        assertEquals("0942", launchTimeLine(opened, ClockFormat.Nato))
    }

    // Awareness's App view (#174): one app's opens.

    private fun opened(appId: String, at: LocalDateTime, session: Long? = null) =
        LaunchRecord(appId = appId, at = at, session = session)

    private fun day(y: Int, m: Int, d: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(y, m, d, hour, minute)

    @Test
    fun `an app's opens group under the day they fell in, newest first`() {
        val view = resolveAppOpens(
            appId = "atlas",
            label = "Atlas",
            launches = listOf(
                opened("atlas", day(2026, 8, 6, 9, 10)),
                opened("ledger", day(2026, 8, 7, 11, 0)),
                opened("atlas", day(2026, 8, 7, 21, 30)),
                opened("atlas", day(2026, 8, 7, 9, 15)),
                // 2:00am on the 8th is still the 7th's, under the 4am boundary
                // (ADR 0003) — the same day the evening open belongs to.
                opened("atlas", day(2026, 8, 8, 2, 0)),
            ),
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 6)),
            view.days.map { it.day },
        )
        assertEquals(
            listOf(day(2026, 8, 8, 2, 0), day(2026, 8, 7, 21, 30), day(2026, 8, 7, 9, 15)),
            view.days[0].opens.map { it.at },
        )
        assertEquals(listOf(day(2026, 8, 6, 9, 10)), view.days[1].opens.map { it.at })
    }

    @Test
    fun `the counts are of the records the view renders, and a launch that named no session joins none`() {
        val view = resolveAppOpens(
            appId = "atlas",
            label = "Atlas",
            launches = listOf(
                opened("atlas", day(2026, 8, 7, 9, 15), session = 1),
                opened("atlas", day(2026, 8, 7, 9, 40), session = 1),
                opened("atlas", day(2026, 8, 6, 20, 0), session = 2),
                opened("atlas", day(2026, 8, 6, 21, 0), session = null),
                opened("ledger", day(2026, 8, 7, 11, 0), session = 3),
            ),
        )

        assertEquals(4, view.opens)
        assertEquals(view.days.sumOf { it.opens.size }, view.opens)
        // Two sessions, though four opens fell across them: an app opened three
        // times in one session appeared in one session.
        assertEquals(2, view.sessions)
        assertEquals("4 opens · 2 sessions", appOpensLine(view))
    }

    /** The record is what Bodha holds; the phone's current inventory does not edit it. */
    @Test
    fun `an app with no name left keeps its recorded id and is named as uninstalled`() {
        val view = resolveAppOpens(
            appId = "com.example.gone",
            label = null,
            launches = listOf(opened("com.example.gone", day(2026, 8, 7, 9, 15), session = 1)),
        )

        assertEquals("com.example.gone", view.appId)
        assertEquals("com.example.gone", view.name)
        assertEquals(false, view.installed)
        assertEquals(1, view.opens)
        assertEquals("No longer installed · 1 open · 1 session", appOpensLine(view))
    }

    @Test
    fun `one open and two hundred render the same shape`() {
        fun view(count: Int) = resolveAppOpens(
            appId = "atlas",
            label = "Atlas",
            launches = (1..count).map { opened("atlas", day(2026, 8, 7, 5).plusMinutes(it * 5L), 1) },
        )

        val one = view(1)
        val many = view(200)
        assertEquals(one.days.size, many.days.size)
        assertEquals(one.installed, many.installed)
        assertEquals(one.sessions, many.sessions)
        assertEquals("1 open · 1 session", appOpensLine(one))
        assertEquals("200 opens · 1 session", appOpensLine(many))
    }

    @Test
    fun `an app opened outside every session is never told it had none`() {
        val view = resolveAppOpens(
            appId = "atlas",
            label = "Atlas",
            launches = listOf(opened("atlas", day(2026, 8, 7, 9, 15), session = null)),
        )

        assertEquals(0, view.sessions)
        assertEquals("1 open", appOpensLine(view))
    }

    /** A read that landed on nothing is a named absence, never a 0 in a count field. */
    @Test
    fun `an app with nothing recorded says so rather than counting nothing`() {
        val view = resolveAppOpens("atlas", "Atlas", launches = listOf(opened("ledger", now)))

        assertEquals(emptyList<AppDay>(), view.days)
        assertEquals("No opens recorded", appOpensLine(view))
    }

    @Test
    fun `a day heading is the date and nothing else, spelled by the chosen format`() {
        val august = AppDay(LocalDate.of(2026, 8, 7), listOf(opened("atlas", day(2026, 8, 7, 9, 15))))

        assertEquals("Friday, 7 August", appDayLine(august))
        assertEquals("2026-08-07", appDayLine(august, DateFormat.Numeric))
    }

    /**
     * Foreground time needs usage access and arrives with it (#175). Until then
     * the view has no field for it — pinned here as a shape, because a field
     * resolving to 0 would say the app was never in front, which is a different
     * claim from "nothing measured it".
     */
    @Test
    fun `the App view carries no duration field at all`() {
        val fields = AppOpens::class.java.declaredFields.map { it.name }

        assertEquals(listOf("appId", "name", "installed", "days", "opens", "sessions"), fields)
        for (word in listOf("duration", "millis", "seconds", "foreground", "screen")) {
            assert(fields.none { it.lowercase().contains(word) }) {
                "AppOpens carries \"$word\" before #175 grants it a reading"
            }
        }
    }

    @Test
    fun `no rendered line carries a delta, a direction word or a ranking`() {
        val start = LocalDateTime.of(2026, 8, 7, 9, 41)
        val appDay = AppDay(LocalDate.of(2026, 8, 7), listOf(opened("atlas", start)))
        fun app(installed: Boolean, opens: Int, sessions: Int) = appOpensLine(
            AppOpens(
                appId = "atlas",
                name = if (installed) "Atlas" else "atlas",
                installed = installed,
                days = if (opens == 0) emptyList() else listOf(appDay),
                opens = opens,
                sessions = sessions,
            )
        )
        val lines = listOf(
            app(installed = true, opens = 12, sessions = 4),
            app(installed = true, opens = 1, sessions = 0),
            app(installed = true, opens = 0, sessions = 0),
            app(installed = false, opens = 3, sessions = 2),
            appDayLine(appDay),
        ) + listOf(
            awarenessTodayLine(AwarenessToday.None),
            awarenessTodayLine(AwarenessToday.Sessions(1, running = false)),
            awarenessTodayLine(AwarenessToday.Sessions(6, running = true)),
            awarenessSessionLine(AwarenessSession(record(1, start, start.plusMinutes(12)), true)),
            awarenessSessionLine(AwarenessSession(record(2, start, null), false)),
            awarenessIntentWord(intentional = true),
            awarenessIntentWord(intentional = false),
        ) + sessionDetailNotes(
            SessionDetail(session, emptyList(), checks = 3, repeatedOpen = true, statement = null)
        )
        val forbidden = listOf("+", "-", "more", "less", "up", "down", "better", "worse", "most", "least")
        for (line in lines) {
            for (word in forbidden) {
                assert(!line.lowercase().split(" ", "·").contains(word)) {
                    "\"$line\" carries \"$word\" (ADR 0013)"
                }
            }
        }
    }
}
