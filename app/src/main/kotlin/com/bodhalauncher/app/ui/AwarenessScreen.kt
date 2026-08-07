package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.AWARENESS_EXCLUDED
import com.bodhalauncher.engine.AWARENESS_INCLUDE
import com.bodhalauncher.engine.AWARENESS_TURN_ON_USAGE
import com.bodhalauncher.engine.AppOpens
import com.bodhalauncher.engine.AwarenessDayFigures
import com.bodhalauncher.engine.AwarenessDuration
import com.bodhalauncher.engine.AwarenessSession
import com.bodhalauncher.engine.AwarenessToday
import com.bodhalauncher.engine.AwarenessUsage
import com.bodhalauncher.engine.AwarenessView
import com.bodhalauncher.engine.AwarenessWeek
import com.bodhalauncher.engine.Exclusions
import com.bodhalauncher.engine.ProBoundary
import com.bodhalauncher.engine.SessionDetail
import com.bodhalauncher.engine.SessionRecord
import com.bodhalauncher.engine.appDayLine
import com.bodhalauncher.engine.appOpensLine
import com.bodhalauncher.engine.appOpensSourceLine
import com.bodhalauncher.engine.awarenessDayFiguresLine
import com.bodhalauncher.engine.awarenessDayLine
import com.bodhalauncher.engine.awarenessForegroundLine
import com.bodhalauncher.engine.awarenessIntentWord
import com.bodhalauncher.engine.awarenessSessionLine
import com.bodhalauncher.engine.awarenessWeekRateLine
import com.bodhalauncher.engine.exclusionSessionLine
import com.bodhalauncher.engine.exclusionsLine
import com.bodhalauncher.engine.formatDate
import com.bodhalauncher.engine.launchTimeLine
import com.bodhalauncher.engine.sessionDetailNotes
import java.time.LocalDate

/**
 * Awareness's Today view (#171, #172, #176): one day's count, then that day's
 * sessions in time order — when each started, how long it ran, and whether the
 * user stated an intent in it.
 *
 * Drawn as a scrolling list of hairline rows, the only shape ADR 0025's
 * vocabulary yields; a plotted or time-scaled strip would need a sixth rule. The
 * classification is a **word** on the row, because ADR 0013 forbids valence
 * colour and ADR 0025 has already spent tinted fill on the current thing.
 *
 * Each row opens its session (#173). Back from the Session view returns to root
 * rather than here, which is ADR 0011 read literally: there is no stack to walk
 * back down. The same is true of the day this view is showing — a day picked on
 * the Week view opens here, and Escape from here leaves for root rather than
 * returning to the Week.
 *
 * A row's own actions live where every other row's do (#178): long-press on
 * touch, the Actions node on Right, Menu as an accelerator — all of it
 * [ListRow]'s, none of it rebuilt here. The exclusions list hangs off the foot of
 * this view rather than off Settings, because the undo belongs beside the thing
 * it undoes and nothing in Settings earns it (ADR 0019).
 */
@Composable
fun AwarenessScreen(
    /** Null while the store is still being read — nothing renders, never a stand-in 0. */
    today: AwarenessToday?,
    /** Empty while [today] is null, and empty on a day with no sessions. */
    sessions: List<AwarenessSession>,
    /** Which day is being shown: the live one, or one picked from the Week (#176). */
    day: LocalDate,
    isToday: Boolean,
    /**
     * What is currently taken out of every view (#178). The rows above were
     * filtered before they arrived; this is here only so the foot of the list can
     * say what is missing and offer the way back.
     */
    exclusions: Exclusions,
    onPickView: (AwarenessView) -> Unit,
    onOpenSession: (AwarenessSession) -> Unit,
    /** The row's own actions, in the shared affordance (#178, ADR 0022, ADR 0023). */
    onSessionActions: (AwarenessSession) -> Unit,
    onOpenExclusions: () -> Unit,
    /** Non-null only where the entitlement window withheld one of this day's records (#177). */
    boundary: ProBoundary? = null,
    onBoundary: () -> Unit = {},
    onBack: () -> Unit,
) {
    if (today == null) {
        AwarenessNote(onBack)
        return
    }
    val formats = LocalBodhaFormats.current
    AwarenessList(
        title = "Awareness",
        line = awarenessDayLine(today, day, isToday, formats.date),
        switch = {
            AwarenessViewSwitch(
                current = AwarenessView.Today,
                onPick = onPickView,
                // Only where there is no row at all to take it: a day with
                // sessions arrives on the first of them, and a quiet day with
                // something excluded arrives on the Excluded row.
                arrival = if (sessions.isEmpty() && exclusions.isEmpty) {
                    Modifier.focusOnOpen()
                } else {
                    Modifier
                },
            )
        },
        boundary = boundary,
        onBoundary = onBoundary,
    ) {
        sessions.forEachIndexed { index, session ->
            SessionRow(
                session = session,
                onOpen = { onOpenSession(session) },
                onActions = { onSessionActions(session) },
                modifier = if (index == 0) Modifier.focusOnOpen() else Modifier,
            )
        }
        // Only where there is something in it. A permanent row naming an empty
        // list would be a control for undoing nothing, sitting under every day
        // the reader never excluded anything from.
        if (!exclusions.isEmpty) {
            ListRow(
                title = AWARENESS_EXCLUDED,
                subtitle = exclusionsLine(exclusions),
                onClick = onOpenExclusions,
                trailing = { TrailingChevron() },
                modifier = if (sessions.isEmpty()) Modifier.focusOnOpen() else Modifier,
            )
        }
    }
}

