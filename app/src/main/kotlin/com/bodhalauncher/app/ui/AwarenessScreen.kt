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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.SessionDetail
import com.bodhalauncher.engine.awarenessIntentWord
import com.bodhalauncher.engine.awarenessSessionLine
import com.bodhalauncher.engine.awarenessTodayLine
import com.bodhalauncher.engine.launchTimeLine
import com.bodhalauncher.engine.sessionDetailNotes

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
 * Each row opens its session (#173), so the first one takes focus on arrival and
 * Escape travels up from it to the root binding (ADR 0022). Back from the
 * Session view returns to root rather than here, which is ADR 0011 read
 * literally: there is no stack to walk back down.
 */
@Composable
fun AwarenessScreen(
    /** Null while the store is still being read — nothing renders, never a stand-in 0. */
    today: AwarenessToday?,
    /** Empty while [today] is null, and empty on a day with no sessions. */
    sessions: List<AwarenessSession>,
    onOpenSession: (AwarenessSession) -> Unit,
    onBack: () -> Unit,
) {
    val line = today?.let(::awarenessTodayLine)
    if (sessions.isEmpty()) {
        AwarenessNote(line, onBack)
        return
    }
    AwarenessList(title = "Awareness", line = line) {
        sessions.forEachIndexed { index, session ->
            SessionRow(
                session = session,
                onOpen = { onOpenSession(session) },
                modifier = if (index == 0) Modifier.focusOnOpen() else Modifier,
            )
        }
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

/** The surface's list shape: a title, a line under it, and the rows beneath. */
@Composable
private fun AwarenessList(
    title: String,
    line: String?,
    /**
     * For a view whose rows are all read rather than activated: the surface
     * itself is then named and focusable, which is the only way Escape has a
     * chain to travel up (ADR 0022). It holds no click, so ADR 0020's floor does
     * not reach it. A view with a real first row focuses that instead.
     */
    focusSelf: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            .then(
                if (focusSelf) {
                    Modifier.semantics { contentDescription = title }.focusOnOpen().focusable()
                } else {
                    Modifier
                }
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = BodhaSpacing.l),
    ) {
        // A surface name is a label, not a spoken line (ADR 0021).
        Text(title, color = colors.ink, style = BodhaType.title)
        line?.let {
            Spacer(Modifier.height(BodhaSpacing.s))
            Text(it, color = colors.inkMuted, style = BodhaType.body)
        }
        Spacer(Modifier.height(BodhaSpacing.l))
        content()
    }
}

/**
 * One session, opened by its row (#173). The span is the row's own line; the
 * classification is its second, in the muted machinery ink every subtitle takes
 * — one ink for the data, and no colour carrying a verdict.
 *
 * The chevron is the vocabulary's navigate marker and is here only because the
 * row now navigates (ADR 0025 rule 3). The row's click merges its two lines, so
 * a reader hears one row rather than two loose strings.
 */
@Composable
fun SessionRow(
    session: AwarenessSession,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        title = awarenessSessionLine(session, LocalBodhaFormats.current.clock),
        subtitle = awarenessIntentWord(session.intentional),
        onClick = onOpen,
        trailing = { TrailingChevron() },
        modifier = modifier,
    )
}

/**
 * Awareness's Session view (#173): one session opened up — what it launched and
 * in what order, what the user stated in it, and what else it held.
 *
 * Back and Escape leave for **root**, not for the Today view: ADR 0011 refuses a
 * stack, and this is the same cost it already accepted for Settings-from-Search.
 * The screen binds neither key; both come from the host's single binding, which
 * the surface's own focus is what makes reachable (ADR 0022) — every row here is
 * read rather than activated, so there is no first row to focus instead.
 */
@Composable
fun SessionDetailScreen(
    /** Null while the launches and the events are still being read — nothing renders. */
    detail: SessionDetail?,
    /** The app's own name, or its id where it is no longer installed to have one. */
    labelFor: (String) -> String,
    iconFor: (String) -> ImageBitmap?,
) {
    if (detail == null) return
    val colors = LocalBodhaColors.current
    val clock = LocalBodhaFormats.current.clock
    AwarenessList(
        title = "Session",
        line = awarenessSessionLine(detail.session, clock),
        focusSelf = true,
    ) {
        Text(
            awarenessIntentWord(detail.session.intentional),
            color = colors.inkMuted,
            style = BodhaType.caption,
        )
        // What was stated, in the user's own words — the sans, as the Focus
        // session's label is (ADR 0021), because Bodha did not write it.
        detail.statement?.let { statement ->
            Spacer(Modifier.height(BodhaSpacing.m))
            Text(statement, color = colors.ink, style = BodhaType.body)
        }
        if (detail.launches.isNotEmpty()) {
            SectionOverline("Opened")
            detail.launches.forEach { launch ->
                LaunchRow(
                    label = labelFor(launch.appId),
                    time = launchTimeLine(launch, clock),
                    icon = iconFor(launch.appId),
                )
            }
        }
        val notes = sessionDetailNotes(detail)
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(BodhaSpacing.l))
            notes.forEach { note ->
                Text(note, color = colors.inkMuted, style = BodhaType.body)
            }
        }
    }
}

/**
 * One launch inside a session (#173): the app's mark and name, and when it was
 * opened. Read rather than activated — the record is history, and a row that
 * relaunched from it would be a different feature — so it publishes no click and
 * carries no chevron, and is named as one node rather than two loose strings.
 */
@Composable
fun LaunchRow(
    label: String,
    time: String,
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    ListRow(
        title = label,
        subtitle = time,
        onClick = null,
        leading = icon?.let { { AppMark(it) } },
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label. $time."
        },
    )
}
