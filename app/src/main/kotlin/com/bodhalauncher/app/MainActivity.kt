package com.bodhalauncher.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.IntentionStore
import com.bodhalauncher.app.home.LibraryStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.intent.IntentPromptRuntime
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.ui.ActionOptionsDialog
import com.bodhalauncher.app.ui.AppActionsSheet
import com.bodhalauncher.app.ui.AppPickerDialog
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.EditHomeDialog
import com.bodhalauncher.app.ui.HomeGestures
import com.bodhalauncher.app.ui.HomeScreen
import com.bodhalauncher.app.ui.IntentPromptSheet
import com.bodhalauncher.app.ui.IntentionEditorDialog
import com.bodhalauncher.app.ui.LibraryScreen
import com.bodhalauncher.app.ui.PlaceholderSurface
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.HomeInputs
import com.bodhalauncher.engine.IntentCategory
import com.bodhalauncher.engine.LibraryInputs
import com.bodhalauncher.engine.resolveHome
import com.bodhalauncher.engine.resolveLibrary
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {

    private lateinit var catalog: AppCatalog

    override fun onResume() {
        super.onResume()
        (application as BodhaApp).intentPrompt.onLauncherVisible()
    }

    override fun onPause() {
        super.onPause()
        (application as BodhaApp).intentPrompt.onLauncherHidden()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BodhaApp
        val pinStore = PinStore(this)
        val intentionStore = IntentionStore(this)
        val libraryStore = LibraryStore(this)
        catalog = AppCatalog(this)
        catalog.onAppsRemoved = { ids ->
            ids.forEach { pinStore.unpin(it); pinStore.unhide(it) }
        }
        catalog.startWatching()
        setContent {
            BodhaTheme {
                HomeRoot(pinStore, intentionStore, libraryStore, catalog, app.intentPrompt)
            }
        }
    }

    override fun onDestroy() {
        catalog.stopWatching()
        super.onDestroy()
    }
}

/** The surfaces Home's swipes fan out to; all but Home are placeholders for now. */
private enum class HomeSurface(val title: String) {
    Home("Home"),
    Search("Search"),
    Library("App Library"),
    Awareness("Awareness"),
    Today("Today"),
    Focus("Focus"),
    OpenCheck("Open Check"),
}