/**
 * A named absence filling the surface, tappable to leave — the pattern the
 * placeholder and the inbox's empty states already use.
 *
 * **A read that has not landed, and nothing else** (#176). A quiet day used to
 * come here too, and once the surface carries a switch that would make the Week
 * unreachable from exactly the day a reader is most likely to go looking for it.
 * A day with nothing on it names its absence on the list's own line instead.
 */
@Composable
private fun AwarenessNote(onBack: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .semantics { contentDescription = "Awareness. Tap to go back." }
            .focusOnOpen()
            .clickable(onClick = onBack),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Awareness", color = colors.ink, style = BodhaType.title)
    }
}

/**
 * The surface's list shape: a title, the view switch, a line under it, and the
 * rows beneath.
 *
 * That order is the sentence the screen makes: the title names the surface, the
 * pills choose the view, and the line describes the view that was chosen.
 *
 * **Where arrival focus lands, stated once for every Awareness view** (#176):
 * the first rendered **row**, via `Modifier.focusOnOpen()` at the call site;
 * where a view renders no rows, the current switch pill, via
 * [AwarenessViewSwitch]'s `arrival`; and where there is no switch at all, the
 * container itself, via [focusSelf]. Never nowhere — a key event travels up from
 * the focused node, so a surface that focuses nothing has no Escape until the
 * reader presses Tab (ADR 0022's build amendment).
 */
@Composable
private fun AwarenessList(
    title: String,
    line: String?,
    /**
     * For a view that leads with read content, or whose rows may be absent
     * altogether: the surface itself is then named and focusable, which is the
     * only way Escape has a chain to travel up (ADR 0022). It holds no click, so
     * ADR 0020's floor does not reach it. A view with a real first row focuses
     * that instead.
     *
     * The second half of that is what a failed read needs (#174): a view whose
     * rows never arrived would otherwise publish nothing to focus, and Escape
     * would have no chain from a screen the reader is nonetheless looking at.
     */
    focusSelf: Boolean = false,
    /**
     * The Today/Week switch (#176). Each of the two views that carry one decides
     * for itself whether a row takes arrival first; the drill-downs pass none.
     */
    switch: (@Composable () -> Unit)? = null,
    /**
     * The entitlement window's terminus (#177), non-null **only where the window
     * actually withheld a record**. A view that stopped for want of records has
     * nothing true to say about Pro, and a permanent line naming what Pro costs
     * on a screen that lost nothing is an upsell — which [ProBoundaryDialog]'s
     * own copy refuses to be.
     */
    boundary: ProBoundary? = null,
    onBoundary: () -> Unit = {},
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
        if (switch != null) {
            Spacer(Modifier.height(BodhaSpacing.m))
            switch()
        }
        line?.let {
            Spacer(Modifier.height(BodhaSpacing.s))
            Text(it, color = colors.inkMuted, style = BodhaType.body)
        }
        Spacer(Modifier.height(BodhaSpacing.l))
        content()
        // Once, at the edge of the window, beneath everything it renders — never
        // repeated per row, because the reader crossed the edge once.
        boundary?.let {
            Spacer(Modifier.height(BodhaSpacing.l))
            ProBoundaryTerminus(explanation = it.explanation, onOpen = onBoundary)
        }
    }
}

