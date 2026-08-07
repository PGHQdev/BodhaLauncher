package com.bodhalauncher.app.intent

import android.content.Context
import com.bodhalauncher.engine.IntentCategory
import com.bodhalauncher.engine.IntentSignal
import com.bodhalauncher.engine.PromptDecision
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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

    /**
     * The intents stated at or after [from] (#172): two of ADR 0013's three
     * signals, since both are written here.
     *
     * A dismissal states nothing, so it is not one. A prompt answer carries the
     * session it was asked under; an Open Check intention carries none, which is
     * what leaves it to be attributed by the span it fell inside.
     *
     * The text rides along where the record has any (#173), because the Session
     * view reads back what was stated and this file is the only place the words
     * exist — the event log carries none by construction.
     *
     * An unreadable line is skipped rather than failing the read: a session
     * classifies as unclassified, which is the honest answer when the signal
     * cannot be found.
     */
    fun signalsSince(from: Instant): List<IntentSignal> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            runCatching {
                val record = JSONObject(line)
                val at = record.getLong("at")
                if (at < from.toEpochMilli()) return@runCatching null
                if (record.getString("category") == CATEGORY_DISMISSED) return@runCatching null
                IntentSignal(
                    at = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()),
                    session = if (record.isNull("session")) null else record.getLong("session"),
                    text = if (record.isNull("text")) null else record.getString("text"),
                )
            }.getOrNull()
        }
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
