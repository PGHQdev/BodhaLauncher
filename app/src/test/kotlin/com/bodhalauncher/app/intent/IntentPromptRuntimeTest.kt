package com.bodhalauncher.app.intent

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.engine.PromptDecision
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.TriggerSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * The prompt's dismissal, which has two callers that must land in the same
 * place: the user's swipe-down and the phone session ending under the sheet
 * (ADR 0011, #134). The record is what Awareness reads later, so a dismissal
 * that logged its event and wrote no record would leave the two stores
 * disagreeing about the same prompt.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class IntentPromptRuntimeTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val runtime by lazy { IntentPromptRuntime(context, SessionRuntime(context)) }
    private val decision =
        PromptDecision(SessionId(7), Instant.parse("2026-01-01T09:00:00Z"), TriggerSource.Reflexive)

    @Test
    fun `a dismissal records the outcome and drops the pending decision`() {
        runtime.promptDue.value = decision

        runtime.dismiss(decision)

        assertEquals(listOf("dismissed"), recordedCategories())
        assertNull(runtime.promptDue.value)
    }

    @Test
    fun `a dismissal still records after the ending session dropped the pending decision`() {
        runtime.promptDue.value = decision
        // What the session end does first: this runtime's own listener is
        // registered before composition, so it clears the decision ahead of the
        // sheet's dismissal reaching here (#134).
        runtime.promptDue.value = null

        runtime.dismiss(decision)

        assertEquals(listOf("dismissed"), recordedCategories())
    }

    private fun recordedCategories(): List<String> =
        File(context.filesDir, "intent_records.jsonl")
            .readLines()
            .map { JSONObject(it).getString("category") }
}
