package com.bodhalauncher.app

import android.app.Application
import com.bodhalauncher.app.intent.IntentPromptRuntime
import com.bodhalauncher.app.session.SessionRuntime

class BodhaApp : Application() {

    lateinit var sessions: SessionRuntime
        private set

    lateinit var intentPrompt: IntentPromptRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        sessions = SessionRuntime(this)
        // Listens to transitions, so it must be wired before the session stream starts.
        intentPrompt = IntentPromptRuntime(this, sessions).also { it.start() }
        sessions.start()
    }
}
