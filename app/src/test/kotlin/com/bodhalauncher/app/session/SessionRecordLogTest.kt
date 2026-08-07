package com.bodhalauncher.app.session

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.engine.RetentionCategory
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.Transition
import com.bodhalauncher.engine.dayKey
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The durable session record (#171, ADR 0028): one row per engine session,
 * closed where the engine ends it — including an end the engine reconciled
 * after process death, which arrives as the same [Transition.SessionEnded].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class SessionRecordLogTest {

    private lateinit var db: BodhaDatabase
    private lateinit var log: SessionRecordLog

    private fun at(text: String): Instant =
        LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant()

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BodhaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        log = SessionRecordLog(db.sessionRecords())
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun `start, resume inside the merge window, end - exactly one record, unlock to final screen-off`() = runBlocking {
        val session = SessionId(1)
        log.write(Transition.SessionStarted(session, at("2026-08-07T10:00:00")))
        log.write(Transition.SessionResumed(session, at("2026-08-07T10:05:10")))
        log.write(Transition.SessionEnded(session, at("2026-08-07T10:20:00")))

        val day = dayKey(LocalDateTime.parse("2026-08-07T10:00:00")).toEpochDay()
        val records = db.sessionRecords().forDay(day)
        assertEquals(1, records.size)
        assertEquals(at("2026-08-07T10:00:00").toEpochMilli(), records[0].startMillis)
        assertEquals(at("2026-08-07T10:20:00").toEpochMilli(), records[0].endMillis)
    }

    @Test
    fun `a peek leaves no record`() = runBlocking {
        log.write(Transition.PeekObserved(at("2026-08-07T10:00:00")))
        assertEquals(0, db.sessionRecords().count())
    }

    @Test
    fun `killing and restarting mid-session leaves one record, closed where the engine reconciled`() = runBlocking {
        val session = SessionId(3)
        log.write(Transition.SessionStarted(session, at("2026-08-07T10:00:00")))

        // A fresh process: a new log over the same store, fed the reconciled end.
        val restarted = SessionRecordLog(db.sessionRecords())
        restarted.write(Transition.SessionEnded(session, at("2026-08-07T10:12:00")))

        assertEquals(1, db.sessionRecords().count())
        val day = dayKey(LocalDateTime.parse("2026-08-07T10:00:00")).toEpochDay()
        assertEquals(
            at("2026-08-07T10:12:00").toEpochMilli(),
            db.sessionRecords().forDay(day)[0].endMillis,
        )
    }

    @Test
    fun `a start replayed after restart keeps the first row`() = runBlocking {
        val session = SessionId(4)
        log.write(Transition.SessionStarted(session, at("2026-08-07T10:00:00")))
        log.write(Transition.SessionStarted(session, at("2026-08-07T10:00:05")))
        assertEquals(1, db.sessionRecords().count())
        val day = dayKey(LocalDateTime.parse("2026-08-07T10:00:00")).toEpochDay()
        assertEquals(at("2026-08-07T10:00:00").toEpochMilli(), db.sessionRecords().forDay(day)[0].startMillis)
    }

    @Test
    fun `a session starting before 4am belongs to the day it started`() = runBlocking {
        val session = SessionId(5)
        log.write(Transition.SessionStarted(session, at("2026-08-07T03:30:00")))
        log.write(Transition.SessionEnded(session, at("2026-08-07T04:30:00")))

        // Under the 4am boundary, 3:30am on the 7th is still the 6th's day key.
        val sixth = dayKey(LocalDateTime.parse("2026-08-07T03:30:00")).toEpochDay()
        val seventh = sixth + 1
        assertEquals(1, db.sessionRecords().forDay(sixth).size)
        assertEquals(0, db.sessionRecords().forDay(seventh).size)
    }

    @Test
    fun `a still-open session is returned for any day, as running`() = runBlocking {
        val session = SessionId(6)
        log.write(Transition.SessionStarted(session, at("2026-08-07T03:30:00")))

        val seventh = dayKey(LocalDateTime.parse("2026-08-07T12:00:00")).toEpochDay()
        val records = db.sessionRecords().forDay(seventh)
        assertEquals(1, records.size)
        assertNull(records[0].endMillis)
    }

    /** The Week's read (#176): the seven days it names, plus anything still open. */
    @Test
    fun `a range read returns the days in it, plus any session still open`() = runBlocking {
        log.write(Transition.SessionStarted(SessionId(10), at("2026-08-01T10:00:00")))
        log.write(Transition.SessionEnded(SessionId(10), at("2026-08-01T10:10:00")))
        log.write(Transition.SessionStarted(SessionId(11), at("2026-08-07T10:00:00")))
        log.write(Transition.SessionEnded(SessionId(11), at("2026-08-07T10:10:00")))
        // Started before the range and never closed: the resolvers place it on
        // its own day, and this query is only what makes it reachable at all.
        log.write(Transition.SessionStarted(SessionId(12), at("2026-07-20T10:00:00")))

        val from = dayKey(LocalDateTime.parse("2026-08-01T10:00:00")).toEpochDay()
        val to = dayKey(LocalDateTime.parse("2026-08-07T10:00:00")).toEpochDay()
        assertEquals(
            listOf(12L, 10L, 11L),
            db.sessionRecords().forDays(from, to).map { it.sessionId },
        )
    }

    @Test
    fun `a day outside the range is not returned`() = runBlocking {
        log.write(Transition.SessionStarted(SessionId(13), at("2026-07-31T10:00:00")))
        log.write(Transition.SessionEnded(SessionId(13), at("2026-07-31T10:10:00")))

        val from = dayKey(LocalDateTime.parse("2026-08-01T10:00:00")).toEpochDay()
        val to = dayKey(LocalDateTime.parse("2026-08-07T10:00:00")).toEpochDay()
        assertEquals(emptyList<Long>(), db.sessionRecords().forDays(from, to).map { it.sessionId })
    }

    @Test
    fun `the dashboard row carries the count under raw usage with its window`() = runBlocking {
        val session = SessionId(9)
        log.write(Transition.SessionStarted(session, at("2026-08-07T10:00:00")))
        log.write(Transition.SessionEnded(session, at("2026-08-07T10:10:00")))

        val summary = db.sessionRecords().dashboardSummary()
        assertEquals(RetentionCategory.RawUsageEvents, summary.category)
        assertEquals(1, summary.count)
        assertEquals(RetentionCategory.RawUsageEvents.defaultDays, summary.retentionDays)
    }

    @Test
    fun `retention's cut deletes by start`() = runBlocking {
        val old = SessionId(7)
        val recent = SessionId(8)
        log.write(Transition.SessionStarted(old, at("2026-07-01T10:00:00")))
        log.write(Transition.SessionEnded(old, at("2026-07-01T10:10:00")))
        log.write(Transition.SessionStarted(recent, at("2026-08-07T10:00:00")))
        log.write(Transition.SessionEnded(recent, at("2026-08-07T10:10:00")))

        db.sessionRecords().deleteBefore(at("2026-08-01T04:00:00").toEpochMilli())
        assertEquals(1, db.sessionRecords().count())
    }
}
