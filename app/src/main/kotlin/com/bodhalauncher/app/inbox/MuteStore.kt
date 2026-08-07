package com.bodhalauncher.app.inbox

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * The sources muted in Bodha (#164, ADR 0015): package names only, persisted
 * locally so the mute survives a restart and a listener reconnect (ADR 0009 —
 * a set of package names that never leaves the phone). Bodha-local by
 * construction: nothing here touches the app's notifications anywhere else.
 *
 * The set itself lives on the companion, because the listener and the UI hold
 * different instances and must see one truth: a mute takes effect at the edge
 * from that moment, without a rebind.
 */
class MuteStore(context: Context) {

    private val prefs = context.getSharedPreferences("muted_sources", Context.MODE_PRIVATE)

    init {
        muted.value = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
    }

    fun mute(appPackage: String) {
        muted.value = muted.value + appPackage
        prefs.edit { putStringSet(KEY_PACKAGES, muted.value) }
    }

    fun unmute(appPackage: String) {
        muted.value = muted.value - appPackage
        prefs.edit { putStringSet(KEY_PACKAGES, muted.value) }
    }

    companion object {
        /** The muted packages, readable without an instance — the listener's edge check. */
        val muted = mutableStateOf<Set<String>>(emptySet())

        private const val KEY_PACKAGES = "packages"
    }
}
