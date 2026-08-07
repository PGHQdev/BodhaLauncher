package com.bodhalauncher.app.focus

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.data.EventLogger
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Focus session's persistence and every ending path (#166, #169, #170):
 * the session survives process death as its end instant, the record is written
 * on both endings, counts survive, and extend un-happens the ending.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class FocusStoreTest {

    private lateinit var db: BodhaDatabase
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val t0: Instant = Instant.parse("2026-08-07T10:00:00Z")

    @Before
    fun setUp() {
        context.getSharedPreferences("focus_session", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, BodhaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun store() = FocusStore(context, db.focusRecords(), EventLogger(db.eventLog()))

    private fun FocusStore.startDefault(minutes: Long = 30, allowed: Set<String> = setOf("com.a")) =
        start("Deep work", minutes, allowed, t0)

    /** The single-lane writes are async; a fresh read after settling sees them. */
    private fun drain() = Thread.sleep(150)

    @Test
    fun `the session survives a restart with remaining time derived from the stored end instant`() {
        store().startDefault(minutes = 30)

        val restarted = store()
        val session = restarted.active.value
        assertNotNull(session)
        assertEquals(t0.plusSeconds(30 * 60), session!!.endsAt)
    }

    @Test
    fun `one session at a time - a second start is a no-op`() {
        val s = store()
        s.startDefault(minutes = 30)
        s.start("Another", 15, emptySet(), t0.plusSeconds(60))
        assertEquals("Deep work", s.active.value?.label)
    }

    @Test
    fun `an end that passed while the process was dead is detected on the next start`() {
        store().startDefault(minutes = 15)

        val restarted = store()
        restarted.resolveEnd(t0.plus(Duration.ofHours(3)))

        assertNull(restarted.active.value)
        val moment = restarted.pending.value
        assertNotNull(moment)
        // The full duration keeps the boundary; lateness re-derives at render.
        assertEquals(t0.plusSeconds(15 * 60), moment!!.record.endedAt)
        assertFalse(moment.record.endedEarly)
    }

    @Test
    fun `a record is written on the full-duration path with the counts the checks fired`() = runBlocking {
        val s = store()
        s.startDefault(minutes = 15)
        s.countReach(); s.countReach(); s.countProceed()
        s.resolveEnd(t0.plusSeconds(15 * 60))
        drain()

        assertEquals(1, db.focusRecords().count())
        val summary = db.focusRecords().dashboardSummary()
        assertEquals(com.bodhalauncher.engine.RetentionCategory.Reflections, summary.category)
        val moment = s.pending.value!!
        assertEquals(2, moment.record.reaches)
        assertEquals(1, moment.record.proceeds)
    }

    @Test
    fun `ending early writes the record the same way, marked early`() = runBlocking {
        val s = store()
        s.startDefault(minutes = 30)
        s.endEarly(t0.plusSeconds(5 * 60))
        drain()

        assertEquals(1, db.focusRecords().count())
        val moment = s.pending.value!!
        assertTrue(moment.record.endedEarly)
        assertEquals(t0.plusSeconds(5 * 60), moment.record.endedAt)
    }

    @Test
    fun `counts survive process death`() {
        val s = store()
        s.startDefault()
        s.countReach(); s.countProceed()

        val restarted = store()
        assertEquals(1, restarted.active.value?.reaches)
        assertEquals(1, restarted.active.value?.proceeds)
    }

    @Test
    fun `the moment is consumed once and never again, including across a restart`() {
        val s = store()
        s.startDefault(minutes = 15)
        s.resolveEnd(t0.plusSeconds(20 * 60))

        assertNotNull(s.consumePending())
        assertNull(s.consumePending())
        assertNull(store().pending.value)
    }

    @Test
    fun `extend resurrects the same session for ten minutes and its record un-happens`() = runBlocking {
        val s = store()
        s.startDefault(minutes = 15, allowed = setOf("com.a"))
        s.countReach()
        val endedAt = t0.plusSeconds(15 * 60)
        s.resolveEnd(endedAt)
        drain()
        assertEquals(1, db.focusRecords().count())

        val moment = s.consumePending()!!
        val extendAt = endedAt.plusSeconds(120)
        s.extend(moment, extendAt)
        drain()

        val session = s.active.value!!
        assertEquals("Deep work", session.label)
        assertEquals(t0, session.startedAt)
        assertEquals(extendAt.plusSeconds(10 * 60), session.endsAt)
        assertEquals(setOf("com.a"), session.allowedAppIds)
        assertEquals(1, session.reaches)
        assertEquals(0, db.focusRecords().count())
    }

    @Test
    fun `started, completed and abandoned are logged - paused never is`() = runBlocking {
        val s = store()
        s.startDefault(minutes = 15)
        s.resolveEnd(t0.plusSeconds(15 * 60))
        s.start("Again", 15, emptySet(), t0.plusSeconds(16 * 60))
        s.endEarly(t0.plusSeconds(17 * 60))
        drain()

        val types = db.eventLog().between(0, Long.MAX_VALUE).map { it.type }
        assertEquals(2, types.count { it == "FocusStarted" })
        assertEquals(1, types.count { it == "FocusCompleted" })
        assertEquals(1, types.count { it == "FocusAbandoned" })
        assertEquals(0, types.count { it == "FocusPaused" })
    }

    @Test
    fun `the final end after an extend leaves exactly one record`() = runBlocking {
        val s = store()
        s.startDefault(minutes = 15)
        s.resolveEnd(t0.plusSeconds(15 * 60))
        drain()
        s.extend(s.consumePending()!!, t0.plusSeconds(16 * 60))
        drain()
        s.resolveEnd(t0.plusSeconds(26 * 60))
        drain()

        assertEquals(1, db.focusRecords().count())
    }
}
