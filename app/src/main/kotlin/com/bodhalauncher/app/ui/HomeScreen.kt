package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.HomeState
import com.bodhalauncher.engine.MAX_ACTIONS
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ADR 0010 "Centered Axis": centered serif clock and intention (the voice), sans
 * operational text below (the machinery). Renders [HomeState] and nothing else —
 * no logic beyond mapping state to layout.
 *
 * The actions are **cards**, not the hairline list ADR 0010 described: ADR 0025
 * names "Home's actions" as the card idiom by name and reserves the hairline row
 * for lists that scroll, and it is the later decision on that question. The
 * centered axis, the serif voice and the sans machinery are untouched by it.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit = {},
    onActionLongPress: (HomeAction) -> Unit = {},
    /** Shown while there is room for another pin; opens the app picker. */
    onAddAction: (() -> Unit)? = null,
    /** The intention is read-only here; tapping it opens Today, its editor (#158). */
    onOpenToday: (() -> Unit)? = null,
    /** Opens the mode selector; the label shows only while a mode is active (#155). */
    onContextLabelTap: () -> Unit = {},
    iconFor: (HomeAction) -> ImageBitmap? = { null },
    /** Changes when any package changes, so cached icons refresh with their apps. */
    iconKey: Any = Unit,
    gestures: HomeGestures? = null,
    onSearch: () -> Unit = {},
) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .then(if (gestures != null) Modifier.homeGestures(gestures) else Modifier)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // First in the traversal because that is what a skip link is: a docked
        // user's route off Home arrives before the content, not after the pins.
        if (gestures != null) HomeGestureAffordances(gestures)
        Spacer(Modifier.height(48.dp))
        Clock()
        state.contextLabel?.let {
            Spacer(Modifier.height(12.dp))
            // The mode's name and nothing else — a manual switch and a scheduled
            // one look the same. The pill sizes to its text (ADR 0025 rule 4).
            BodhaPill(label = it, onClick = onContextLabelTap)
        }
        // Read-only in the voice face; the empty state lives on Today (#158).
        state.dailyIntention?.let { intention ->
            Spacer(Modifier.height(36.dp))
            IntentionCard(text = intention, muted = false, onEdit = onOpenToday)
        }
        Spacer(Modifier.height(48.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BodhaSpacing.s),
        ) {
            state.actions.forEach { action ->
                val icon = remember(action.id, iconKey) { iconFor(action) }
                CardRow(
                    title = action.label,
                    onClick = { onAction(action) },
                    onLongClick = { onActionLongPress(action) },
                    // The app's own mark, so bare rather than chipped (rule 5).
                    leading = if (icon != null) ({ AppMark(icon) }) else null,
                )
            }
            if (onAddAction != null && state.actions.size < MAX_ACTIONS) {
                CardRow(
                    // Empty, the row is the empty state (#137): it invites the
                    // first pin and names where the rest are.
                    title = if (state.actions.isEmpty()) "Pin your first app" else "Add a pin",
                    subtitle = if (state.actions.isEmpty()) "The rest live in the App Library" else null,
                    onClick = onAddAction,
                    // Bodha's own glyph, so it takes the chip (rule 5).
                    leading = { IconChip { Text("＋", style = BodhaType.body, color = colors.inkMuted) } },
                )
            }
        }
        state.inboxDigest?.let {
            Spacer(Modifier.height(28.dp))
            // Summarising, which is one of the two things a tint may mean (rule 2).
            BodhaCard(modifier = Modifier.fillMaxWidth(), emphasis = Emphasis.Tinted) {
                Text(
                    text = it,
                    color = colors.inkMuted,
                    style = BodhaType.label,
                    modifier = Modifier.padding(BodhaSpacing.m),
                )
            }
        }
        if (!state.homeRoleHeld) {
            Spacer(Modifier.height(20.dp))
            // One factual line naming the declined state (#136, ADR 0018): a
            // word, not a control — it carries a name a reader reads and owes
            // no keyboard route and no touch target. The only route back to the
            // role is the future Settings row.
            Text(
                text = "Bodha is an app you open — nothing meets you at unlock",
                color = colors.inkMuted,
                style = BodhaType.overline,
                textAlign = TextAlign.Center,
            )
        }
        if (state.focusActive) {
            Spacer(Modifier.height(20.dp))
            // A word, not a dot: a status indicator is neither of rule 2's two
            // meanings, so it cannot hold a fill, and a coloured dot said nothing
            // to a reader anyway (#26).
            Text(text = "Focus session", color = colors.inkMuted, style = BodhaType.overline)
        }
        Spacer(Modifier.weight(1f))
        BodhaField(
            // Clickable also consumes taps, so double-tap here never falls through
            // to lock; it is also what names the field, from the label inside it.
            modifier = Modifier.clickable(onClick = onSearch),
        ) {
            Text(
                text = "Search",
                color = colors.inkMuted,
                style = BodhaType.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Clock() {
    val colors = LocalBodhaColors.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Duration.ofSeconds(60L - now.second).toMillis())
            now = LocalDateTime.now()
        }
    }
    Text(
        text = now.format(timeFormat),
        color = LocalBodhaColors.current.ink,
        style = BodhaType.voiceClock,
    )
    Text(
        text = now.format(dateFormat),
        color = colors.inkMuted,
        style = BodhaType.label,
    )
}

/**
 * The daily intention in the tinted card rule 2 gives *the current thing*, still
 * the centered serif italic line ADR 0010 draws — the container changed, the
 * voice did not.
 *
 * The ring is passed to [BodhaCard] rather than sensed by it because the
 * focusable node is the line's own click chain, one level in.
 */
@Composable
internal fun IntentionCard(text: String, muted: Boolean, onEdit: (() -> Unit)?) {
    val colors = LocalBodhaColors.current
    var focused by remember { mutableStateOf(false) }
    BodhaCard(
        modifier = Modifier.fillMaxWidth(),
        emphasis = Emphasis.Tinted,
        focused = focusRingShown(focused),
    ) {
        Text(
            text = text,
            color = if (muted) colors.inkMuted else colors.ink,
            style = BodhaType.voiceLine,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = onEdit != null) { onEdit?.invoke() }
                .padding(BodhaSpacing.m),
        )
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("H:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")
