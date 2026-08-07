package com.bodhalauncher.app.session

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.data.BodhaDatabase
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
        log.apply(Transition.SessionStarted(session, at("2026-08-07T10:00:00")))
        log.apply(Transition.SessionResumed(session, at("2026-08-07T10:05:10")))
        log.apply(Transition.SessionEnded(session, at("2026-08-07T10:20:00")))

        val day = dayKey(LocalDateTime.parse("2026-08-07T10:00:00")).toEpochDay()
        val records = db.sessionRecords().forDay(day)
        assertEquals(1, records.size)
        assertEquals(at("2026-08-07T10:00:00").toEpochMilli(), records[0].startMillis)
        assertEquals(at("2026-08-07T10:20:00").toEpochMilli(), records[0].endMillis)
    }

    @Test
    fun `a peek leaves no record`() = runBlocking {
        log.apply(Transition.PeekObserved(at("2026-08-07T10:00:00")))
        assertEquals(0, db.sessionRecords().count())
    }

    @Test
    fun `killing and restarting mid-session leaves one record, closed where the engine reconciled`() = runBlocking {
        val session = SessionId(3)
        log.apply(Transition.SessionStarted(session, at("2026-08-07T10:00:00")))

        // A fresh process: a new log over the same store, fed the reconciled end.
        val restarted = SessionRecordLog(db.sessionRecords())
        restarted.apply(Transition.SessionEnded(session, at("2026-08-07T10:12:00")))

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
        log.apply(Transition.SessionStarted(session, at("2026-08-07T10:00:00")))
        log.apply(Transition.SessionStarted(session, at("2026-08-07T10:00:05")))
        assertEquals(1, db.sessionRecords().count())
        val day = dayKey(LocalDateTime.parse("2026-08-07T10:00:00")).toEpochDay()
        assertEquals(at("2026-08-07T10:00:00").toEpochMilli(), db.sessionRecords().forDay(day)[0].startMillis)
    }

    @Test
    fun `a session starting before 4am belongs to the day it started`() = runBlocking {
        val session = SessionId(5)
        log.apply(Transition.SessionStarted(session, at("2026-08-07T03:30:00")))
        log.apply(Transition.SessionEnded(session, at("2026-08-07T04:30:00")))

        // Under the 4am boundary, 3:30am on the 7th is still the 6th's day key.
        val sixth = dayKey(LocalDateTime.parse("2026-08-07T03:30:00")).toEpochDay()
        val seventh = sixth + 1
        assertEquals(1, db.sessionRecords().forDay(sixth).size)
        assertEquals(0, db.sessionRecords().forDay(seventh).size)
    }

    @Test
    fun `a still-open session is returned for any day, as running`() = runBlocking {
        val session = SessionId(6)
        log.apply(Transition.SessionStarted(session, at("2026-08-07T03:30:00")))

        val seventh = dayKey(LocalDateTime.parse("2026-08-07T12:00:00")).toEpochDay()
        val records = db.sessionRecords().forDay(seventh)
        assertEquals(1, records.size)
        assertNull(records[0].endMillis)
    }

    @Test
    fun `retention's cut deletes by start`() = runBlocking {
        val old = SessionId(7)
        val recent = SessionId(8)
        log.apply(Transition.SessionStarted(old, at("2026-07-01T10:00:00")))
        log.apply(Transition.SessionEnded(old, at("2026-07-01T10:10:00")))
        log.apply(Transition.SessionStarted(recent, at("2026-08-07T10:00:00")))
        log.apply(Transition.SessionEnded(recent, at("2026-08-07T10:10:00")))

        db.sessionRecords().deleteBefore(at("2026-08-01T04:00:00").toEpochMilli())
        assertEquals(1, db.sessionRecords().count())
    }
}
