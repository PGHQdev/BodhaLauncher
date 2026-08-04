package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CapabilityEducationTest {

    @Test
    fun `granted capability is live regardless of history or entry`() {
        for (entry in EducationEntry.entries) {
            val resolution = resolveCapability(
                capability = Capability.UsageAccess,
                granted = true,
                educationShown = true,
                entry = entry,
            )
            assertEquals(CapabilityResolution.Live, resolution)
        }
    }

    @Test
    fun `first feature touch shows the education screen`() {
        val resolution = resolveCapability(
            capability = Capability.NotificationAccess,
            granted = false,
            educationShown = false,
            entry = EducationEntry.FeatureTouch,
        )

        val educate = assertIs<CapabilityResolution.Educate>(resolution)
        assertEquals(Capability.NotificationAccess, educate.screen.capability)
    }

    @Test
    fun `a feature touch after the screen was already shown degrades without prompting`() {
        val resolution = resolveCapability(
            capability = Capability.UsageAccess,
            granted = false,
            educationShown = true,
            entry = EducationEntry.FeatureTouch,
        )

        assertEquals(CapabilityResolution.Degraded, resolution)
    }

    @Test
    fun `an explicit user request re-opens education even after earlier denial`() {
        val resolution = resolveCapability(
            capability = Capability.Contacts,
            granted = false,
            educationShown = true,
            entry = EducationEntry.UserRequest,
        )

        assertIs<CapabilityResolution.Educate>(resolution)
    }

    @Test
    fun `revoked capability degrades on feature touch instead of re-prompting`() {
        // Revocation looks like: education was shown long ago (it was granted once), now not granted.
        val resolution = resolveCapability(
            capability = Capability.Calendar,
            granted = false,
            educationShown = true,
            entry = EducationEntry.FeatureTouch,
        )

        assertEquals(CapabilityResolution.Degraded, resolution)
    }

    @Test
    fun `every capability has an education screen with all statements filled`() {
        for (capability in Capability.entries) {
            val educate = assertIs<CapabilityResolution.Educate>(
                resolveCapability(capability, granted = false, educationShown = false, entry = EducationEntry.UserRequest)
            )
            with(educate.screen) {
                assertTrue(dataAccessed.isNotBlank())
                assertTrue(processing.isNotBlank())
                assertTrue(withoutIt.isNotBlank())
                assertTrue(feature.isNotBlank())
            }
        }
    }

    @Test
    fun `education screens state that processing stays on the phone`() {
        val educate = assertIs<CapabilityResolution.Educate>(
            resolveCapability(Capability.UsageAccess, granted = false, educationShown = false, entry = EducationEntry.FeatureTouch)
        )

        assertTrue(educate.screen.processing.contains("phone", ignoreCase = true))
    }
}
