package com.bodhalauncher.app.entitlement

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.EntitlementSnapshot
import java.time.Instant

/**
 * The locally cached entitlement fact (#22), persisted like the other stores
 * (ADR 0009). A fresh install reads free; the billing integration writes here
 * when it lands, and until then this cache is the whole truth the gate sees.
 */
class EntitlementStore(context: Context) {

    private val prefs = context.getSharedPreferences("entitlement", Context.MODE_PRIVATE)

    val snapshot = mutableStateOf(load())

    fun update(proActive: Boolean, fetchedAt: Instant) {
        snapshot.value = EntitlementSnapshot(proActive, fetchedAt)
        prefs.edit {
            putBoolean(KEY_PRO_ACTIVE, proActive)
            putLong(KEY_FETCHED_AT, fetchedAt.toEpochMilli())
        }
    }

    private fun load(): EntitlementSnapshot = EntitlementSnapshot(
        proActive = prefs.getBoolean(KEY_PRO_ACTIVE, false),
        fetchedAt = prefs.getLong(KEY_FETCHED_AT, -1L)
            .takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
    )

    private companion object {
        const val KEY_PRO_ACTIVE = "pro_active"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}
