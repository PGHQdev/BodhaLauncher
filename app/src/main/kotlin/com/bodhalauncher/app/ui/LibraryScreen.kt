package com.bodhalauncher.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.LibraryIndexEntry
import com.bodhalauncher.engine.LibraryLayout
import com.bodhalauncher.engine.LibraryState
import kotlinx.coroutines.launch

/**
 * The App Library: a quiet text-first list of launchable apps (ADR 0010 —
 * left-aligned hairline machinery, no badges), with a search field and layout
 * switcher above the rows [LibraryState] resolved. Optional layouts render the
 * same resolved content denser (compact icons) or grouped (categories). Tap
 * opens, a rightward swipe pins to Home, leftward hides, long-press opens the
 * actions sheet. Pulling down past the top returns Home, mirroring the
 * swipe-up that opened it; back does the same via the caller's BackHandler.
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    query: String,
    onQueryChange: (String) -> Unit,
    onLayoutChange: (LibraryLayout) -> Unit,
    /** Opens usage-access settings; the layout note is the entry point. */
    onLayoutNoteTap: () -> Unit,
    iconFor: (HomeAction) -> ImageBitmap?,
    /** Changes when any package changes, so cached icons refresh with their apps. */
    iconKey: Any,
    onOpen: (HomeAction) -> Unit,
    onLongPress: (HomeAction) -> Unit,
    onPin: (HomeAction) -> Unit,
    onHide: (HomeAction) -> Unit,
    onUnhide: (HomeAction) -> Unit,
    hiddenSearchable: Boolean,
    onHiddenSearchableChange: (Boolean) -> Unit,
    /** User group names, so the Groups layout knows which sections it may manage. */
    groupNames: List<String>,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onBack: () -> Unit,
) {
    // null = closed, "" = creating, else the group being renamed or deleted.
    val groupEditor = remember { mutableStateOf<String?>(null) }
    val colors = LocalBodhaColors.current
    val dismissThreshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
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
            style = BodhaType.overline,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        LibrarySearchField(query, onQueryChange)
        LayoutSwitcher(state.layout, onLayoutChange)
        state.layoutNote?.let { note ->
            Text(
                text = note,
                color = colors.inkMuted,
                style = BodhaType.caption,
                modifier = Modifier
                    .touchTargetFloor()
                    .clickable(onClick = onLayoutNoteTap)
                    .padding(bottom = 12.dp),
            )
        }
        val hiddenExpanded = remember { mutableStateOf(false) }
        val showHiddenRows = hiddenExpanded.value || query.isNotBlank()
        val scope = rememberCoroutineScope()
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.layout == LibraryLayout.CompactIcons) {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 76.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().nestedScroll(dismissOnOverscroll),
                ) {
                    items(count = state.rows.size, key = { state.rows[it].id }) { i ->
                        IconCell(state.rows[i], iconKey, iconFor, onOpen, onLongPress)
                    }
                    hiddenSection(
                        state.hiddenRows, hiddenExpanded.value, showHiddenRows,
                        hiddenSearchable, onHiddenSearchableChange,
                        onToggle = { hiddenExpanded.value = !hiddenExpanded.value },
                        row = { app ->
                            AppRow(app, onOpen, onLongPress, onSwipeRight = onUnhide)
                        },
                    )
                }
                if (state.index.isNotEmpty()) {
                    AlphabetScrubber(
                        index = state.index,
                        onJump = { row -> scope.launch { gridState.scrollToItem(row) } },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(dismissOnOverscroll),
                ) {
                    if (state.sections.isEmpty()) {
                        items(count = state.rows.size, key = { state.rows[it].id }) { i ->
                            val app = state.rows[i]
                            AppRow(
                                app, onOpen, onLongPress,
                                onSwipeRight = onPin, onSwipeLeft = onHide,
                                lastUsedLine = state.lastUsedLines[app.id],
                            )
                        }
                    } else {
                        state.sections.forEach { section ->
                            val manageable = state.layout == LibraryLayout.Groups &&
                                section.title in groupNames
                            item(key = "section:" + section.title) {
                                SectionHeader(
                                    title = section.title,
                                    onLongPress = { groupEditor.value = section.title }
                                        .takeIf { manageable },
                                )
                            }
                            items(
                                // Grouped rows repeat across sections, so ids alone
                                // can't key them.
                                count = section.rows.size,
                                key = { "section:" + section.title + ":" + section.rows[it].id },
                            ) { i ->
                                val app = section.rows[i]
                                AppRow(
                                    app, onOpen, onLongPress,
                                    onSwipeRight = onPin, onSwipeLeft = onHide,
                                    lastUsedLine = state.lastUsedLines[app.id],
                                )
                            }
                        }
                        if (state.layout == LibraryLayout.Groups && query.isBlank()) {
                            item(key = "new-group") {
                                NewGroupRow(onTap = { groupEditor.value = "" })
                            }
                        }
                    }
                    hiddenSection(
                        state.hiddenRows, hiddenExpanded.value, showHiddenRows,
                        hiddenSearchable, onHiddenSearchableChange,
                        onToggle = { hiddenExpanded.value = !hiddenExpanded.value },
                        row = { app ->
                            AppRow(app, onOpen, onLongPress, onSwipeRight = onUnhide)
                        },
                    )
                }
                if (state.index.isNotEmpty()) {
                    AlphabetScrubber(
                        index = state.index,
                        onJump = { row -> scope.launch { listState.scrollToItem(row) } },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
    }

    groupEditor.value?.let { editing ->
        GroupEditorDialog(
            existing = editing.takeIf { it.isNotEmpty() },
            taken = groupNames,
            onSave = { name ->
                if (editing.isEmpty()) onCreateGroup(name) else onRenameGroup(editing, name)
            },
            onDelete = { onDeleteGroup(editing) },
            onDismiss = { groupEditor.value = null },
        )
    }
}

/** The hidden section, shared by list and grid scopes via each scope's item builders. */
private fun androidx.compose.foundation.lazy.LazyListScope.hiddenSection(
    hiddenRows: List<HomeAction>,
    expanded: Boolean,
    showRows: Boolean,
    hiddenSearchable: Boolean,
    onHiddenSearchableChange: (Boolean) -> Unit,
    onToggle: () -> Unit,
    row: @Composable (HomeAction) -> Unit,
) {
    if (hiddenRows.isEmpty()) return
    item(key = "hidden-header") { HiddenHeader(hiddenRows.size, expanded, onToggle) }
    if (showRows) {
        item(key = "hidden-searchable") {
            HiddenSearchableRow(hiddenSearchable, onHiddenSearchableChange)
        }
        items(count = hiddenRows.size, key = { "hidden:" + hiddenRows[it].id }) { i ->
            row(hiddenRows[i])
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.hiddenSection(
    hiddenRows: List<HomeAction>,
    expanded: Boolean,
    showRows: Boolean,
    hiddenSearchable: Boolean,
    onHiddenSearchableChange: (Boolean) -> Unit,
    onToggle: () -> Unit,
    row: @Composable (HomeAction) -> Unit,
) {
    if (hiddenRows.isEmpty()) return
    val fullSpan: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan =
        { GridItemSpan(it.maxLineSpan) }
    item(key = "hidden-header", span = fullSpan) { HiddenHeader(hiddenRows.size, expanded, onToggle) }
    if (showRows) {
        item(key = "hidden-searchable", span = fullSpan) {
            HiddenSearchableRow(hiddenSearchable, onHiddenSearchableChange)
        }
        items(
            count = hiddenRows.size,
            key = { "hidden:" + hiddenRows[it].id },
            span = { fullSpan(this) },
        ) { i ->
            row(hiddenRows[i])
        }
    }
}

@Composable
internal fun LayoutSwitcher(current: LibraryLayout, onChange: (LibraryLayout) -> Unit) {
    val colors = LocalBodhaColors.current
    // Scrolls rather than squeezes: five labels at the 48dp floor need ~332dp, which
    // a 360dp phone does not have once the page padding is off (ADR 0020 — the floor
    // wins, so the row gives way instead of the targets).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
    ) {
        layoutLabels.forEach { (layout, label) ->
            Text(
                text = label,
                color = if (layout == current) colors.ink else colors.inkMuted,
                style = BodhaType.label,
                modifier = Modifier
                    .touchTargetFloor()
                    .clickable { onChange(layout) }
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.width(20.dp))
        }
    }
}

private val layoutLabels = listOf(
    LibraryLayout.Alphabetical to "A–Z",
    LibraryLayout.CompactIcons to "Icons",
    LibraryLayout.Categories to "Categories",
    LibraryLayout.Recent to "Recent",
    LibraryLayout.Groups to "Groups",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SectionHeader(title: String, onLongPress: (() -> Unit)? = null) {
    val colors = LocalBodhaColors.current
    Text(
        text = title,
        color = colors.inkMuted,
        style = BodhaType.overline,
        modifier = Modifier
            .fillMaxWidth()
            .touchTargetFloor()
            .let { base ->
                if (onLongPress == null) base
                else base.combinedClickable(onClick = {}, onLongClick = onLongPress)
            }
            .padding(top = 20.dp, bottom = 8.dp),
    )
}

/** The Groups layout's quiet entry point for creating a group. */
@Composable
internal fun NewGroupRow(onTap: () -> Unit) {
    val colors = LocalBodhaColors.current
    Text(
        text = "New group …",
        color = colors.inkMuted,
        style = BodhaType.label,
        modifier = Modifier
            .fillMaxWidth()
            .touchTargetFloor()
            .clickable(onClick = onTap)
            .padding(top = 20.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IconCell(
    app: HomeAction,
    iconKey: Any,
    iconFor: (HomeAction) -> ImageBitmap?,
    onOpen: (HomeAction) -> Unit,
    onLongPress: (HomeAction) -> Unit,
) {
    val colors = LocalBodhaColors.current
    val icon = remember(app.id, iconKey) { iconFor(app) }
    Column(
        modifier = Modifier
            .touchTargetFloor()
            .combinedClickable(
                onClick = { onOpen(app) },
                onLongClick = { onLongPress(app) },
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(44.dp))
        } else {
            Box(Modifier.size(44.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = colors.ink,
            style = BodhaType.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * The letter rail: drag or tap jumps the list to that letter's first app.
 * Shows only letters the resolved rows actually contain.
 *
 * **One accessibility node, not one per letter** (ADR 0020). The rail fills the
 * height with a slot per present letter — about 27 slots over ~700dp, so ~26dp
 * each — and no phone is tall enough for 27 targets at the 48dp floor. So the
 * letters' own semantics are cleared, stopping a screen reader focusing them as
 * bare characters, and the rail carries one custom action per letter: a direct
 * jump, rather than the 26 increments an adjustable control would cost.
 *
 * Only its touch area grows to the floor; [Alignment.End] keeps the letters
 * drawn against the same edge, so nothing moves visually.
 */
@Composable
internal fun AlphabetScrubber(
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
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .touchTargetFloor()
            .clearAndSetSemantics {
                contentDescription = RAIL_LABEL
                // Labelled by the letter alone: the reader already reads the
                // node's own name before the action list.
                customActions = index.map { entry ->
                    CustomAccessibilityAction(entry.letter.toString()) {
                        onJump(entry.firstRow)
                        true
                    }
                }
            }
            .onSizeChanged { railHeight.intValue = it.height }
            .pointerInput(index) {
                detectVerticalDragGestures(
                    onDragStart = { jumpTo(it.y) },
                    onVerticalDrag = { change, _ -> jumpTo(change.position.y) },
                )
            }
            .pointerInput(index) { detectTapGestures { jumpTo(it.y) } },
    ) {
        // Each letter gets a uniform slot so jumpTo's y-to-slot math is exact.
        index.forEach {
            Box(
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = it.letter.toString(), color = colors.inkMuted, style = BodhaType.caption)
            }
        }
    }
}

/** The rail's spoken name, asserted by [AccessibilityFloorTest]. */
internal const val RAIL_LABEL = "Jump to letter"

@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = BodhaType.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) {
                        Text(text = "Search", color = colors.inkMuted, style = BodhaType.body)
                    }
                    field()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    app: HomeAction,
    onOpen: (HomeAction) -> Unit,
    onLongPress: (HomeAction) -> Unit,
    onSwipeRight: ((HomeAction) -> Unit)? = null,
    onSwipeLeft: ((HomeAction) -> Unit)? = null,
    /** Subdued screen-time line ("Last used 8 minutes ago"); absent without usage access. */
    lastUsedLine: String? = null,
) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
                .pointerInput(app, onSwipeRight, onSwipeLeft) {
                    val threshold = SWIPE_THRESHOLD.toPx()
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = {
                            when {
                                drag > threshold -> onSwipeRight?.invoke(app)
                                drag < -threshold -> onSwipeLeft?.invoke(app)
                            }
                        },
                    )
                }
                .combinedClickable(
                    onClick = { onOpen(app) },
                    onLongClick = { onLongPress(app) },
                )
                .padding(vertical = if (lastUsedLine == null) 16.dp else 12.dp),
        ) {
            Text(text = app.label, color = colors.ink, style = BodhaType.body)
            if (lastUsedLine != null) {
                Text(text = lastUsedLine, color = colors.inkMuted, style = BodhaType.caption)
            }
        }
    }
}

@Composable
internal fun HiddenHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text(
            text = if (expanded) "Hidden · $count" else "Hidden · $count …",
            color = colors.inkMuted,
            style = BodhaType.overline,
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
                .clickable(onClick = onToggle)
                .padding(vertical = 16.dp),
        )
    }
}

@Composable
internal fun HiddenSearchableRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalBodhaColors.current
    Text(
        text = if (enabled) "Shown in search" else "Kept out of search",
        color = if (enabled) colors.ink else colors.inkMuted,
        style = BodhaType.label,
        modifier = Modifier
            .fillMaxWidth()
            .touchTargetFloor()
            .clickable { onChange(!enabled) }
            .padding(vertical = 8.dp),
    )
}
