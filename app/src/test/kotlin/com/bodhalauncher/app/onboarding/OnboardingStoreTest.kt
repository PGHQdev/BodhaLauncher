package com.bodhalauncher.app.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.OnboardingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class OnboardingStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `starts incomplete at the first step`() {
        val store = OnboardingStore(context)
        assertFalse(store.complete.value)
        assertEquals(0, store.furthestPassed.intValue)
    }

    @Test
    fun `advance writes the marker and survives a new instance`() {
        OnboardingStore(context).advance(OnboardingStep.Promise)
        assertEquals(1, OnboardingStore(context).furthestPassed.intValue)
    }

    @Test
    fun `the marker never moves backwards`() {
        val store = OnboardingStore(context)
        store.advance(OnboardingStep.Promise)
        store.advance(OnboardingStep.Promise)
        assertEquals(1, store.furthestPassed.intValue)
    }

    @Test
    fun `finish persists the completion flag`() {
        OnboardingStore(context).finish()
        assertTrue(OnboardingStore(context).complete.value)
    }
}
