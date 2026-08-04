package com.bodhalauncher.app.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** The spec's gesture fan-out from Home; taps on actions are consumed by their rows first. */
data class HomeGestures(
    val onSwipeDown: () -> Unit,
    val onSwipeUp: () -> Unit,
    val onSwipeLeft: () -> Unit,
    val onSwipeRight: () -> Unit,
    val onDoubleTapEmpty: () -> Unit,
    val onLongPressEmpty: () -> Unit,
)

fun Modifier.homeGestures(gestures: HomeGestures): Modifier = this
    .pointerInput(gestures) {
        val threshold = 72.dp.toPx()
        var drag = Offset.Zero
        detectDragGestures(
            onDragStart = { drag = Offset.Zero },
            onDrag = { _, amount -> drag += amount },
            onDragEnd = {
                val (x, y) = drag
                when {
                    abs(y) >= abs(x) && y > threshold -> gestures.onSwipeDown()
                    abs(y) >= abs(x) && y < -threshold -> gestures.onSwipeUp()
                    abs(x) > abs(y) && x < -threshold -> gestures.onSwipeLeft()
                    abs(x) > abs(y) && x > threshold -> gestures.onSwipeRight()
                }
            },
        )
    }
    .pointerInput(gestures) {
        detectTapGestures(
            onDoubleTap = { gestures.onDoubleTapEmpty() },
            onLongPress = { gestures.onLongPressEmpty() },
        )
    }
