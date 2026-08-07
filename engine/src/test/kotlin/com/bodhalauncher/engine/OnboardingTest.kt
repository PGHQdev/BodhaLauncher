package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingTest {

    @Test
    fun `a device that never onboarded opens at the promise step`() {
        assertEquals(OnboardingStep.Promise, resolveOnboardingStep(complete = false, furthestPassed = 0))
    }

    @Test
    fun `the completion flag ends the flow whatever the marker says`() {
        assertNull(resolveOnboardingStep(complete = true, furthestPassed = 0))
        assertNull(resolveOnboardingStep(complete = true, furthestPassed = 3))
    }

    @Test
    fun `a marker past every built step resolves the flow`() {
        assertNull(resolveOnboardingStep(complete = false, furthestPassed = OnboardingStep.entries.size))
    }

    @Test
    fun `a passed step is never re-offered on resume`() {
        // With later steps built, marker n opens step n; today marker 1 resolves.
        OnboardingStep.entries.forEachIndexed { index, step ->
            assertEquals(step, resolveOnboardingStep(complete = false, furthestPassed = index))
        }
    }

    @Test
    fun `a corrupt negative marker still opens the first step rather than crashing`() {
        assertEquals(OnboardingStep.Promise, resolveOnboardingStep(complete = false, furthestPassed = -1))
    }

    @Test
    fun `onboarding is absent from the surface set`() {
        // The flow is unreachable by swipe, Search or Home control because no
        // surface exists for them to resolve. The whole set is pinned, so any
        // future addition — under whatever name — re-answers this deliberately.
        assertEquals(
            listOf("Home", "Search", "App Library", "Awareness", "Today", "Focus", "Settings"),
            Surface.entries.map { it.title },
        )
    }
}