@Composable
private fun HomeRoot(
    pinStore: PinStore,
    intentionStore: IntentionStore,
    libraryStore: LibraryStore,
    catalog: AppCatalog,
    intentPrompt: IntentPromptRuntime,
) {
    val pinnedIds by pinStore.pinned
    val hidden by pinStore.hidden
    val allApps by catalog.apps
    val intention by intentionStore.intention
    val sessionIntent by intentPrompt.sessionIntent
    val pinned = remember(pinnedIds, allApps) { catalog.resolve(pinnedIds) }
    var pickerOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<HomeAction?>(null) }
    var editingIntention by remember { mutableStateOf(false) }
    var editingHome by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf(HomeSurface.Home) }
    val context = LocalContext.current

    if (surface != HomeSurface.Home) {
        val back = { surface = HomeSurface.Home }
        BackHandler(onBack = back)
        if (surface == HomeSurface.Library) {
            var query by remember { mutableStateOf("") }
            var actionsFor by remember { mutableStateOf<HomeAction?>(null) }
            val hiddenSearchable by libraryStore.hiddenSearchable
            LibraryScreen(
                state = resolveLibrary(
                    LibraryInputs(
                        apps = allApps,
                        query = query,
                        hidden = hidden,
                        hiddenSearchable = hiddenSearchable,
                    )
                ),
                query = query,
                onQueryChange = { query = it },
                onOpen = catalog::launch,
                onLongPress = { actionsFor = it },
                onPin = { pinStore.pin(it.id) },
                onHide = { pinStore.hide(it.id) },
                onUnhide = { pinStore.unhide(it.id) },
                hiddenSearchable = hiddenSearchable,
                onHiddenSearchableChange = libraryStore::setHiddenSearchable,
                onBack = back,
            )
            actionsFor?.let { app ->
                val dismiss = { actionsFor = null }
                AppActionsSheet(
                    app = app,
                    shortcuts = remember(app.id) { catalog.shortcuts(app.id) },
                    isPinned = app.id in pinnedIds,
                    isHidden = app.id in hidden,
                    onOpen = { dismiss(); catalog.launch(app) },
                    onShortcut = { dismiss(); catalog.launchShortcut(it) },
                    onPin = { dismiss(); pinStore.pin(app.id) },
                    onUnpin = { dismiss(); pinStore.unpin(app.id) },
                    onHide = { dismiss(); pinStore.hide(app.id) },
                    onUnhide = { dismiss(); pinStore.unhide(app.id) },
                    onPause = { dismiss(); surface = HomeSurface.Focus },
                    onOpenCheck = { dismiss(); surface = HomeSurface.OpenCheck },
                    onAppInfo = { dismiss(); catalog.openAppInfo(app.id) },
                    onDismiss = dismiss,
                )
            }
        } else {
            PlaceholderSurface(title = surface.title, onBack = back)
        }
        return
    }

    // Ticks each minute so the intention drops at the 4am boundary (ADR 0003)
    // even when Home sits on screen with nothing else changing.
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay((60 - LocalDateTime.now().second) * 1000L)
            value = LocalDateTime.now()
        }
    }

    // Remaining inputs fill in as their features ship (suggestions #6, digest #10, …).
    val state = resolveHome(
        HomeInputs(
            dailyIntention = intention?.textOn(now),
            pinned = pinned,
            hidden = hidden,
            sessionIntent = sessionIntent,
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            state = state,
            onAction = catalog::launch,
            onActionLongPress = { optionsFor = it },
            onAddAction = { pickerOpen = true },
            onEditIntention = { editingIntention = true },
            gestures = HomeGestures(
                onSwipeDown = { surface = HomeSurface.Search },
                onSwipeUp = { surface = HomeSurface.Library },
                onSwipeLeft = { surface = HomeSurface.Awareness },
                onSwipeRight = { surface = HomeSurface.Today },
                // Lock mechanism is settled in the permissions spec (#18); stub until then.
                onDoubleTapEmpty = {
                    Toast.makeText(context, "Lock — mechanism pending", Toast.LENGTH_SHORT).show()
                },
                onLongPressEmpty = { editingHome = true },
            ),
            onSearch = { surface = HomeSurface.Search },
        )

        val due by intentPrompt.promptDue
        if (due != null) {
            IntentPromptSheet(
                onSelect = { category, text ->
                    intentPrompt.select(category, text)
                    // The intent flows straight into the action.
                    if (category == IntentCategory.FindSomething) surface = HomeSurface.Search
                },
                onDismiss = intentPrompt::dismiss,
            )
        }
    }

    if (pickerOpen) {
        AppPickerDialog(
            apps = allApps.filter { it.id !in pinnedIds },
            onPick = { pinStore.pin(it.id); pickerOpen = false },
            onDismiss = { pickerOpen = false },
        )
    }
    if (editingHome) {
        EditHomeDialog(onAddPin = { pickerOpen = true }, onDismiss = { editingHome = false })
    }
    if (editingIntention) {
        IntentionEditorDialog(
            current = intention?.textOn(now),
            onSave = { intentionStore.set(it, LocalDateTime.now()) },
            onClear = intentionStore::clear,
            onDismiss = { editingIntention = false },
        )
    }
    optionsFor?.let { action ->
        ActionOptionsDialog(
            action = action,
            isPinned = action.id in pinnedIds,
            onPin = { pinStore.pin(it.id) },
            onUnpin = { pinStore.unpin(it.id) },
            onHide = { pinStore.hide(it.id) },
            onDismiss = { optionsFor = null },
        )
    }
}
