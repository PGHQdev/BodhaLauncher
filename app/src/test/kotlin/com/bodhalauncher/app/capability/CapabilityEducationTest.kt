package com.bodhalauncher.app.capability

import android.app.AppOpsManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bodhalauncher.app.data.EventLogDao
import com.bodhalauncher.app.data.EventLogEntity
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.EventType
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The app-side half of the education flow (#157): one host answers every
 * touchpoint, so the sheet, the denial memory and the two events are asserted
 * here rather than by driving three surfaces. The rule itself is the engine's.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class CapabilityEducationTest {

    private lateinit var context: Context
    private lateinit var store: EducationStateStore
    private lateinit var dao: RecordingDao
    private lateinit var education: CapabilityEducation

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("capability_education", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = EducationStateStore(context)
        dao = RecordingDao()
        education = CapabilityEducation(CapabilityEdge(context), store, EventLogger(dao))
        // The shadow app-ops manager allows every op by default; start ungranted.
        setUsageAccess(AppOpsManager.MODE_ERRORED)
    }

    @Test
    fun `a first feature touch educates and a later one degrades quietly`() {
        education.ask(Capability.Calendar, EducationEntry.FeatureTouch)
        assertNotNull(education.showing)
        education.onSkip()

        education.ask(Capability.Calendar, EducationEntry.FeatureTouch)
        assertNull(education.showing)
    }

    @Test
    fun `an explicit request educates again after a denial`() {
        education.ask(Capability.UsageAccess, EducationEntry.FeatureTouch)
        education.onSkip()

        education.ask(Capability.UsageAccess, EducationEntry.UserRequest)
        assertEquals(Capability.UsageAccess, education.showing?.capability)
    }

    @Test
    fun `the screen counts as delivered when it opens, not when it is answered`() {
        education.ask(Capability.Contacts, EducationEntry.FeatureTouch)

        assertEquals(true, store.shown(Capability.Contacts))
    }

    @Test
    fun `skipping closes the sheet and logs it`() {
        education.ask(Capability.Calendar, EducationEntry.UserRequest)
        education.onSkip()

        assertNull(education.showing)
        assertEquals(listOf(EventType.PermissionSkipped), dao.awaitTypes(1))
    }

    @Test
    fun `the sheet closes with the surface that asked, answering nothing`() {
        education.ask(Capability.Calendar, EducationEntry.FeatureTouch)
        education.close()

        assertNull(education.showing)
        assertEquals(emptyList<EventType>(), dao.awaitTypes(0))
        // Closing is not an answer, but the screen was still delivered (#18).
        assertEquals(true, store.shown(Capability.Calendar))
    }

    @Test
    fun `a grant observed after education logs once, and never for an unasked capability`() {
        education.ask(Capability.UsageAccess, EducationEntry.FeatureTouch)
        education.onContinue()
        grantUsageAccess()

        education.logGrantsObserved()
        education.logGrantsObserved()

        assertEquals(listOf(EventType.PermissionEnabled), dao.awaitTypes(1))
    }

    @Test
    fun `a grant made without ever seeing the sheet is not logged`() {
        grantUsageAccess()

        education.logGrantsObserved()

        assertEquals(emptyList<EventType>(), dao.awaitTypes(0))
    }

    private fun grantUsageAccess() = setUsageAccess(AppOpsManager.MODE_ALLOWED)

    private fun setUsageAccess(mode: Int) {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
            mode,
        )
    }
}

/** The logger writes off the main thread (#25), so the assertion waits for the row. */
private class RecordingDao : EventLogDao {

    private val rows = mutableListOf<EventLogEntity>()

    override suspend fun insert(event: EventLogEntity) {
        synchronized(rows) { rows += event }
    }

    fun awaitTypes(expected: Int): List<EventType> {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            val types = synchronized(rows) { rows.map { EventType.valueOf(it.type) } }
            if (types.size >= expected) {
                // A settle window: an extra event would fail the assertion, not pass unseen.
                Thread.sleep(100)
                return synchronized(rows) { rows.map { EventType.valueOf(it.type) } }
            }
            Thread.sleep(20)
        }
        return synchronized(rows) { rows.map { EventType.valueOf(it.type) } }
    }

    override suspend fun deleteBefore(cutoffMillis: Long) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun between(fromMillis: Long, toMillis: Long): List<EventLogEntity> =
        synchronized(rows) { rows.toList() }
    override suspend fun count(): Int = synchronized(rows) { rows.size }
}
