package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.LibraryIndexEntry
import com.bodhalauncher.engine.LibraryState
import kotlinx.coroutines.launch

/**
 * The App Library: a quiet text-first list of every launchable app (ADR 0010 —
 * left-aligned hairline machinery, no icons or badges). Renders [LibraryState]
 * and nothing else. Pulling down past the list's top returns Home, mirroring
 * the swipe-up that opened it; back does the same via the caller's BackHandler.
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (HomeAction) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    val dismissThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val overscroll = remember { mutableFloatStateOf(0f) }
    val dismissOnOverscroll = remember(onBack) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    overscroll.floatValue += available.y
                    if (overscroll.floatValue > dismissThreshold) {
                        overscroll.floatValue = 0f
                        onBack()
                    }
                } else {
                    // List consumed the motion or it went upward: this is a scroll, not a pull.
                    overscroll.floatValue = 0f
                }
                return Offset.Zero
            }

            // Gesture ends in a fling pass; reset so pulls never sum across gestures.
            override suspend fun onPreFling(available: Velocity): Velocity {
                overscroll.floatValue = 0f
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        // Sans: a list header is machinery, not the voice (ADR 0010).
        Text(
            text = "Apps",
            color = colors.inkMuted,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        SearchField(query, onQueryChange)
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(dismissOnOverscroll),
            ) {
                items(count = state.rows.size, key = { state.rows[it].id }) { index ->
                    AppRow(state.rows[index], onOpen)
                }
            }
            if (state.index.size > 1) {
                AlphabetScrubber(
                    index = state.index,
                    onJump = { row -> scope.launch { listState.scrollToItem(row) } },
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * The letter rail: drag or tap jumps the list to that letter's first app.
 * Shows only letters the resolved rows actually contain.
 */
@Composable
private fun AlphabetScrubber(
    index: List<LibraryIndexEntry>,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBodhaColors.current
    val railHeight = remember { mutableIntStateOf(0) }
    fun jumpTo(y: Float) {
        if (railHeight.intValue == 0) return
        val slot = (y / railHeight.intValue * index.size).toInt().coerceIn(0, index.size - 1)
        onJump(index[slot].firstRow)
    }
    Column(
        modifier = modifier
            .onSizeChanged { railHeight.intValue = it.height }
            .pointerInput(index) {
                detectVerticalDragGestures(
                    onDragStart = { jumpTo(it.y) },
                    onVerticalDrag = { change, _ -> jumpTo(change.position.y) },
                )
            }
            .pointerInput(index) { detectTapGestures { jumpTo(it.y) } }
            .padding(start = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        index.forEach {
            Text(text = it.letter.toString(), color = colors.inkMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.ink, fontSize = 16.sp),
            cursorBrush = SolidColor(colors.ink),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) {
                        Text(text = "Search", color = colors.inkMuted, fontSize = 16.sp)
                    }
                    field()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    }
}

@Composable
private fun AppRow(app: HomeAction, onOpen: (HomeAction) -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text(
            text = app.label,
            color = colors.ink,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(app) }
                .padding(vertical = 16.dp),
        )
    }
}
