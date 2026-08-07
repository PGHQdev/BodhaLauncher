package com.bodhalauncher.app

import android.app.Application
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.data.EventLogger
import com.bodhalauncher.app.data.RetentionWorker
import com.bodhalauncher.app.intent.IntentPromptRuntime
import com.bodhalauncher.app.session.SessionRecordLog
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.engine.EventType
import com.bodhalauncher.engine.Transition

class BodhaApp : Application() {

    lateinit var sessions: SessionRuntime
        private set

    lateinit var intentPrompt: IntentPromptRuntime
        private set

    lateinit var events: EventLogger
        private set

    override fun onCreate() {
        super.onCreate()
        events = EventLogger(BodhaDatabase.get(this).eventLog())
        sessions = SessionRuntime(this)
        // The durable record Awareness reads (#171, ADR 0028), fed from the same
        // stream as the event log — restart reconciliation and backfill included.
        val sessionRecords = SessionRecordLog(BodhaDatabase.get(this).sessionRecords())
        sessions.addTransitionListener { transition ->
            sessionRecords.record(transition)
            when (transition) {
                is Transition.SessionStarted -> events.log(EventType.SessionStarted)
                is Transition.SessionEnded -> events.log(EventType.SessionEnded)
                else -> Unit
            }
        }
        // Listens to transitions, so it must be wired before the session stream starts.
        intentPrompt = IntentPromptRuntime(this, sessions).also { it.start() }
        sessions.start()
        RetentionWorker.schedule(this)
    }
}
