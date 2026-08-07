package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.bodhalauncher.app.home.IntentionStore
import com.bodhalauncher.app.ui.IntentionSheet
import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.app.ui.TodayScreen
import com.bodhalauncher.engine.dayKey
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * Today as a surface of its own (#158): the day surface, and the one place the
 * intention is set from here on. The whole surface runs on the 4am day key
 * (ADR 0003), ticking each minute so crossing 4:00am on screen clears the
 * intention and rolls the header without a relaunch.
 */
@Composable
fun TodaySurface(
    intentionStore: IntentionStore,
    sheets: SheetSlot,
) {
    val intention by intentionStore.intention
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay((60 - LocalDateTime.now().second) * 1000L)
            value = LocalDateTime.now()
        }
    }
    val day = dayKey(now)
    val text = intention?.textOn(now)
    // The editor is about this surface, so it leaves with it — the system Home
    // button must not park it in the app-wide slot (#132, ADR 0011).
    DisposableEffect(Unit) {
        onDispose { sheets.showing<Sheet.IntentionEditor>()?.let(sheets::close) }
    }
    TodayScreen(
        day = day,
        intention = text,
        onEditIntention = { sheets.open(Sheet.IntentionEditor()) },
    )
    sheets.showing<Sheet.IntentionEditor>()?.let { sheet ->
        val dismiss = sheets.dismissedBy(sheet) { sheets.close(sheet) }
        IntentionSheet(
            current = text,
            // Kept for exactly one day: only the previous day key's text offers.
            suggestion = intention?.takeIf { it.dayKey == day.minusDays(1) }?.text,
            onSave = { intentionStore.set(it, LocalDateTime.now()); sheets.close(sheet) },
            onClear = { intentionStore.clear(); sheets.close(sheet) },
            onDismiss = dismiss,
        )
    }
}