/**
 * The gate's own sentence, at the edge of the window (#177, ADR 0005).
 *
 * **What renders here is [ProBoundary.explanation] and nothing Awareness wrote
 * itself.** An earlier pass put a machinery line here — "Seven days render free"
 * — and kept the authored sentence for the dialog behind it, which made one fact
 * two sentences and left the reader who never pressed the row with only the
 * summary. One authored fact, stated once, where the list stops.
 *
 * Which is why it is set in [BodhaType.voicePassage] rather than in a
 * [CardRow]'s title: the gate's copy is text Bodha wrote and means, and
 * CONTEXT.md's **Voice** entry settles the face by authorship rather than by
 * which container the string landed in. That is the one reason this is a call
 * site built from [BodhaCard] instead of the row component — every other part of
 * it, the floor, the ring and the click, is what `CardRow` would have given.
 *
 * A card rather than a hairline row, because it is the list's terminus rather
 * than one more record in it, and no chevron, because it opens a dialog rather
 * than navigating (ADR 0025 rules 1 and 3). Plain fill for the same reason every
 * other way-in on this surface is plain: Awareness's primary action is reading
 * the record, and solid accent here would rank a boundary above it.
 *
 * It stays a control because the dialog behind it is the shared Pro site every
 * gate in the app opens (`LibrarySurface` opens the same one) and is where #22's
 * purchase lands. Today that dialog restates this sentence and adds nothing,
 * which is a redundancy worth naming rather than hiding.
 */
@Composable
private fun ProBoundaryTerminus(explanation: String, onOpen: () -> Unit) {
    val colors = LocalBodhaColors.current
    var focused by remember { mutableStateOf(false) }
    BodhaCard(focused = focusRingShown(focused)) {
        Text(
            text = explanation,
            color = colors.ink,
            style = BodhaType.voicePassage,
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onOpen)
                .padding(horizontal = BodhaSpacing.m, vertical = BodhaSpacing.s),
        )
    }
}

/**
 * Awareness's Today/Week switch (#176): two bare pills, the current one tinted.
 *
 * It lives here beside its only caller rather than in the shared vocabulary,
 * which is [LayoutSwitcher]'s precedent — a set of pills over one screen's own
 * options is a call site, not a component, until a second screen wants it. It is
 * not a `ChoiceRow` either: that is a *setting* with a title above its answers,
 * and this is the view you are looking at.
 *
 * The pills carry `selected` where [LayoutSwitcher]'s pass none, and that
 * difference is deliberate. Fill is a colour, a colour is the one channel a
 * screen reader has no access to, and which view is current is the whole
 * meaning of this control — so the state goes in the semantics as well as in
 * the tint.
 *
 * Scrolls rather than squeezes, for `LayoutSwitcher`'s stated reason: at 2× text
 * the 48dp floor wins and the row gives way instead of the targets.
 */
@Composable
fun AwarenessViewSwitch(
    current: AwarenessView,
    onPick: (AwarenessView) -> Unit,
    modifier: Modifier = Modifier,
    /** Where arrival lands when the view beneath renders no row to take it. */
    arrival: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BodhaSpacing.s),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            // Names the pair as one choice, so a reader hears where the current
            // pill's "selected" belongs.
            .selectableGroup(),
    ) {
        AwarenessView.entries.forEach { view ->
            BodhaPill(
                label = view.label,
                onClick = { onPick(view) },
                // Rule 2's tint means *the current thing*, which is exactly what
                // the view being looked at is (ADR 0025).
                emphasis = if (view == current) Emphasis.Tinted else Emphasis.Plain,
                selected = view == current,
                modifier = if (view == current) arrival else Modifier,
            )
        }
    }
}

/**
 * One day of the Week view (#176), opening that day in the Today view.
 *
 * The date is the row's title and the figures are its line — no bar, no share of
 * a total, nothing sized by a number. Every day draws the same row whatever it
 * holds, which is what keeps seven days side by side from becoming seven days
 * ranked (ADR 0013).
 *
 * The chevron is here because the row navigates, and for no other reason
 * (ADR 0025 rule 3).
 */
@Composable
fun WeekDayRow(
    figures: AwarenessDayFigures,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        title = formatDate(figures.day, LocalBodhaFormats.current.date),
        subtitle = awarenessDayFiguresLine(figures),
        onClick = onOpen,
        trailing = { TrailingChevron() },
        modifier = modifier,
    )
}

