package com.bodhalauncher.app.awareness

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.engine.Exclusions
import com.bodhalauncher.engine.SessionId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The exclusions store (#178): what the reader has taken out of Awareness, held
 * as ids in preferences rather than as a column on the records.
 *
 * AC 3 is what most of this is about, and the store's *location* is the whole
 * argument: an exclusion moves no row, so every count the privacy dashboard
 * takes is unchanged by construction rather than by a filter someone remembered
 * to leave out of it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ExclusionStoreTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private lateinit var db: BodhaDatabase

    @Before
    fun openDb() {
        context.getSharedPreferences("awareness_exclusions", Context.MODE_PRIVATE)
            .edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BodhaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun `an exclusion survives a new store over the same preferences`() {
        val store = ExclusionStore(context)
        store.excludeApp("atlas")
        store.excludeSession(41)

        val reborn = ExclusionStore(context)
        assertEquals(Exclusions(apps = setOf("atlas"), sessions = setOf(41L)), reborn.exclusions.value)
    }

    @Test
    fun `including takes exactly that one back out, and leaves the rest excluded`() {
        val store = ExclusionStore(context)
        store.excludeApp("atlas")
        store.excludeApp("ledger")
        store.excludeSession(41)
        store.excludeSession(42)

        store.includeApp("atlas")
        store.includeSession(42)

        assertEquals(
            Exclusions(apps = setOf("ledger"), sessions = setOf(41L)),
            ExclusionStore(context).exclusions.value,
        )
    }

    /**
     * A file this store cannot read is a surface that will not open, and one
     * unreadable line is not worth that — the tolerant parse `OpenCheckRuleStore`
     * already takes.
     */
    @Test
    fun `a malformed session id is dropped rather than throwing`() {
        context.getSharedPreferences("awareness_exclusions", Context.MODE_PRIVATE)
            .edit()
            .putString("apps", "atlas")
            .putString("sessions", "41\nnot-a-number\n42")
            .commit()

        assertEquals(
            Exclusions(apps = setOf("atlas"), sessions = setOf(41L, 42L)),
            ExclusionStore(context).exclusions.value,
        )
    }

    /**
     * An excluded id whose record retention has taken is a row that can never be
     * drawn and an undo that can never be reached, so a successful read that
     * proves it gone drops it. A read that returned everything prunes nothing.
     */
    @Test
    fun `pruning drops the ids no record answers to and leaves the rest`() {
        val store = ExclusionStore(context)
        store.excludeSession(41)
        store.excludeSession(42)

        store.pruneSessions(setOf(41L, 42L))
        assertEquals(setOf(41L, 42L), store.exclusions.value.sessions)

        store.pruneSessions(setOf(42L))
        assertEquals(setOf(42L), store.exclusions.value.sessions)
        assertEquals(setOf(42L), ExclusionStore(context).exclusions.value.sessions)
    }

    /**
     * AC 3: exclusion never deletes a record. The launch log holds what it held,
     * and the privacy dashboard's count under raw usage is the same number either
     * side of the exclusion (#24, ADR 0013).
     */
    @Test
    fun `excluding writes no record - the launch log's dashboard count is unchanged`() = runBlocking {
        val log = LaunchLog(db.launchRecords())
        log.write("atlas", Instant.parse("2026-08-07T09:13:00Z"), SessionId(1))
        log.write("ledger", Instant.parse("2026-08-07T09:15:00Z"), SessionId(1))
        val before = db.launchRecords().dashboardSummary().count

        val store = ExclusionStore(context)
        store.excludeApp("atlas")
        store.excludeSession(1)

        assertEquals(before, db.launchRecords().dashboardSummary().count)
        assertEquals(2, db.launchRecords().dashboardSummary().count)
        // And the rows themselves, unmoved: the read is the same read.
        assertEquals(2, db.launchRecords().forSession(1).size)
        assertEquals(1, db.launchRecords().forApp("atlas").size)
        assertTrue(!store.exclusions.value.isEmpty)
    }
}
