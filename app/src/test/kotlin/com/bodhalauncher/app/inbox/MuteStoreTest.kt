package com.bodhalauncher.app.inbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MuteStoreTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a mute survives a restart - a fresh instance reads it back`() {
        MuteStore(context).mute("com.example.noisy")
        val reborn = MuteStore(context)
        assertEquals(setOf("com.example.noisy"), MuteStore.muted.value)
        reborn.unmute("com.example.noisy")
        assertEquals(emptySet<String>(), MuteStore.muted.value)
    }

    @Test
    fun `unmuting one source leaves the others muted`() {
        val store = MuteStore(context)
        store.mute("com.example.one")
        store.mute("com.example.two")
        store.unmute("com.example.one")
        assertEquals(setOf("com.example.two"), MuteStore.muted.value)
        MuteStore(context)
        assertEquals(setOf("com.example.two"), MuteStore.muted.value)
    }
}
