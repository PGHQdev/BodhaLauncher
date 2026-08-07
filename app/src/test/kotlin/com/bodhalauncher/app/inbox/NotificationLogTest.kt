package com.bodhalauncher.app.inbox

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.engine.DigestSection
import com.bodhalauncher.engine.RetentionCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationLogTest {

    private lateinit var db: BodhaDatabase
    private lateinit var dao: NotificationLogDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BodhaDatabase::class.java
        ).build()
        dao = db.notificationLog()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun record(
        keyHash: String = "k1",
        section: DigestSection = DigestSection.Updates,
        at: Long = 1_000,
    ) = NotificationRecordEntity(
        keyHash = keyHash,
        appPackage = "com.example.app",
        section = section.name,
        category = null,
        postedAtMillis = at,
        updatedAtMillis = at,
    )

    @Test
    fun `the record carries app, section, category and timestamps only`() {
        // ADR 0015's metadata-only rule, pinned the same way the event log pins
        // its own shape: there is no field that could carry a title, a body or
        // a sender name, and the key is a hash rather than the system's string.
        val fields = NotificationRecordEntity::class.java.declaredFields
            .map { it.name }.filterNot { it.startsWith("$") }.toSet()
        assertEquals(
            setOf("keyHash", "appPackage", "section", "category", "postedAtMillis", "updatedAtMillis"),
            fields,
        )
    }

    @Test
    fun `a notification updating in place counts once`() = runBlocking {
        dao.upsert(record(at = 1_000))
        dao.upsert(record(at = 2_000))
        val counts = dao.countsBetween(0, 10_000)
        assertEquals(listOf(SectionCount(DigestSection.Updates.name, 1)), counts)
    }

    @Test
    fun `counts group by section over the window`() = runBlocking {
        dao.upsert(record(keyHash = "a", section = DigestSection.People))
        dao.upsert(record(keyHash = "b", section = DigestSection.People))
        dao.upsert(record(keyHash = "c", section = DigestSection.Silent))
        dao.upsert(record(keyHash = "old", section = DigestSection.Silent, at = -5))
        val counts = dao.countsBetween(0, 10_000).associate { it.section to it.count }
        assertEquals(mapOf("People" to 2, "Silent" to 1), counts)
    }

    @Test
    fun `a lockdown removal drops the row immediately`() = runBlocking {
        dao.upsert(record(keyHash = "locked"))
        dao.deleteByKey("locked")
        assertEquals(0, dao.count())
    }

    @Test
    fun `retention prunes strictly older rows`() = runBlocking {
        dao.upsert(record(keyHash = "old", at = 100))
        dao.upsert(record(keyHash = "kept", at = 200))
        dao.deleteBefore(200)
        assertEquals(1, dao.count())
    }

    @Test
    fun `the store contributes its dashboard row`() = runBlocking {
        dao.upsert(record())
        val summary = dao.dashboardSummary()
        assertEquals(RetentionCategory.NotificationContent, summary.category)
        assertEquals(1, summary.count)
        assertEquals(7, summary.retentionDays)
    }
}
