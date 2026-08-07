package com.bodhalauncher.app.awareness

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.engine.DashboardInputs
import com.bodhalauncher.engine.DashboardRow
import com.bodhalauncher.engine.EntitlementSnapshot
import com.bodhalauncher.engine.RetentionCategory
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.resolveAwarenessWindow
import com.bodhalauncher.engine.resolvePrivacyDashboard
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Bodha's own launch log (#173, ADR 0013): one record per launch it mediates —
 * app identity, timestamp, session — needing no permission and keeping full
 * history, where the system's `UsageEvents` keeps a few days.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class LaunchLogTest {

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.parse("2026-08-07T14:00:00")
    }

    private lateinit var db: BodhaDatabase
    private lateinit var log: LaunchLog

    private fun at(text: String): Instant =
        LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant()

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BodhaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        log = LaunchLog(db.launchRecords())
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun `a launch writes exactly one record - app, time and session`() = runBlocking {
        log.write("com.example.atlas", at("2026-08-07T09:42:00"), SessionId(1))

        val records = db.launchRecords().forSession(1)
        assertEquals(1, records.size)
        assertEquals("com.example.atlas", records[0].appId)
        assertEquals(at("2026-08-07T09:42:00").toEpochMilli(), records[0].atMillis)
        assertEquals(1L, records[0].sessionId)
    }

    @Test
    fun `a launch with no session open is recorded, attributed to none`() = runBlocking {
        log.write("com.example.atlas", at("2026-08-07T09:42:00"), session = null)

        assertEquals(1, db.launchRecords().count())
        assertEquals(emptyList<LaunchRecordEntity>(), db.launchRecords().forSession(1))
    }

    @Test
    fun `a session's launches come back in the order they happened`() = runBlocking {
        log.write("second", at("2026-08-07T09:45:00"), SessionId(1))
        log.write("first", at("2026-08-07T09:42:00"), SessionId(1))
        log.write("elsewhere", at("2026-08-07T09:43:00"), SessionId(2))

        assertEquals(
            listOf("first", "second"),
            db.launchRecords().forSession(1).map { it.appId },
        )
    }

    /** One app opened up (#174): its own records, and the near end at the top. */
    @Test
    fun `forApp returns only that app's records, newest first`() = runBlocking {
        log.write("atlas", at("2026-08-07T09:42:00"), SessionId(1))
        log.write("ledger", at("2026-08-07T09:43:00"), SessionId(1))
        log.write("atlas", at("2026-08-07T21:30:00"), SessionId(2))
        log.write("atlas", at("2026-08-06T09:10:00"), session = null)

        val records = db.launchRecords().forApp("atlas")

        assertEquals(listOf("atlas", "atlas", "atlas"), records.map { it.appId })
        assertEquals(
            listOf(
                at("2026-08-07T21:30:00").toEpochMilli(),
                at("2026-08-07T09:42:00").toEpochMilli(),
                at("2026-08-06T09:10:00").toEpochMilli(),
            ),
            records.map { it.atMillis },
        )
    }

    /**
     * Retention governs what exists (ADR 0028) and entitlement governs what
     * renders (ADR 0005), in the render path. The query knows about neither, which
     * is what keeps the Pro window a recomposition rather than a second read.
     */
    @Test
    fun `forApp has no time bound, so every retained record reaches the caller`() = runBlocking {
        log.write("atlas", at("2025-01-01T09:00:00"), session = null)
        log.write("atlas", at("2026-08-07T09:42:00"), SessionId(1))

        assertEquals(2, db.launchRecords().forApp("atlas").size)
    }

    /**
     * One read, two tiers (#177). The query is the same rows either way and the
     * window is the only thing that differs, which is what makes upgrading a
     * recomposition rather than a second trip to the database — and what would be
     * false the moment a bound reached this DAO.
     */
    @Test
    fun `the launch query takes no bound, so both tiers read the same rows`() = runBlocking {
        log.write("atlas", at("2026-06-01T09:00:00"), session = null)
        log.write("atlas", at("2026-08-07T09:42:00"), SessionId(1))

        val rows = db.launchRecords().forApp("atlas").map { it.toRecord() }
        val free = resolveAwarenessWindow(EntitlementSnapshot(proActive = false), NOW).launches(rows)
        val pro = resolveAwarenessWindow(EntitlementSnapshot(proActive = true), NOW).launches(rows)

        assertEquals(2, rows.size)
        assertEquals(1, free.records.size)
        assertEquals(2, pro.records.size)
        assertNotNull(free.boundary)
        assertNull(pro.boundary)
    }

    /**
     * The window lives in the render path and nowhere else (#177), asserted
     * through the DAO and the dashboard rather than through a surface: a reader
     * that is not a view sees every retained record whatever the tier.
     */
    @Test
    fun `the launch log hands back every retained record, and only the window withholds one`() = runBlocking {
        log.write("atlas", at("2026-06-01T09:00:00"), session = null)
        log.write("atlas", at("2026-08-07T09:42:00"), SessionId(1))

        assertEquals(2, db.launchRecords().count())
        assertEquals(2, db.launchRecords().dashboardSummary().count)
        assertEquals(2, db.launchRecords().forApp("atlas").size)

        val rows = db.launchRecords().forApp("atlas").map { it.toRecord() }
        assertEquals(
            listOf(LocalDateTime.parse("2026-08-07T09:42:00")),
            resolveAwarenessWindow(EntitlementSnapshot(proActive = false), NOW)
                .launches(rows).records.map { it.at },
        )
    }

    /**
     * The record carries app identity and a timestamp, and there is no field it
     * could carry anything else in (ADR 0013) — which is the whole reason this
     * store exists beside the app-name-free event log.
     */
    @Test
    fun `the record maps to what the engine reads, and nothing more`() = runBlocking {
        log.write("com.example.atlas", at("2026-08-07T09:42:00"), SessionId(7))

        val record = db.launchRecords().forSession(7).single().toRecord()
        assertEquals("com.example.atlas", record.appId)
        assertEquals(LocalDateTime.parse("2026-08-07T09:42:00"), record.at)
        assertEquals(7L, record.session)
    }

    @Test
    fun `retention's cut deletes by the moment of the launch`() = runBlocking {
        log.write("old", at("2026-07-01T10:00:00"), SessionId(1))
        log.write("recent", at("2026-08-07T10:00:00"), SessionId(2))

        db.launchRecords().deleteBefore(at("2026-08-01T04:00:00").toEpochMilli())

        assertEquals(1, db.launchRecords().count())
        assertNull(db.launchRecords().forSession(1).firstOrNull())
    }

    /**
     * The store's row in the privacy dashboard (#24): asserted at the resolver,
     * because #146 has no surface yet. It shares RawUsageEvents with the session
     * records, and the resolver folds a category into one row — so the dashboard
     * gives one raw-usage answer however many stores fill it.
     */
    @Test
    fun `the launch log supplies a data-category row with its count and window`() = runBlocking {
        log.write("com.example.atlas", at("2026-08-07T09:42:00"), SessionId(1))
        log.write("com.example.ledger", at("2026-08-07T09:45:00"), SessionId(1))

        val summary = db.launchRecords().dashboardSummary()
        assertEquals(RetentionCategory.RawUsageEvents, summary.category)
        assertEquals(2, summary.count)
        assertEquals(RetentionCategory.RawUsageEvents.defaultDays, summary.retentionDays)

        val dashboard = resolvePrivacyDashboard(DashboardInputs(dataCategories = listOf(summary)))
        assertEquals(
            listOf(DashboardRow.Data(RetentionCategory.RawUsageEvents, count = 2, retentionDays = 30)),
            dashboard.localData,
        )
    }
}
