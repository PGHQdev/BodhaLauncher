package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.HomeState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ADR 0010 "Centered Axis": centered serif clock and intention (the voice),
 * left-aligned hairline-rule action list and sans operational text (the machinery).
 * Renders [HomeState] and nothing else — no logic beyond mapping state to layout.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit = {},
) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Clock()
        state.contextLabel?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = colors.inkMuted, fontSize = 13.sp, letterSpacing = 2.sp)
        }
        state.dailyIntention?.let {
            Spacer(Modifier.height(36.dp))
            Text(
                text = it,
                color = colors.ink,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 19.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(48.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            state.actions.forEach { action ->
                ActionRow(action, onAction)
            }
        }
        state.inboxDigest?.let {
            Spacer(Modifier.height(28.dp))
            Text(text = it, color = colors.inkMuted, fontSize = 14.sp)
        }
        if (state.focusActive) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
        }
        Spacer(Modifier.weight(1f))
        SearchField()
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
        fontFamily = FontFamily.Serif,
        fontSize = 64.sp,
    )
    Text(
        text = now.format(dateFormat),
        color = colors.inkMuted,
        fontSize = 14.sp,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun ActionRow(action: HomeAction, onAction: (HomeAction) -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text(
            text = action.label,
            color = colors.ink,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAction(action) }
                .padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun SearchField() {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Search",
            color = colors.inkMuted,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("H:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")
