package com.bodhalauncher.app

import android.app.Application
import com.bodhalauncher.app.session.SessionRuntime

class BodhaApp : Application() {

    lateinit var sessions: SessionRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        sessions = SessionRuntime(this).also { it.start() }
    }
}
