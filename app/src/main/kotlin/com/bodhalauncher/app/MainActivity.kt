package com.bodhalauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bodhalauncher.app.session.SessionRuntime
import com.bodhalauncher.engine.SessionPhase
import com.bodhalauncher.engine.Transition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessions = (application as BodhaApp).sessions
        setContent { SessionDebugScreen(sessions) }
    }
}

/** Placeholder Home: live session state until the real Home ships (issue #3). */
@Composable
private fun SessionDebugScreen(sessions: SessionRuntime) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Bodha")
        Text(text = phaseLabel(sessions.phase.value))
        sessions.recentTransitions.takeLast(10).reversed().forEach { transition ->
            Text(text = transitionLabel(transition))
        }
    }
}

private fun phaseLabel(phase: SessionPhase): String = when (phase) {
    SessionPhase.Idle -> "No session"
    is SessionPhase.Active -> "Session #${phase.session.value}"
    is SessionPhase.ProvisionalEnd -> "Session #${phase.session.value} ending…"
}

private fun transitionLabel(transition: Transition): String = when (transition) {
    is Transition.SessionStarted -> "${clock(transition.at)}  started #${transition.session.value}"
    is Transition.SessionResumed -> "${clock(transition.at)}  resumed #${transition.session.value}"
    is Transition.SessionEnded -> "${clock(transition.at)}  ended #${transition.session.value}"
    is Transition.PeekObserved -> "${clock(transition.at)}  peek"
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
private fun clock(at: Instant): String = clockFormat.format(at.atZone(ZoneId.systemDefault()))
