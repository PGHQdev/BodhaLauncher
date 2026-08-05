package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * Whether the "→ for actions" hint has been retired (ADR 0023), persisted
 * locally (ADR 0009 — it is a boolean that never leaves the phone).
 *
 * It lives in its own preference file rather than beside the launch log because
 * ADR 0019's delete row clears *behavioural history*, and a record that the user
 * knows how their launcher works is not behaviour — it is closer to onboarding's
 * completion flag. Nothing clears this; the separate file is what makes that
 * true by construction rather than by whoever writes the delete remembering.
 *
 * [retired] is set by the key being pressed, never by the hint being drawn: a
 * display counter would retire the hint from someone who never noticed it.
 */
class ActionsKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences("actions_key", Context.MODE_PRIVATE)

    val retired = mutableStateOf(prefs.getBoolean(KEY_RETIRED, false))

    fun retire() {
        if (retired.value) return
        retired.value = true
        prefs.edit { putBoolean(KEY_RETIRED, true) }
    }

    private companion object {
        const val KEY_RETIRED = "retired"
    }
}
