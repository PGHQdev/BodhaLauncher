package com.bodhalauncher.app.opencheck

import android.content.Context
import androidx.core.content.edit
import com.bodhalauncher.engine.OpenCheckState
import com.bodhalauncher.engine.TimedSession
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Persists the Open Check engine snapshot locally (ADR 0009): a pending timed
 * session must survive Bodha being killed (#75), and the repeated-opening
 * window and cooldowns ride along.
 */
class OpenCheckStateStore(context: Context) {

    private val prefs = context.getSharedPreferences("open_check_state", Context.MODE_PRIVATE)

    fun save(state: OpenCheckState) {
        val json = JSONObject()
            .putOpt("grantedApp", state.grantedApp)
            .putOpt("grantedUntil", state.grantedUntil?.toEpochMilli())
            .put("recentLaunches", JSONObject(state.recentLaunches.mapValues { (_, launches) ->
                JSONArray(launches.map(Instant::toEpochMilli))
            }))
            .put("cooldownUntil", JSONObject(state.cooldownUntil.mapValues { (_, until) -> until.toEpochMilli() }))
            .putOpt("timedSession", state.timedSession?.let { session ->
                JSONObject()
                    .put("appId", session.appId)
                    .put("startedAt", session.startedAt.toEpochMilli())
                    .put("endsAt", session.endsAt.toEpochMilli())
                    .put("plannedMinutes", session.plannedMinutes)
            })
        prefs.edit { putString(KEY_STATE, json.toString()) }
    }

    fun load(): OpenCheckState {
        val raw = prefs.getString(KEY_STATE, null) ?: return OpenCheckState.Initial
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return OpenCheckState.Initial
        return OpenCheckState(
            grantedApp = json.optString("grantedApp").takeIf { it.isNotEmpty() },
            grantedUntil = json.optLong("grantedUntil", -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
            recentLaunches = json.optJSONObject("recentLaunches")?.let { launches ->
                launches.keys().asSequence().associateWith { id ->
                    val at = launches.getJSONArray(id)
                    (0 until at.length()).map { Instant.ofEpochMilli(at.getLong(it)) }
                }
            }.orEmpty(),
            cooldownUntil = json.optJSONObject("cooldownUntil")?.let { cooldowns ->
                cooldowns.keys().asSequence().associateWith { Instant.ofEpochMilli(cooldowns.getLong(it)) }
            }.orEmpty(),
            timedSession = json.optJSONObject("timedSession")?.let { session ->
                TimedSession(
                    appId = session.getString("appId"),
                    startedAt = Instant.ofEpochMilli(session.getLong("startedAt")),
                    endsAt = Instant.ofEpochMilli(session.getLong("endsAt")),
                    plannedMinutes = session.getLong("plannedMinutes"),
                )
            },
        )
    }

    private companion object {
        const val KEY_STATE = "state"
    }
}
