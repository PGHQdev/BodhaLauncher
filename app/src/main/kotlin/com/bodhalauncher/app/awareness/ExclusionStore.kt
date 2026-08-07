package com.bodhalauncher.app.awareness

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.Exclusions

/**
 * What the reader has taken out of Awareness, persisted locally (#178, ADR 0009).
 *
 * SharedPreferences rather than a Room table, and the choice does real work.
 * #19's rule already puts preferences in key-value, and keeping the exclusions
 * out of the database is what makes "an excluded item still counts in the privacy
 * dashboard's category counts" true **by construction**: the dashboard counts
 * rows in `launch_record` and `session_record`, and an exclusion moves no row.
 * A column on those tables would have made the same guarantee a thing someone
 * has to remember not to break.
 *
 * Newline-joined ids under two keys, `PinStore`'s shape, because the two lists
 * are exactly what that store already holds — a set of app ids that the reader
 * curated, read whole on every access.
 *
 * **Clear-behavioural-history does not reach this file** (ADR 0019), and that is
 * deliberate rather than an oversight: an exclusion is the reader's instruction
 * about what to render, not a record of what they did. Clearing it would put
 * back on screen exactly the things they asked to have taken off, which reads as
 * a bug however it is explained. The records the exclusion hides are cleared by
 * that row, as they always were.
 *
 * One instance per composition of the Awareness surface, so every view shares the
 * one [exclusions] state and a toggle on any of them is a recomposition rather
 * than a re-read.
 */
class ExclusionStore(context: Context) {

    private val prefs = context.getSharedPreferences("awareness_exclusions", Context.MODE_PRIVATE)

    val exclusions = mutableStateOf(load())

    fun excludeApp(id: String) = update { it.copy(apps = it.apps + id) }

    fun includeApp(id: String) = update { it.copy(apps = it.apps - id) }

    fun excludeSession(id: Long) = update { it.copy(sessions = it.sessions + id) }

    fun includeSession(id: Long) = update { it.copy(sessions = it.sessions - id) }

    /**
     * Drops the excluded session ids no record answers to any more.
     *
     * Retention deletes a session record thirty days on (ADR 0028) while the
     * exclusion outlives it, and an id with nothing behind it is an entry in the
     * undo list that can never be undone from — it has no row to draw and no
     * session to put back. [keep] is the ids a **successful** read actually
     * returned, so a read that failed prunes nothing rather than mistaking its
     * own failure for a retention cut.
     */
    fun pruneSessions(keep: Set<Long>) {
        val gone = exclusions.value.sessions - keep
        if (gone.isEmpty()) return
        update { it.copy(sessions = it.sessions - gone) }
    }

    private fun update(edit: (Exclusions) -> Exclusions) {
        val next = edit(exclusions.value)
        exclusions.value = next
        prefs.edit {
            putString(KEY_APPS, next.apps.joinToString("\n"))
            putString(KEY_SESSIONS, next.sessions.joinToString("\n"))
        }
    }

    /**
     * A line that is not a session id is dropped rather than thrown on, the
     * tolerant parse `OpenCheckRuleStore` already takes: a file this store cannot
     * read is a surface that will not open, and one unreadable exclusion is not
     * worth that.
     */
    private fun load(): Exclusions = Exclusions(
        apps = lines(KEY_APPS).toSet(),
        sessions = lines(KEY_SESSIONS).mapNotNull { it.toLongOrNull() }.toSet(),
    )

    private fun lines(key: String): List<String> =
        prefs.getString(key, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    private companion object {
        const val KEY_APPS = "apps"
        const val KEY_SESSIONS = "sessions"
    }
}
