package com.bodhalauncher.app.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bodhalauncher.engine.ContextMode
import com.bodhalauncher.engine.ManualSwitch
import com.bodhalauncher.engine.ModeNameError
import com.bodhalauncher.engine.ScheduleWindow
import com.bodhalauncher.engine.manualSwitchExpired
import com.bodhalauncher.engine.validateModeName
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * The user's context modes (ordered) and the manual switch, persisted locally
 * (ADR 0009, ADR 0016). Nothing is pre-made: the list starts empty and the
 * default arrangement is not a mode — it is the absence of a choice. Free and
 * uncapped (ADR 0005). The pins themselves live in [PinStore], one arrangement
 * per mode; this store carries the names, their windows and their order, and
 * keeps that one in step on rename and delete.
 *
 * Order is load-bearing rather than cosmetic (#156): the first mode whose window
 * is open wins, which is how overlapping windows resolve the same way every time.
 */
class ModeStore(context: Context, private val pins: PinStore) {

    private val prefs = context.getSharedPreferences("context_modes", Context.MODE_PRIVATE)

    val modes = mutableStateOf(loadModes())

    /** The switch the user made by hand, with the moment it was made (#156). */
    val switch = mutableStateOf(loadSwitch())

    /** Creating asks only for a name; the refusal, if any, is the caller's message. */
    fun create(name: String): ModeNameError? {
        val candidate = sanitize(name)
        validateModeName(candidate, names())?.let { return it }
        persistModes(modes.value + ContextMode(candidate.trim()))
        return null
    }

    fun rename(from: String, to: String): ModeNameError? {
        val trimmed = sanitize(to).trim()
        if (trimmed == from) return null
        validateModeName(trimmed, names() - from)?.let { return it }
        persistModes(modes.value.map { if (it.name == from) it.copy(name = trimmed) else it })
        pins.renameArrangement(from, trimmed)
        switch.value?.let { if (it.mode == from) persistSwitch(it.copy(mode = trimmed)) }
        return null
    }

    /** The record separator cannot live inside a name; GroupStore's move. */
    private fun sanitize(name: String) = name.replace(SEPARATOR, ' ')

    /** Deleting the active mode drops the switch, so Home reverts immediately. */
    fun delete(name: String) {
        persistModes(modes.value.filterNot { it.name == name })
        pins.removeArrangement(name)
        if (switch.value?.mode == name) persistSwitch(null)
    }

    /** A mode's daily window; null takes it away and leaves the mode manual-only. */
    fun setWindow(name: String, window: ScheduleWindow?) {
        persistModes(modes.value.map { if (it.name == name) it.copy(window = window) else it })
    }

    /**
     * Move-up and move-down, which is the whole of reordering: a drag handle
     * would have no keyboard route (ADR 0022) and would owe a `// reachable:`
     * marker under ADR 0024's guard.
     */
    fun move(name: String, by: Int) {
        val current = modes.value
        val from = current.indexOfFirst { it.name == name }
        if (from < 0) return
        val to = (from + by).coerceIn(0, current.lastIndex)
        if (to == from) return
        val reordered = current.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        persistModes(reordered)
    }

    /**
     * The manual switch; null chooses the default arrangement, which is a choice
     * like any other and expires like one (#156). [now] is stamped rather than
     * read here so the expiry is decided against the same clock the resolver uses.
     */
    fun select(name: String?, now: LocalDateTime) = persistSwitch(ManualSwitch(name, now))

    /**
     * Drops a switch that has reached its boundary.
     *
     * Expiry is **written, not merely observed**: left in place, a switch that
     * lapsed against a window would come back to life the moment that window was
     * edited away, since with no window left there is no boundary to have passed
     * — and Home would jump back to an arrangement chosen hours ago.
     */
    fun expireSwitch(now: LocalDateTime) {
        switch.value?.let { if (manualSwitchExpired(it, modes.value, now)) persistSwitch(null) }
    }

    private fun names(): List<String> = modes.value.map { it.name }

    private fun persistModes(value: List<ContextMode>) {
        modes.value = value
        val json = JSONArray()
        value.forEach { mode ->
            val entry = JSONObject().put(MODE_NAME, mode.name)
            mode.window?.let {
                entry.put(MODE_START, it.startMinute).put(MODE_END, it.endMinute)
            }
            json.put(entry)
        }
        prefs.edit { putString(KEY_MODES, json.toString()) }
    }

    private fun persistSwitch(value: ManualSwitch?) {
        switch.value = value
        prefs.edit {
            if (value == null) {
                remove(KEY_CHOICE)
                remove(KEY_SWITCH_AT)
            } else {
                if (value.mode == null) remove(KEY_CHOICE) else putString(KEY_CHOICE, value.mode)
                putString(KEY_SWITCH_AT, value.at.toString())
            }
        }
    }

    // JSON rather than joined names, now that a mode carries a window too —
    // FocusStore's move, and for its reason: a control-character separator is
    // not valid XML 1.0 and SharedPreferences is XML-backed.
    private fun loadModes(): List<ContextMode> {
        val raw = prefs.getString(KEY_MODES, null).orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).map { i ->
                val entry = json.getJSONObject(i)
                ContextMode(
                    name = entry.getString(MODE_NAME),
                    window = if (entry.has(MODE_START)) {
                        ScheduleWindow(entry.getInt(MODE_START), entry.getInt(MODE_END))
                    } else null,
                )
            }
            // Before windows the list was newline-joined names (#155). A device
            // that already has modes keeps them, each without a window.
        }.getOrElse { raw.split(SEPARATOR).filter { it.isNotEmpty() }.map { ContextMode(it) } }
    }

    /**
     * The stamp is what marks a switch as existing, so "chose the default
     * arrangement" is distinguishable from "never chose anything" — the first
     * expires, the second was never in force.
     */
    private fun loadSwitch(): ManualSwitch? {
        val choice = prefs.getString(KEY_CHOICE, null)
        val at = prefs.getString(KEY_SWITCH_AT, null)
            // A choice from before schedules has no stamp. Dated to the far past
            // it keeps holding, because a device that predates windows has none
            // and so has no boundary ahead of it — the #155 behaviour, intact.
            ?: return choice?.let { ManualSwitch(it, LocalDateTime.MIN) }
        return runCatching { ManualSwitch(choice, LocalDateTime.parse(at)) }.getOrNull()
    }

    private companion object {
        const val KEY_MODES = "modes"
        const val KEY_CHOICE = "choice"
        const val KEY_SWITCH_AT = "choiceAt"
        const val SEPARATOR = '\n'

        const val MODE_NAME = "name"
        const val MODE_START = "start"
        const val MODE_END = "end"
    }
}