/**
 * Awareness's Week view (#176, ADR 0013): the last seven days, oldest first,
 * each with what it held — and the rate the period ran at, with the period
 * before it beside it as a bare second number.
 *
 * The same rows as Today, one level up: a date and its figures, and picking one
 * opens that day. Nothing here is plotted, ordered by a figure, coloured by a
 * verdict or marked with a direction, which is ADR 0013's four prohibitions as
 * a shape rather than as a rule someone remembers.
 *
 * **The usage state is stated once, above the rows, and never per row.** Every
 * duration on this surface degrades for the same reason at the same moment, so
 * saying it seven times would be seven copies of one sentence — and the reader
 * would have to read them all to learn it was the same one.
 */
@Composable
fun AwarenessWeekScreen(
    /** Null while the stores are still being read — the shell renders, never a stand-in 0. */
    week: AwarenessWeek?,
    usage: AwarenessUsage,
    onPickView: (AwarenessView) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    /** Enters the one capability-education flow (#157), never a second copy of it. */
    onTurnOnUsage: () -> Unit,
    /**
     * Non-null only where the window dropped one of the seven days (#177). A
     * rolling seven sits inside the free window by construction, so on today's
     * caps this is always null — the parameter is here because the cap is
     * ADR 0005's to change and a Week that silently shortened itself would be
     * the one clamped view with nothing to explain it.
     */
    boundary: ProBoundary? = null,
    onBoundary: () -> Unit = {},
) {
    val colors = LocalBodhaColors.current
    AwarenessList(
        title = "Awareness",
        line = week?.let(::awarenessWeekRateLine),
        switch = {
            AwarenessViewSwitch(
                current = AwarenessView.Week,
                onPick = onPickView,
                arrival = if (week == null) Modifier.focusOnOpen() else Modifier,
            )
        },
        boundary = boundary,
        onBoundary = onBoundary,
    ) {
        if (week == null) return@AwarenessList
        // Where there is a rate it is the line under the title, and this says
        // nothing. Where there is not — no access, or a read that came back
        // empty-handed with access held — the absence is named here rather than
        // left as a line that quietly did not render.
        if (week.rate !is AwarenessDuration.Span) {
            val foreground = awarenessForegroundLine(week.rate, usage)
            if (usage is AwarenessUsage.Ungranted && usage.offersTurnOn) {
                // Plain emphasis and one ink: this is a way in, not the screen's
                // primary action, and a solid fill here would rank it above the
                // record it sits over (ADR 0025, ADR 0013).
                CardRow(
                    title = foreground,
                    subtitle = AWARENESS_TURN_ON_USAGE,
                    onClick = onTurnOnUsage,
                )
            } else {
                Text(foreground, color = colors.inkMuted, style = BodhaType.body)
            }
            Spacer(Modifier.height(BodhaSpacing.l))
        }
        week.days.forEachIndexed { index, figures ->
            WeekDayRow(
                figures = figures,
                onOpen = { onOpenDay(figures.day) },
                modifier = if (index == 0) Modifier.focusOnOpen() else Modifier,
            )
        }
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
    /**
     * The session's own actions (#178), handed to [ListRow] and to nothing else:
     * the long-press, the Right-revealed Actions node, the Menu accelerator, the
     * retiring hint and the focus ring are all already there (ADR 0022, ADR 0023,
     * #131). Null is a row with no actions — the gallery's specimens — and costs
     * neither the node nor the keys.
     */
    onActions: (() -> Unit)? = null,
) {
    ListRow(
        title = awarenessSessionLine(session, LocalBodhaFormats.current.clock),
        subtitle = awarenessIntentWord(session.intentional),
        onClick = onOpen,
        onLongClick = onActions,
        trailing = { TrailingChevron() },
        modifier = modifier,
    )
}

/**
 * The exclusions list (#178): everything currently taken out of Awareness, and
 * one press to put any of it back.
 *
 * It lives here rather than in Settings, and that is the ticket's own decision
 * rather than an accident of where the code went. Nothing in Settings earns it
 * under ADR 0019's three clauses, and an undo belongs beside the thing it undoes
 * — the reader excluded an app while reading Awareness, and this is where they
 * will look for it.
 *
 * **Every row acts in place, so no row wears a chevron** (ADR 0025 rule 3):
 * pressing one includes the item again and the row leaves the list, which is the
 * whole interaction. The subtitle names the outcome, because a row whose title is
 * the thing and whose press is the opposite of the thing needs to say which it
 * does.
 *
 * A session id whose record retention has taken is simply absent: the caller
 * hands over what it found, and prunes the ids nothing answered to. That is what
 * keeps this list from holding a row that cannot be undone from.
 */
@Composable
fun ExclusionsScreen(
    /** The excluded app ids, in the order the caller settled on. */
    apps: List<String>,
    /** The excluded sessions' records — empty while the read is in flight. */
    sessions: List<SessionRecord>,
    /**
     * What is excluded, for the line under the title. Read from the store rather
     * than folded from the two lists above, so a read still in flight draws an
     * empty list under a true count rather than under "nothing is excluded".
     */
    exclusions: Exclusions,
    /** The app's own name, or its id where it is no longer installed to have one. */
    labelFor: (String) -> String,
    iconFor: (String) -> ImageBitmap?,
    onIncludeApp: (String) -> Unit,
    onIncludeSession: (Long) -> Unit,
) {
    val formats = LocalBodhaFormats.current
    val empty = apps.isEmpty() && sessions.isEmpty()
    AwarenessList(
        title = AWARENESS_EXCLUDED,
        line = exclusionsLine(exclusions),
        // Only where there is no row to take arrival: this screen exists for the
        // populated case, and that case focuses its first row (ADR 0022).
        focusSelf = empty,
    ) {
        apps.forEachIndexed { index, id ->
            ListRow(
                title = labelFor(id),
                subtitle = AWARENESS_INCLUDE,
                onClick = { onIncludeApp(id) },
                leading = iconFor(id)?.let { { AppMark(it) } },
                modifier = if (index == 0) Modifier.focusOnOpen() else Modifier,
            )
        }
        sessions.forEachIndexed { index, record ->
            ListRow(
                title = exclusionSessionLine(record, formats.clock, formats.date),
                subtitle = AWARENESS_INCLUDE,
                onClick = { onIncludeSession(record.id) },
                modifier = if (index == 0 && apps.isEmpty()) Modifier.focusOnOpen() else Modifier,
            )
        }
    }
}

/**
 * Awareness's Session view (#173): one session opened up — what it launched and
 * in what order, what the user stated in it, and what else it held.
 *
 * Back and Escape leave for **root**, not for the Today view: ADR 0011 refuses a
 * stack, and this is the same cost it already accepted for Settings-from-Search.
 * The screen binds neither key; both come from the host's single binding, which
 * the surface's own focus is what makes reachable (ADR 0022).
 *
 * The surface keeps focus itself rather than handing it to the first launch row
 * (#174): a session that opened nothing has no rows at all, and conditional
 * arrival focus would leave exactly that case with no chain for Escape to travel
 * up.
 *
 * It is the one Awareness view that carries no entitlement boundary (#177), and
 * that is a fact about the record rather than an omission: a session is reached
 * from a list the window has already clamped, and a launch is never older than
 * the session it was made in — so nothing here can fall outside a window the row
 * that opened it fell inside.
 */
@Composable
fun SessionDetailScreen(
    /** Null while the launches and the events are still being read — nothing renders. */
    detail: SessionDetail?,
    /** The app's own name, or its id where it is no longer installed to have one. */
    labelFor: (String) -> String,
    iconFor: (String) -> ImageBitmap?,
    /**
     * Opens the app's own view (#174), by the id the record holds. The id rather
     * than the label, because the label is what a catalog happened to answer and
     * the id is what every store is keyed by.
     */
    onOpenApp: (String) -> Unit,
    /** The app's own actions (#178), by the same id and through the same affordance. */
    onAppActions: (String) -> Unit,
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
                    onOpen = { onOpenApp(launch.appId) },
                    onActions = { onAppActions(launch.appId) },
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
 * opened. The row opens the app's own view (#174) — still not a way to relaunch,
 * which remains a different feature, but a way further into the record.
 *
 * The chevron is here only because the row now navigates (ADR 0025 rule 3), and
 * the hand-written merged description went the moment the row gained a click:
 * `Modifier.clickable` merges the row into one named node on its own, which is
 * what [SessionRow] has always relied on.
 */
@Composable
fun LaunchRow(
    label: String,
    time: String,
    icon: ImageBitmap?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** The app's own actions (#178), handed to [ListRow] and to nothing else. */
    onActions: (() -> Unit)? = null,
) {
    ListRow(
        title = label,
        subtitle = time,
        onClick = onOpen,
        onLongClick = onActions,
        leading = icon?.let { { AppMark(it) } },
        trailing = { TrailingChevron() },
        modifier = modifier,
    )
}

/**
 * Awareness's App view (#174, #175): one app — how long it was in front, when it
 * was opened, how often, and how many sessions those opens fell in.
 *
 * The launch log is the spine and needs no permission (ADR 0013). Usage access
 * fills in what Bodha cannot see on its own — the span, and the opens that came
 * from a notification, from recents, from another app — and where it is absent
 * the view **says so in a sentence** and draws nothing in its place. Not a
 * blank, and never a 0: a zero in a duration field is a claim about the reader's
 * behaviour built out of a gap in Bodha's reach.
 *
 * The opens sit under day headings, newest day first, because a bare list of
 * times a fortnight long tells a reader nothing about when. Each open is a row
 * **read rather than activated**: it is history, it navigates nowhere, and so it
 * carries no chevron and publishes no actionable node (ADR 0025 rule 3).
 *
 * The one thing on this screen that can be acted on is the route into the usage
 * education, and it appears only where offering it is honest — never granted,
 * and never yet declined. A revocation and a past refusal both rest on the
 * stated absence, which is what "a past refusal degrades quietly" means (#175,
 * ADR 0017). It is a card row without a chevron: it opens a sheet, and rule 3
 * says a chevron means navigation and nothing else.
 *
 * Back and Escape leave for **root**, the same as the Session view they were
 * reached through, and for the same reason: ADR 0011 refuses a stack, so a
 * drill-down two rows deep is still not a stack frame. The accepted cost is that
 * Escape from here loses the session the reader was in — the trade ADR 0011
 * already records for losing a Search query.
 */
@Composable
fun AppOpensScreen(
    /**
     * Null while the launch log is still being read, and null if the read
     * failed. The view is a named, focusable shell either way — a screen with
     * nothing to focus is a screen Escape cannot leave (ADR 0022).
     */
    view: AppOpens?,
    /** The title before a read lands: the app's name, or its id where it has none left. */
    label: String,
    /**
     * The surface's capability state, which words the degraded sentences the
     * figure alone cannot: "never granted" and "turned off ten minutes ago" are
     * the same absence to [AppOpens.foreground] and different things to say.
     */
    usage: AwarenessUsage,
    /** Enters the one capability-education flow (#157), never a second copy of it. */
    onTurnOn: () -> Unit,
    /**
     * Non-null only where the window withheld an open (#177). The launch log is
     * read whole and clamped here, which is what lets this view state a boundary
     * at all — a query narrowed to seven days would have nothing to compare
     * against and would never speak.
     */
    boundary: ProBoundary? = null,
    onBoundary: () -> Unit = {},
) {
    val colors = LocalBodhaColors.current
    val formats = LocalBodhaFormats.current
    if (view == null) {
        AwarenessList(title = label, line = null, focusSelf = true) {}
        return
    }
    AwarenessList(
        title = view.name,
        line = appOpensLine(view),
        focusSelf = true,
        boundary = boundary,
        onBoundary = onBoundary,
    ) {
        val foreground = awarenessForegroundLine(view.foreground, usage)
        if (usage is AwarenessUsage.Ungranted && usage.offersTurnOn) {
            // Plain emphasis and one ink: this is a way in, not the screen's
            // primary action, and a solid fill here would rank it above the
            // record it sits over (ADR 0025, ADR 0013).
            CardRow(title = foreground, subtitle = AWARENESS_TURN_ON_USAGE, onClick = onTurnOn)
        } else {
            Text(foreground, color = colors.inkMuted, style = BodhaType.body)
        }
        Spacer(Modifier.height(BodhaSpacing.l))
        view.days.forEach { day ->
            SectionOverline(appDayLine(day, formats.date))
            day.opens.forEach { open ->
                ListRow(title = launchTimeLine(open, formats.clock), onClick = null)
            }
        }
        // What the rows are missing, said once at the foot rather than marked on
        // every row: no row here claims which source it came from, because the
        // reader opened the app rather than the plumbing.
        appOpensSourceLine(usage)?.let { source ->
            Spacer(Modifier.height(BodhaSpacing.l))
            Text(source, color = colors.inkMuted, style = BodhaType.body)
        }
    }
}
