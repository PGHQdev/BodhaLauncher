package com.bodhalauncher.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bodhalauncher.app.focus.FocusStore
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.ui.CardRow
import com.bodhalauncher.app.ui.FocusScreen
import com.bodhalauncher.app.ui.FocusSetupSheet
import com.bodhalauncher.app.ui.HomeGestures
import com.bodhalauncher.app.ui.LocalBodhaColors
import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.app.ui.focusOnOpen
import com.bodhalauncher.engine.focusRemainingPhrase
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * Focus (#166, ADR 0012): setup when nothing runs, the running session when one
 * does. Setup is a sheet through the single slot, offered on arrival and again
 * from the row behind it — never while a session is active, which is what makes
 * one-at-a-time structural. Starting never touches the entitlement gate: the
 * start path is [FocusStore.start], which has no reference to reach it with.
 */
@Composable
fun FocusSurface(
    focusStore: FocusStore,
    catalog: AppCatalog,
    sheets: SheetSlot,
    gestures: HomeGestures,
) {
    val session by focusStore.active
    val running = session
    if (running == null) {
        FocusIdle(sheets)
        val apps by catalog.apps
        sheets.showing<Sheet.FocusSetup>()?.let { sheet ->
            val dismiss = sheets.dismissedBy(sheet) { sheets.close(sheet) }
            FocusSetupSheet(
                apps = apps,
                onStart = { label, minutes, allowed ->
                    focusStore.start(label, minutes, allowed, Instant.now())
                    sheets.close(sheet)
                },
                onDismiss = dismiss,
            )
        }
    } else {
        // Ticks each second so the remaining time is always the end instant
        // against wall clock — the derivation, re-run, rather than a countdown.
        val now by produceState(Instant.now(), running.endsAt) {
            while (true) {
                value = Instant.now()
                delay(1_000)
            }
        }
        FocusScreen(
            label = running.label,
            remaining = focusRemainingPhrase(running.endsAt, now),
            // Resolved through the catalog, so an uninstalled app drops off (#166).
            allowedAppLabels = catalog.resolve(running.allowedAppIds.toList()).map { it.label },
            onEnd = { focusStore.endEarly(Instant.now()) },
            gestures = gestures,
        )
    }
}

/**
 * Behind the setup sheet (#166): the one way back into setup after a dismissal.
 * A surface, so back and Escape resolve through the navigation model unchanged.
 */
@Composable
private fun FocusIdle(sheets: SheetSlot) {
    val colors = LocalBodhaColors.current
    // The sheet is the arrival (#166); the row remains for the user who dismissed it.
    LaunchedEffect(Unit) {
        if (sheets.current == null) sheets.open(Sheet.FocusSetup())
    }
    DisposableEffect(Unit) {
        // Setup is about this surface, so it leaves with it (#132, ADR 0011).
        onDispose { sheets.showing<Sheet.FocusSetup>()?.let(sheets::close) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CardRow(
            title = "Start a Focus session",
            onClick = { sheets.open(Sheet.FocusSetup()) },
            modifier = Modifier.focusOnOpen(),
        )
    }
}
