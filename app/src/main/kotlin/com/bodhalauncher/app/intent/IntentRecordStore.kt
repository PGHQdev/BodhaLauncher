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

    /**
     * A typed Open Check intention (#76): same file, same retention pruning,
     * never anywhere else — the event log carries no text by construction.
     */
    fun appendOpenCheckIntention(text: String) {
        append(CATEGORY_OPEN_CHECK, text, TRIGGER_OPEN_CHECK, session = null)
    }

    private fun append(decision: PromptDecision, category: String, text: String?) {
        append(category, text, decision.trigger.name, decision.session.value)
    }

    private fun append(category: String, text: String?, trigger: String, session: Any?) {
        val record = JSONObject()
            .put("category", category)
            .putOpt("text", text)
            .put("at", Instant.now().toEpochMilli())
            .put("trigger", trigger)
            .putOpt("session", session)
        file.appendText(record.toString() + "\n")
    }

    /** Drops records older than [epochMillis] — the retention worker's arm here (#19). */
    fun pruneBefore(epochMillis: Long) {
        if (!file.exists()) return
        val kept = file.readLines().filter { line ->
            runCatching { JSONObject(line).getLong("at") >= epochMillis }.getOrDefault(false)
        }
        file.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
    }

    private companion object {
        /** Free text submitted without picking one of the six categories. */
        const val CATEGORY_OTHER = "other"
        const val CATEGORY_DISMISSED = "dismissed"
        const val CATEGORY_OPEN_CHECK = "open_check"
        const val TRIGGER_OPEN_CHECK = "open_check"
    }
}
