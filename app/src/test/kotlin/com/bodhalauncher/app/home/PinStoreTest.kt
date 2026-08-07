package com.bodhalauncher.app.home

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins belong to an arrangement (ADR 0016); hidden suggestions do not. The
 * upgrade path matters most: pins written by a pre-arrangement build are the
 * default arrangement's pins, in the order they were written.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class PinStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        prefs().edit().clear().commit()
    }

    @Test
    fun `pins written before arrangements are the default arrangement's, in order`() {
        prefs().edit().putString("pinned", "com.a\ncom.b\ncom.c").commit()

        val store = PinStore(context)

        assertEquals(listOf("com.a", "com.b", "com.c"), store.pinned.value)
        assertEquals(listOf("com.a", "com.b", "com.c"), store.pinsOf(PinStore.DEFAULT_ARRANGEMENT))
    }

    @Test
    fun `pinning and unpinning the active arrangement survive a fresh store`() {
        val store = PinStore(context)
        store.pin("com.a")
        store.pin("com.b")
        store.pin("com.a")
        store.unpin("com.a")

        assertEquals(listOf("com.b"), store.pinned.value)
        assertEquals(listOf("com.b"), PinStore(context).pinsOf(PinStore.DEFAULT_ARRANGEMENT))
    }

    @Test
    fun `an arrangement never written has no pins and does not borrow the default's`() {
        val store = PinStore(context)
        store.pin("com.a")

        assertEquals(emptyList<String>(), store.pinsOf("focus"))
        assertEquals(listOf("com.a"), store.pinsOf(PinStore.DEFAULT_ARRANGEMENT))
    }

    @Test
    fun `hiding stays global and leaves the arrangement's pins alone`() {
        val store = PinStore(context)
        store.pin("com.a")
        store.hide("com.b")

        val reopened = PinStore(context)
        assertEquals(listOf("com.a"), reopened.pinsOf(PinStore.DEFAULT_ARRANGEMENT))
        assertEquals(setOf("com.b"), reopened.hidden.value)

        reopened.unpin("com.a")
        assertEquals(setOf("com.b"), PinStore(context).hidden.value)
    }

    private fun prefs() = context.getSharedPreferences("home_pins", Context.MODE_PRIVATE)
}
