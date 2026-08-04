package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrivacyDashboardTest {

    @Test
    fun `a fresh install shows explicit none rows, not empty sections`() {
        val dashboard = resolvePrivacyDashboard(DashboardInputs())

        assertEquals(listOf(DashboardRow.None), dashboard.permissions)
        assertEquals(listOf(DashboardRow.None), dashboard.connectors)
        assertEquals(listOf(DashboardRow.None), dashboard.cloudFeatures)
    }

    @Test
    fun `granted permissions list what each enables`() {
        val dashboard = resolvePrivacyDashboard(
            DashboardInputs(
                permissions = listOf(
                    PermissionStatus(Capability.UsageAccess, granted = true),
                    PermissionStatus(Capability.Contacts, granted = false),
                )
            )
        )

        assertEquals(1, dashboard.permissions.size)
        val row = assertIs<DashboardRow.Permission>(dashboard.permissions.single())
        assertEquals(Capability.UsageAccess, row.capability)
        assertTrue(row.enables.isNotBlank())
    }

    @Test
    fun `local data rows carry count and retention`() {
        val dashboard = resolvePrivacyDashboard(
            DashboardInputs(
                dataCategories = listOf(
                    DataCategorySummary(RetentionCategory.RawUsageEvents, count = 1200, retentionDays = 30),
                    DataCategorySummary(RetentionCategory.Reflections, count = 3, retentionDays = null),
                )
            )
        )

        val usage = assertIs<DashboardRow.Data>(dashboard.localData[0])
        assertEquals(1200, usage.count)
        assertEquals(30, usage.retentionDays)
        val reflections = assertIs<DashboardRow.Data>(dashboard.localData[1])
        assertEquals(null, reflections.retentionDays)
    }

    @Test
    fun `export and delete-local are always available`() {
        val dashboard = resolvePrivacyDashboard(DashboardInputs())

        assertTrue(dashboard.canExport)
        assertTrue(dashboard.canDeleteLocalData)
    }

    @Test
    fun `delete-account appears only when signed in`() {
        assertFalse(resolvePrivacyDashboard(DashboardInputs(signedIn = false)).canDeleteAccount)
        assertTrue(resolvePrivacyDashboard(DashboardInputs(signedIn = true)).canDeleteAccount)
    }

    @Test
    fun `future connectors and cloud features appear once configured`() {
        val dashboard = resolvePrivacyDashboard(
            DashboardInputs(connectors = listOf("Todoist"), cloudFeatures = listOf("Encrypted backup"))
        )

        assertEquals(listOf<DashboardRow>(DashboardRow.Named("Todoist")), dashboard.connectors)
        assertEquals(listOf<DashboardRow>(DashboardRow.Named("Encrypted backup")), dashboard.cloudFeatures)
    }
}
