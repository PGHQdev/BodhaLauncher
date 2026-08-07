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
     * The shape #174 pinned as absent, now present and pinned as a **named
     * state** (#175). The field's type is what makes a 0 unsayable: a `Long`
     * here could be filled in with one and would say the app was never in front,
     * which is a different claim from "nothing measured it".
     *
     * The default is the state a phone without usage access is permanently in,
     * so a caller that reads no usage at all gets the true answer rather than an
     * optimistic one.
     */
    @Test
    fun `the App view's duration is a named state, defaulting to the absence`() {
        val field = AppOpens::class.java.declaredFields.single { it.name == "foreground" }

        assertEquals(AwarenessDuration::class.java, field.type)
        assertEquals(
            AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
            resolveAppOpens("atlas", "Atlas", listOf(opened("atlas", now))).foreground,
        )
    }

    // Durations and the launches Bodha didn't mediate (#175).

    private fun front(appId: String, at: LocalDateTime) = ForegroundEntry(appId, at)

    private val logged = listOf(opened("atlas", day(2026, 8, 7, 9, 15), session = 1))

    @Test
    fun `a system opening inside the same-launch window is the launch Bodha already logged`() {
        val merged = mergeLaunches(
            appId = "atlas",
            logged = logged,
            // Bodha writes the record, then starts the activity: the system's
            // entry is always the later of the two.
            entries = listOf(front("atlas", day(2026, 8, 7, 9, 15).plusSeconds(4))),
        )

        assertEquals(1, merged.size)
        assertEquals(1L, merged.single().session)
    }

    @Test
    fun `a system opening outside the window is a launch Bodha did not mediate and names no session`() {
        val merged = mergeLaunches(
            appId = "atlas",
            logged = logged,
            entries = listOf(front("atlas", day(2026, 8, 7, 9, 15).plusSeconds(11))),
        )

        assertEquals(2, merged.size)
        assertEquals(null, merged.last().session)
        assertEquals(day(2026, 8, 7, 9, 15).plusSeconds(11), merged.last().at)
    }

    /** One visit continuing — a new activity, a returning dialog, a rotation. */
    @Test
    fun `an app resuming its own next activity is one visit continuing`() {
        val merged = mergeLaunches(
            appId = "atlas",
            logged = emptyList(),
            entries = listOf(
                front("atlas", day(2026, 8, 7, 11, 0)),
                front("atlas", day(2026, 8, 7, 11, 1)),
                front("atlas", day(2026, 8, 7, 11, 2)),
            ),
        )

        assertEquals(listOf(day(2026, 8, 7, 11, 0)), merged.map { it.at })
    }

    /**
     * The order-of-operations pin. Collapsing after filtering to one app leaves
     * every survivor with the same predecessor, so exactly one entry would live
     * however many times the app was opened — a silent one-row bug the shape of
     * a working feature.
     */
    @Test
    fun `another app between two resumes of the same app leaves two opens standing`() {
        val merged = mergeLaunches(
            appId = "atlas",
            logged = emptyList(),
            entries = listOf(
                front("atlas", day(2026, 8, 7, 11, 0)),
                front("ledger", day(2026, 8, 7, 11, 5)),
                front("atlas", day(2026, 8, 7, 11, 9)),
            ),
        )

        assertEquals(
            listOf(day(2026, 8, 7, 11, 0), day(2026, 8, 7, 11, 9)),
            merged.map { it.at },
        )
    }

    @Test
    fun `merged opens come back in time order`() {
        val merged = mergeLaunches(
            appId = "atlas",
            logged = listOf(
                opened("atlas", day(2026, 8, 7, 13, 0), session = 2),
                opened("atlas", day(2026, 8, 7, 9, 15), session = 1),
            ),
            entries = listOf(
                front("atlas", day(2026, 8, 7, 20, 0)),
                front("ledger", day(2026, 8, 7, 10, 55)),
                front("atlas", day(2026, 8, 7, 11, 0)),
                front("ledger", day(2026, 8, 7, 15, 0)),
            ),
        )

        assertEquals(
            listOf(
                day(2026, 8, 7, 9, 15),
                day(2026, 8, 7, 11, 0),
                day(2026, 8, 7, 13, 0),
                day(2026, 8, 7, 20, 0),
            ),
            merged.map { it.at },
        )
    }

    @Test
    fun `with no foreground entries the merge is the launch log unchanged`() {
        assertEquals(logged, mergeLaunches("atlas", logged, entries = emptyList()))
    }

    @Test
    fun `usage access never granted resolves the figure to unavailable and offers the way in`() {
        val state = resolveAwarenessUsage(granted = false, educationShown = false, grantSeen = false)

        assertEquals(AwarenessUsage.Ungranted(offersTurnOn = true), state)
        assertEquals(
            AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
            resolveAppDuration(state, packageName = "com.atlas", reading = mapOf("com.atlas" to 900_000)),
        )
        assertEquals("Foreground time needs usage access", awarenessForegroundLine(
            resolveAppDuration(state, "com.atlas", null), state,
        ))
    }

    /** A declined education drops the turn-on; the state itself is unchanged. */
    @Test
    fun `a declined education drops the turn-on and rests on the named state`() {
        val state = resolveAwarenessUsage(granted = false, educationShown = true, grantSeen = false)

        assertEquals(AwarenessUsage.Ungranted(offersTurnOn = false), state)
        assertEquals(
            "Foreground time needs usage access",
            awarenessForegroundLine(AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess), state),
        )
    }

    @Test
    fun `a grant held this session and gone now names what stopped`() {
        val state = resolveAwarenessUsage(granted = false, educationShown = true, grantSeen = true)

        assertEquals(AwarenessUsage.Revoked, state)
        assertEquals(
            "Foreground time stopped when usage access was turned off",
            awarenessForegroundLine(resolveAppDuration(state, "com.atlas", emptyMap()), state),
        )
        assertEquals(
            "Opens from notifications and other apps stopped when usage access was turned off",
            appOpensSourceLine(state),
        )
    }

    /**
     * A work-profile id carries a serial and resolves to no package to look up,
     * so nothing could have measured it. Flattening the map to a `Long?` before
     * resolving would turn that into a reading that found nothing.
     */
    @Test
    fun `a granted read that never reached the app is unavailable, not none`() {
        val state = AwarenessUsage.Live

        assertEquals(
            AwarenessDuration.Unavailable(UnavailableReason.OtherProfile),
            resolveAppDuration(state, packageName = null, reading = mapOf("com.atlas" to 900_000)),
        )
        assertEquals(
            AwarenessDuration.Unavailable(UnavailableReason.NoReading),
            resolveAppDuration(state, packageName = "com.atlas", reading = null),
        )
    }

    @Test
    fun `an app absent from a successful reading is none rather than unavailable`() {
        val state = AwarenessUsage.Live

        assertEquals(
            AwarenessDuration.None,
            resolveAppDuration(state, "com.atlas", reading = mapOf("com.ledger" to 900_000)),
        )
        // A recorded zero is the same claim as an absence, and said the same way.
        assertEquals(
            AwarenessDuration.None,
            resolveAppDuration(state, "com.atlas", reading = mapOf("com.atlas" to 0)),
        )
        assertEquals(
            AwarenessDuration.Span(900_000),
            resolveAppDuration(state, "com.atlas", reading = mapOf("com.atlas" to 900_000)),
        )
        assertEquals(null, appOpensSourceLine(state))
    }

    /** A figure of nothing is a sentence, because a `0` in a field is unreadable as an answer. */
    @Test
    fun `no foreground line renders a zero`() {
        val lines = foregroundLines()

        assertEquals(7, lines.size)
        for (line in lines) {
            assert(!line.split(" ").any { it.trimEnd('.', ',') == "0" }) { "\"$line\" renders a zero" }
            assert(line.isNotBlank()) { "a foreground state rendered nothing at all" }
        }
        assertEquals("No foreground time recorded", awarenessForegroundLine(AwarenessDuration.None, AwarenessUsage.Live))
        assertEquals(
            "Under a minute in the foreground",
            awarenessForegroundLine(AwarenessDuration.Span(42_000), AwarenessUsage.Live),
        )
        assertEquals(
            "2 hours 15 minutes in the foreground",
            awarenessForegroundLine(AwarenessDuration.Span(8_100_000), AwarenessUsage.Live),
        )
    }

    /** Every arm of the two statement functions, for the sweep and the zero check alike. */
    private fun foregroundLines(): List<String> = listOf(
        awarenessForegroundLine(
            AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
            AwarenessUsage.Ungranted(offersTurnOn = true),
        ),
        awarenessForegroundLine(
            AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
            AwarenessUsage.Revoked,
        ),
        awarenessForegroundLine(
            AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
            AwarenessUsage.Live,
        ),
        awarenessForegroundLine(
            AwarenessDuration.Unavailable(UnavailableReason.OtherProfile),
            AwarenessUsage.Live,
        ),
        awarenessForegroundLine(
            AwarenessDuration.Unavailable(UnavailableReason.NoReading),
            AwarenessUsage.Live,
        ),
        awarenessForegroundLine(AwarenessDuration.None, AwarenessUsage.Live),
        awarenessForegroundLine(AwarenessDuration.Span(8_100_000), AwarenessUsage.Live),
    )

    // Awareness's Week view (#176): seven days side by side.

    private fun onDay(day: LocalDate, hour: Int, minutes: Long = 30, id: Long = day.toEpochDay()) =
        SessionRecord(
            id = id,
            start = day.atTime(hour, 0),
            end = day.atTime(hour, 0).plusMinutes(minutes),
            day = day,
        )

    private val weekNow = LocalDateTime.of(2026, 8, 7, 14, 0)
    private val liveDay: LocalDate = LocalDate.of(2026, 8, 7)

    private fun week(
        records: List<SessionRecord> = emptyList(),
        signals: List<IntentSignal> = emptyList(),
        millis: Long? = null,
        previousMillis: Long? = null,
        usage: AwarenessUsage = AwarenessUsage.Live,
    ) = resolveAwarenessWeek(records, signals, millis, previousMillis, usage, weekNow)

    @Test
    fun `the week is seven days oldest first, ending on the day now falls in`() {
        assertEquals(
            (6L downTo 0L).map { liveDay.minusDays(it) },
            week().days.map { it.day },
        )
        // 2am on the 8th is still the 7th's day, under the 4am boundary.
        assertEquals(
            liveDay,
            resolveAwarenessWeek(
                emptyList(), emptyList(), null, null, AwarenessUsage.Live,
                LocalDateTime.of(2026, 8, 8, 2, 0),
            ).days.last().day,
        )
    }

    @Test
    fun `a day's figures count its own sessions and split them into intentional and unclassified`() {
        val monday: LocalDate = LocalDate.of(2026, 8, 3)
        val stated = onDay(monday, hour = 9, id = 1)
        val figures = week(
            records = listOf(
                stated,
                onDay(monday, hour = 11, id = 2),
                onDay(monday, hour = 13, id = 3),
                onDay(monday.plusDays(1), hour = 9, id = 4),
            ),
            signals = listOf(IntentSignal(at = monday.atTime(9, 10))),
        ).days.single { it.day == monday }

        assertEquals(3, figures.sessions)
        assertEquals(1, figures.intentional)
        assertEquals(2, figures.unclassified)
        assertEquals("3 sessions · 1 intentional · 2 unclassified", awarenessDayFiguresLine(figures))
    }

    /** The day was stamped at write under the 4am boundary; the week reads it back. */
    @Test
    fun `a session that spans the boundary counts on the day it started`() {
        val monday: LocalDate = LocalDate.of(2026, 8, 3)
        val overnight = SessionRecord(
            id = 1,
            start = LocalDateTime.of(2026, 8, 3, 23, 30),
            end = LocalDateTime.of(2026, 8, 4, 1, 0),
            day = monday,
        )
        val days = week(records = listOf(overnight)).days

        assertEquals(1, days.single { it.day == monday }.sessions)
        assertEquals(0, days.single { it.day == monday.plusDays(1) }.sessions)
    }

    /**
     * The double-count arm. Today's rule keeps an open record whatever day it
     * started, because a session running now is running on the screen the reader
     * is holding — fed through seven days it would count one session twice.
     */
    @Test
    fun `a session started three days ago and still open counts once, on its start day`() {
        val started: LocalDate = LocalDate.of(2026, 8, 4)
        val stale = SessionRecord(id = 1, start = started.atTime(9, 0), end = null, day = started)
        val days = week(records = listOf(stale)).days

        assertEquals(listOf(started), days.filter { it.sessions > 0 }.map { it.day })
        assertEquals(1, days.sumOf { it.sessions })
    }

    @Test
    fun `a running session appears on the live day and on no other`() {
        val running = SessionRecord(id = 1, start = weekNow.minusMinutes(10), end = null, day = liveDay)
        val days = week(records = listOf(running)).days

        assertEquals(listOf(liveDay), days.filter { it.sessions > 0 }.map { it.day })
        assertEquals("1 session · 1 unclassified", awarenessDayFiguresLine(days.last()))
    }

    @Test
    fun `a day with no intentional sessions omits the intentional half rather than writing zero`() {
        val figures = AwarenessDayFigures(day = liveDay, sessions = 2, intentional = 0)

        assertEquals("2 sessions · 2 unclassified", awarenessDayFiguresLine(figures))
    }

    @Test
    fun `a day with nothing unclassified omits that half rather than writing zero`() {
        val figures = AwarenessDayFigures(day = liveDay, sessions = 2, intentional = 2)

        assertEquals("2 sessions · 2 intentional", awarenessDayFiguresLine(figures))
    }

    /** A quiet day is a sentence, never a 0 in a count field (ADR 0013). */
    @Test
    fun `a day with no sessions names its absence rather than resolving to a zero`() {
        val quiet = week().days.first()

        assertEquals(0, quiet.sessions)
        assertEquals("No sessions", awarenessDayFiguresLine(quiet))
    }

    @Test
    fun `an absent usage read leaves both rates unavailable and no figure becomes zero`() {
        val ungranted = week(usage = AwarenessUsage.Ungranted(offersTurnOn = true), millis = 900_000)
        assertEquals(
            AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
            ungranted.rate,
        )
        assertEquals(
            AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
            ungranted.previousRate,
        )
        assertEquals(null, awarenessWeekRateLine(ungranted))

        // Access held and the read still came back with nothing at all.
        val granted = week(millis = null, previousMillis = null)
        assertEquals(AwarenessDuration.Unavailable(UnavailableReason.NoReading), granted.rate)
        assertEquals(AwarenessDuration.Unavailable(UnavailableReason.NoReading), granted.previousRate)
    }

    @Test
    fun `a granted read with an empty window resolves to none rather than to zero`() {
        assertEquals(AwarenessDuration.None, resolveWeekRate(AwarenessUsage.Live, totalMillis = 0))
        assertEquals(
            "No foreground time recorded",
            awarenessForegroundLine(resolveWeekRate(AwarenessUsage.Live, 0), AwarenessUsage.Live),
        )
    }

    /** Time spent reading Awareness is not time spent on the phone's other apps. */
    @Test
    fun `the fold excludes the launcher's own package`() {
        val reading = mapOf(
            "com.bodhalauncher" to 3_600_000L,
            "com.atlas" to 900_000L,
            "com.ledger" to 600_000L,
        )

        assertEquals(1_500_000L, totalForegroundMillis(reading, setOf("com.bodhalauncher")))
        assertEquals(5_100_000L, totalForegroundMillis(reading, excluded = emptySet()))
    }

    @Test
    fun `the fold over a null reading is null, not zero`() {
        assertEquals(null, totalForegroundMillis(null, setOf("com.bodhalauncher")))
        // And an empty reading is a real zero, which resolves to the named none.
        assertEquals(0L, totalForegroundMillis(emptyMap(), setOf("com.bodhalauncher")))
    }

    /** Adjacent bare numbers, which is the whole of what ADR 0013 permits here. */
    @Test
    fun `the two periods sit adjacent as bare rates with no sign and no delta`() {
        val both = week(millis = 78_120_000, previousMillis = 85_680_000)

        assertEquals(AwarenessDuration.Span(11_160_000), both.rate)
        assertEquals("This week 3.1h/day · last week 3.4h/day", awarenessWeekRateLine(both))

        // A period with no rate beside it states its own and stops: a sentence
        // with one number and one excuse in it invites the comparison it cannot
        // support.
        assertEquals("This week 3.1h/day", awarenessWeekRateLine(week(millis = 78_120_000)))
        assertEquals(null, awarenessWeekRateLine(week(previousMillis = 85_680_000)))
    }

    @Test
    fun `days are ordered by date whatever order the records arrive in`() {
        val records = (0L..6L).map { onDay(liveDay.minusDays(it), hour = 9, id = it) }
        val shuffled = week(records = records.shuffled())

        assertEquals((6L downTo 0L).map { liveDay.minusDays(it) }, shuffled.days.map { it.day })
        assertEquals(List(AWARENESS_WEEK_DAYS) { 1 }, shuffled.days.map { it.sessions })
    }

    /**
     * The shape pin: the Week renders the session count, the split and the rate,
     * and no field [computeMetrics] owns. Those metrics are computed off the
     * event log while these counts come off the session records — two stores
     * that disagree the moment a record was written by reconciliation with no
     * event logged, and one row showing two answers to one question is the
     * failure [INTENT_SIGNAL_EVENTS] exists to prevent.
     */
    @Test
    fun `the week resolves no field computeMetrics owns`() {
        assertEquals(
            listOf("days", "rate", "previousRate"),
            AwarenessWeek::class.java.declaredFields.map { it.name },
        )
        assertEquals(
            listOf("day", "sessions", "intentional"),
            AwarenessDayFigures::class.java.declaredFields.map { it.name },
        )
        val metrics = ProductMetrics::class.java.declaredFields.map { it.name }
        assertEquals(
            emptyList<String>(),
            AwarenessWeek::class.java.declaredFields.map { it.name }.filter { it in metrics },
        )
    }

    @Test
    fun `a picked day's line names its date, and the live day's line still says today`() {
        val view = AwarenessToday.Sessions(finished = 6, running = false)

        assertEquals("6 sessions today", awarenessDayLine(view, liveDay, isToday = true))
        assertEquals(
            "Tuesday, 4 August · 6 sessions",
            awarenessDayLine(view, LocalDate.of(2026, 8, 4), isToday = false),
        )
        assertEquals(
            "Tuesday, 4 August · No sessions",
            awarenessDayLine(AwarenessToday.None, LocalDate.of(2026, 8, 4), isToday = false),
        )
        assertEquals(
            "2026-08-04 · No sessions",
            awarenessDayLine(
                AwarenessToday.None, LocalDate.of(2026, 8, 4), isToday = false, DateFormat.Numeric,
            ),
        )
    }

    /** The Today path keeps its live-day arm, and every #172 reading is unchanged. */
    @Test
    fun `the day view reads a past day by its own records alone`() {
        val started: LocalDate = LocalDate.of(2026, 8, 4)
        val stale = SessionRecord(id = 1, start = started.atTime(9, 0), end = null, day = started)
        val closed = onDay(liveDay, hour = 9, id = 2)

        assertEquals(
            AwarenessToday.Sessions(finished = 1, running = true),
            resolveAwarenessDay(listOf(stale, closed), liveDay, weekNow),
        )
        assertEquals(
            AwarenessToday.Sessions(finished = 0, running = true),
            resolveAwarenessDay(listOf(stale, closed), started, weekNow),
        )
        // The live-day arm is the Today path's only: a past day takes its own
        // records and nothing else.
        assertEquals(
            listOf(1L),
            awarenessDayRecords(listOf(stale, closed), started, weekNow).map { it.id },
        )
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
        ) + sessionDetailNotes(
            // What an exclusion says on the view it emptied (#178): a bare count
            // of what the reader took out, never a word about what is left.
            SessionDetail(
                session, emptyList(), checks = 0, repeatedOpen = false,
                statement = null, excludedApps = 2,
            )
        ) + listOf(
            exclusionsLine(Exclusions()),
            exclusionsLine(Exclusions(apps = setOf("atlas"))),
            exclusionsLine(Exclusions(sessions = setOf(1, 2))),
            exclusionsLine(Exclusions(apps = setOf("atlas", "ledger"), sessions = setOf(1))),
            exclusionSessionLine(record(1, start, start.plusMinutes(12))),
            exclusionSessionLine(record(2, start, null)),
        ) + foregroundLines() + listOfNotNull(
            appOpensSourceLine(AwarenessUsage.Ungranted(offersTurnOn = true)),
            appOpensSourceLine(AwarenessUsage.Revoked),
            appOpensSourceLine(AwarenessUsage.Live),
            AWARENESS_TURN_ON_USAGE,
        ) + AwarenessView.entries.map { it.label } + listOf(
            awarenessDayFiguresLine(AwarenessDayFigures(liveDay, sessions = 0, intentional = 0)),
            awarenessDayFiguresLine(AwarenessDayFigures(liveDay, sessions = 4, intentional = 3)),
            awarenessDayFiguresLine(AwarenessDayFigures(liveDay, sessions = 1, intentional = 0)),
            awarenessDayFiguresLine(AwarenessDayFigures(liveDay, sessions = 2, intentional = 2)),
            awarenessDayLine(AwarenessToday.None, liveDay, isToday = false),
            awarenessDayLine(AwarenessToday.Sessions(3, running = true), liveDay, isToday = false),
        ) + listOfNotNull(
            awarenessWeekRateLine(week(millis = 78_120_000, previousMillis = 85_680_000)),
            awarenessWeekRateLine(week(millis = 85_680_000, previousMillis = 78_120_000)),
            awarenessWeekRateLine(week(millis = 78_120_000)),
        ) + listOf(
            // The entitlement terminus states what renders, never what was lost
            // (#177) — the sweep is what holds it to that.
            awarenessWindowTerminusLine(FREE_AWARENESS_DAYS),
            awarenessWindowTerminusLine(1),
            awarenessWindowTerminusLine(0),
            awarenessWindowTerminusLine(null),
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
