package com.bodhalauncher.engine

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Taking an app or a session out of Awareness, and putting it back (#178).
 *
 * The rule is a pair of filters over inputs rather than a parameter threaded
 * through every resolver, so what these tests hold is that each figure is folded
 * from the records that survived the filter — which is what makes AC 2 assertable
 * as **whole-value equality against the unexcluded baseline** rather than as a
 * list of fields somebody remembered to restore.
 */
class AwarenessExclusionTest {

    private val now = LocalDateTime.of(2026, 8, 7, 14, 0)
    private val liveDay: LocalDate = LocalDate.of(2026, 8, 7)

    private fun record(id: Long, from: Int, to: Int?) = SessionRecord(
        id = id,
        start = LocalDateTime.of(2026, 8, 7, 9, from),
        end = to?.let { LocalDateTime.of(2026, 8, 7, 9, it) },
    )

    private fun opened(appId: String, minute: Int, session: Long?) =
        LaunchRecord(appId, LocalDateTime.of(2026, 8, 7, 9, minute), session)

    private val first = record(1, from = 12, to = 20)
    private val second = record(2, from = 41, to = 53)
    private val records = listOf(first, second)

    private val launches = listOf(
        opened("atlas", minute = 13, session = 1),
        opened("ledger", minute = 15, session = 1),
        opened("atlas", minute = 42, session = 2),
    )

    @Test
    fun `an excluded session leaves the day's rows and its count`() {
        val exclusions = Exclusions(sessions = setOf(1))
        val kept = retainedSessions(records, exclusions)

        assertEquals(listOf(2L), kept.map { it.id })
        assertEquals(
            AwarenessToday.Sessions(finished = 1, running = false),
            resolveAwarenessDay(kept, liveDay, now),
        )
        assertEquals(listOf(2L), resolveAwarenessSessions(kept, emptyList()).map { it.record.id })
    }

    /** An app is not a session: what it opened goes, and the session it ran in stays. */
    @Test
    fun `an excluded app leaves the launches standing and the sessions untouched`() {
        val exclusions = Exclusions(apps = setOf("atlas"))

        assertEquals(records, retainedSessions(records, exclusions))
        assertEquals(
            listOf("ledger"),
            retainedLaunches(launches, exclusions).map { it.appId },
        )
    }

    @Test
    fun `an excluded app's launches leave the session they were opened in`() {
        val session = AwarenessSession(first, intentional = false)
        val detail = resolveSessionDetail(
            session = session,
            launches = launches,
            events = emptyList(),
            signals = emptyList(),
            exclusions = Exclusions(apps = setOf("atlas")),
        )

        assertEquals(listOf("ledger"), detail.launches.map { it.appId })
        assertEquals(1, detail.excludedApps)
    }

    /**
     * AC 5 as the view sees it: the session keeps its span and its count, says
     * how many apps were taken out, and never claims nothing was opened.
     */
    @Test
    fun `a session whose every launch was excluded keeps its span and its count and says how many apps are excluded`() {
        val session = AwarenessSession(first, intentional = false)
        val detail = resolveSessionDetail(
            session = session,
            launches = launches,
            events = emptyList(),
            signals = emptyList(),
            exclusions = Exclusions(apps = setOf("atlas", "ledger")),
        )

        assertEquals(emptyList(), detail.launches)
        assertEquals(2, detail.excludedApps)
        assertEquals("9:12 · 8 minutes", awarenessSessionLine(detail.session))
        assertEquals(listOf("2 apps excluded"), sessionDetailNotes(detail))
        assertTrue(sessionDetailNotes(detail).none { it.contains("Nothing was opened") })
    }

    /** An app opened three times in one session is one app the reader excluded. */
    @Test
    fun `the excluded count is of apps rather than of launches`() {
        val repeated = listOf(
            opened("atlas", minute = 13, session = 1),
            opened("atlas", minute = 15, session = 1),
            opened("atlas", minute = 17, session = 1),
        )
        val detail = resolveSessionDetail(
            session = AwarenessSession(first, intentional = false),
            launches = repeated,
            events = emptyList(),
            signals = emptyList(),
            exclusions = Exclusions(apps = setOf("atlas")),
        )

        assertEquals(1, detail.excludedApps)
        assertEquals(listOf("1 app excluded"), sessionDetailNotes(detail))
    }

    /** A session that genuinely opened nothing still says so. */
    @Test
    fun `a session that opened nothing with nothing excluded still names the absence`() {
        val detail = resolveSessionDetail(
            session = AwarenessSession(first, intentional = false),
            launches = emptyList(),
            events = emptyList(),
            signals = emptyList(),
        )

        assertEquals(listOf("Nothing was opened in this session"), sessionDetailNotes(detail))
    }

    /**
     * The launch log carries a session id read at the moment of the launch, so an
     * excluded session takes its own opens off the App view by name.
     */
    @Test
    fun `an excluded session takes the opens that named it off the app view`() {
        val exclusions = Exclusions(sessions = setOf(1))
        val kept = retainedLaunches(launches, exclusions)

        assertEquals(
            listOf(LocalDateTime.of(2026, 8, 7, 9, 42)),
            kept.map { it.at },
        )
        assertEquals(1, resolveAppOpens("atlas", "Atlas", kept).opens)
    }

    /**
     * The clause the merge forced (#175): an unmediated open carries no session
     * id at all, so excluding a session would otherwise take only the half of its
     * opens that went through Bodha's own path.
     */
    @Test
    fun `an unmediated open inside an excluded session leaves the app view`() {
        val merged = listOf(
            opened("atlas", minute = 13, session = 1),
            // Android saw it, Bodha did not: no session, inside the first's span.
            opened("atlas", minute = 16, session = null),
            // Outside every excluded session's span, so it stands.
            opened("atlas", minute = 30, session = null),
        )
        val kept = retainedLaunches(merged, Exclusions(sessions = setOf(1)), listOf(first))

        assertEquals(listOf(LocalDateTime.of(2026, 8, 7, 9, 30)), kept.map { it.at })
    }

    /**
     * A caller that read no records filters by app and by named session only.
     * The span clause is the caller's to enable by handing over the records, not
     * something this silently guesses at.
     */
    @Test
    fun `without the excluded sessions' records an unmediated open is left standing`() {
        val merged = listOf(opened("atlas", minute = 16, session = null))

        assertEquals(merged, retainedLaunches(merged, Exclusions(sessions = setOf(1))))
    }

    /** A still-open session has no upper bound, so everything after its start is inside it. */
    @Test
    fun `an unmediated open after an excluded running session's start is inside it`() {
        val running = record(3, from = 55, to = null)
        val merged = listOf(
            opened("atlas", minute = 50, session = null),
            opened("atlas", minute = 58, session = null),
        )
        val kept = retainedLaunches(merged, Exclusions(sessions = setOf(3)), listOf(running))

        assertEquals(listOf(LocalDateTime.of(2026, 8, 7, 9, 50)), kept.map { it.at })
    }

    /**
     * The one figure a reader could check against their own sense of the week
     * (#176, #178). The fold already took a set of packages; the exclusion widens
     * it, and nothing else about the rate changes.
     */
    @Test
    fun `an excluded app's foreground time leaves the week rate`() {
        val reading = mapOf("com.bodhalauncher" to 600_000L, "atlas" to 7_000_000L, "ledger" to 400_000L)
        val mine = setOf("com.bodhalauncher")

        assertEquals(7_400_000L, totalForegroundMillis(reading, mine))
        assertEquals(400_000L, totalForegroundMillis(reading, mine + "atlas"))
        assertEquals(
            AwarenessDuration.Span(400_000L / AWARENESS_WEEK_DAYS),
            resolveWeekRate(AwarenessUsage.Live, totalForegroundMillis(reading, mine + "atlas")),
        )
    }

    /**
     * AC 2, and the reason the rule is a filter over inputs: putting an item back
     * is passing the same records through an empty [Exclusions], so what comes
     * out is the baseline value itself rather than a reconstruction of it. There
     * was nothing to restore, because nothing was pruned.
     */
    @Test
    fun `including again yields the same rows, the same count and the same detail as before`() {
        val session = AwarenessSession(first, intentional = false)
        fun day(exclusions: Exclusions) = resolveAwarenessDay(
            retainedSessions(records, exclusions), liveDay, now,
        )
        fun app(exclusions: Exclusions) =
            resolveAppOpens("atlas", "Atlas", retainedLaunches(launches, exclusions))
        fun detail(exclusions: Exclusions) = resolveSessionDetail(
            session, launches, emptyList(), emptyList(), exclusions,
        )

        val excluded = Exclusions(apps = setOf("atlas"), sessions = setOf(1))
        val before = Triple(day(Exclusions()), app(Exclusions()), detail(Exclusions()))

        // Excluded, then included again, over the very same inputs.
        assertTrue(day(excluded) != before.first)
        assertTrue(app(excluded) != before.second)
        assertTrue(detail(excluded) != before.third)
        assertEquals(before, Triple(day(Exclusions()), app(Exclusions()), detail(Exclusions())))
    }

    /** Nothing excluded is the identity, on both filters. */
    @Test
    fun `an empty exclusion set hands back what it was given`() {
        assertEquals(records, retainedSessions(records, Exclusions()))
        assertEquals(launches, retainedLaunches(launches, Exclusions(), records))
        assertTrue(Exclusions().isEmpty)
        assertTrue(!Exclusions(apps = setOf("atlas")).isEmpty)
        assertTrue(!Exclusions(sessions = setOf(1)).isEmpty)
    }

    @Test
    fun `the exclusions line counts what is excluded and never counts nothing`() {
        assertEquals("Nothing is excluded", exclusionsLine(Exclusions()))
        assertEquals("1 app", exclusionsLine(Exclusions(apps = setOf("atlas"))))
        assertEquals("2 sessions", exclusionsLine(Exclusions(sessions = setOf(1, 2))))
        assertEquals(
            "2 apps · 1 session",
            exclusionsLine(Exclusions(apps = setOf("atlas", "ledger"), sessions = setOf(1))),
        )
    }

    /**
     * Two sessions from different days render identically without the date, and
     * an undo list where two rows are indistinguishable is a list the reader
     * cannot act on.
     */
    @Test
    fun `an excluded session's row names the day as well as the span`() {
        val yesterday = SessionRecord(
            id = 9,
            start = LocalDateTime.of(2026, 8, 6, 9, 12),
            end = LocalDateTime.of(2026, 8, 6, 9, 20),
        )

        assertEquals("Friday, 7 August · 9:12 · 8 minutes", exclusionSessionLine(first))
        assertEquals("Thursday, 6 August · 9:12 · 8 minutes", exclusionSessionLine(yesterday))
        assertTrue(exclusionSessionLine(first) != exclusionSessionLine(yesterday))
    }

    /** The record overload is the same line the classified one draws. */
    @Test
    fun `the session line reads the same from a record as from a classified session`() {
        assertEquals(
            awarenessSessionLine(AwarenessSession(first, intentional = true)),
            awarenessSessionLine(first),
        )
    }
}
