package com.bodhalauncher.app.intent

import android.content.Context
import com.bodhalauncher.engine.IntentCategory
import com.bodhalauncher.engine.PromptDecision
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * One line per prompt outcome, appended to a local file (ADR 0009): the spec's
 * intent record — category, optional text, timestamp, trigger source, session —
 * with a dismissal recorded the same way, category "dismissed". Awareness and
 * Reflection read these later; nothing here does.
 */
class IntentRecordStore(context: Context) {

    private val file = File(context.filesDir, "intent_records.jsonl")

    fun appendSelection(decision: PromptDecision, category: IntentCategory?, text: String?) {
        append(decision, category?.name ?: CATEGORY_OTHER, text)
    }

    fun appendDismissal(decision: PromptDecision) {
        append(decision, CATEGORY_DISMISSED, text = null)
    }

    private fun append(decision: PromptDecision, category: String, text: String?) {
        val record = JSONObject()
            .put("category", category)
            .putOpt("text", text)
            .put("at", Instant.now().toEpochMilli())
            .put("trigger", decision.trigger.name)
            .put("session", decision.session.value)
        file.appendText(record.toString() + "\n")
    }

    private companion object {
        /** Free text submitted without picking one of the six categories. */
        const val CATEGORY_OTHER = "other"
        const val CATEGORY_DISMISSED = "dismissed"
    }
}
