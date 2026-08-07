package com.bodhalauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * The wall clock at minute cadence: recomposes the reader on each minute mark,
 * which is what lets a surface sitting on screen cross the 4am day boundary
 * (ADR 0003) without a relaunch.
 */
@Composable
fun minuteNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay((60 - LocalDateTime.now().second) * 1000L)
            value = LocalDateTime.now()
        }
    }
    return now
}
