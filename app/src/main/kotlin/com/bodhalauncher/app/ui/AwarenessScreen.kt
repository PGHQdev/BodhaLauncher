package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.awarenessIntentWord
import com.bodhalauncher.engine.awarenessSessionLine
import com.bodhalauncher.engine.awarenessTodayLine

/**
 * Awareness's Today view (#171, #172): the day's count, then the day's sessions
 * in time order — when each started, how long it ran, and whether the user
 * stated an intent in it.
 *
 * Drawn as a scrolling list of hairline rows, the only shape ADR 0025's
 * vocabulary yields; a plotted or time-scaled strip would need a sixth rule. The
 * classification is a **word** on the row, because ADR 0013 forbids valence
 * colour and ADR 0025 has already spent tinted fill on the current thing.
 *
 * The rows carry no action: there is nothing to open until #173, and a named
 * action that only reports its own absence is worse than none — the same reason
 * Home's pending lock stays unannounced. So the surface takes focus itself,
 * which is what gives Escape a chain to reach the root binding along (ADR 0022).
 */
@Composable
fun AwarenessScreen(
    /** Null while the store is still being read — nothing renders, never a stand-in 0. */
    today: AwarenessToday?,
    /** Empty while [today] is null, and empty on a day with no sessions. */
    sessions: List<AwarenessSession>,
    onBack: () -> Unit,
) {
    val line = today?.let(::awarenessTodayLine)
    if (sessions.isEmpty()) {
        AwarenessNote(line, onBack)
        return
    }
    AwarenessList(line) {
        sessions.forEach { session -> SessionRow(session) }
    }
}

/**
 * A named absence filling the surface, tappable to leave — the pattern the
 * placeholder and the inbox's empty states already use. Never a zero.
 */
@Composable
private fun AwarenessNote(line: String?, onBack: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .semantics {
                contentDescription = listOfNotNull("Awareness.", line?.plus("."), "Tap to go back.")
                    .joinToString(" ")
            }
            .focusOnOpen()
            .clickable(onClick = onBack),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Awareness", color = colors.ink, style = BodhaType.title)
        line?.let {
            Spacer(Modifier.height(BodhaSpacing.m))
            // A count is data, so it speaks in the operational voice (ADR 0021)
            // with one ink and no direction (ADR 0013).
            Text(it, color = colors.inkMuted, style = BodhaType.body)
        }
    }
}

/** The surface's list shape: the name, the day's count, and the rows beneath. */
@Composable
private fun AwarenessList(line: String?, content: @Composable () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            // Named and focusable but not actionable: it holds no click, so ADR
            // 0020's floor does not reach it, and focus on arrival is only here
            // to give Escape somewhere to start from.
            .semantics { contentDescription = "Awareness" }
            .focusOnOpen()
            .focusable()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = BodhaSpacing.l),
    ) {
        // A surface name is a label, not a spoken line (ADR 0021).
        Text("Awareness", color = colors.ink, style = BodhaType.title)
        line?.let {
            Spacer(Modifier.height(BodhaSpacing.s))
            Text(it, color = colors.inkMuted, style = BodhaType.body)
        }
        Spacer(Modifier.height(BodhaSpacing.l))
        content()
    }
}

/**
 * One session, read rather than activated (#172). The span is the row's own
 * line; the classification is its second, in the muted machinery ink every
 * subtitle takes — one ink for the data, and no colour carrying a verdict.
 *
 * The two lines are merged into **one named node**, because a row that publishes
 * no click does not merge on its own, and ADR 0020 asks a row to be named rather
 * than to arrive as two loose strings.
 */
@Composable
fun SessionRow(session: AwarenessSession) {
    val span = awarenessSessionLine(session, LocalBodhaFormats.current.clock)
    val word = awarenessIntentWord(session.intentional)
    ListRow(
        title = span,
        subtitle = word,
        onClick = null,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$span. $word."
        },
    )
}
