package com.bodhalauncher.app.onboarding

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.OnboardingStep

/**
 * Onboarding's completion flag and progress marker (#135, ADR 0018), persisted
 * locally (ADR 0009). Like ActionsKeyStore, this is not behavioural history —
 * ADR 0019's delete row never clears it, which its own preference file makes
 * true by construction.
 *
 * The marker holds no step content — each step commits to its real store on
 * advance — only the furthest step passed, skips included, so resume never
 * re-offers a step the user already answered or declined.
 */
class OnboardingStore(context: Context) {

    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    val complete = mutableStateOf(prefs.getBoolean(KEY_COMPLETE, false))
    val furthestPassed = mutableIntStateOf(prefs.getInt(KEY_FURTHEST, 0))

    /** Every advance writes the marker — a skip is an advance too. */
    fun advance(step: OnboardingStep) {
        val passed = step.ordinal + 1
        if (passed <= furthestPassed.intValue) return
        furthestPassed.intValue = passed
        prefs.edit { putInt(KEY_FURTHEST, passed) }
    }

    /** Written when the flow resolves; once set, onboarding never opens again. */
    fun finish() {
        if (complete.value) return
        complete.value = true
        prefs.edit { putBoolean(KEY_COMPLETE, true) }
    }

    private companion object {
        const val KEY_COMPLETE = "complete"
        const val KEY_FURTHEST = "furthest_passed"
    }
}
