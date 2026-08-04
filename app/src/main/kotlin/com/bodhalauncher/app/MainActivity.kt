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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.IntentionStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.intent.IntentPromptRuntime
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.ui.ActionOptionsDialog
import com.bodhalauncher.app.ui.AppPickerDialog
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.EditHomeDialog
import com.bodhalauncher.app.ui.HomeGestures
import com.bodhalauncher.app.ui.HomeScreen
import com.bodhalauncher.app.ui.IntentPromptSheet
import com.bodhalauncher.app.ui.IntentionEditorDialog
import com.bodhalauncher.app.ui.PlaceholderSurface
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.HomeInputs
import com.bodhalauncher.engine.resolveHome
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BodhaApp
        val pinStore = PinStore(this)
        val intentionStore = IntentionStore(this)
        val catalog = AppCatalog(this)
        setContent {
            BodhaTheme {
                HomeRoot(pinStore, intentionStore, catalog, app.intentPrompt)
            }
        }
    }
}

/** The surfaces Home's swipes fan out to; all but Home are placeholders for now. */
private enum class HomeSurface(val title: String) {
    Home("Home"),
    Search("Search"),
    Library("App Library"),
    Awareness("Awareness"),
    Today("Today"),
}

@Composable
private fun HomeRoot(
    pinStore: PinStore,
    intentionStore: IntentionStore,
    catalog: AppCatalog,
    intentPrompt: IntentPromptRuntime,
) {
    val pinnedIds by pinStore.pinned
    val hidden by pinStore.hidden
    val intention by intentionStore.intention
    val pinned = remember(pinnedIds) { catalog.resolve(pinnedIds) }
    var pickerOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<HomeAction?>(null) }
    var editingIntention by remember { mutableStateOf(false) }
    var editingHome by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf(HomeSurface.Home) }
    val context = LocalContext.current

    if (surface != HomeSurface.Home) {
        BackHandler { surface = HomeSurface.Home }
        PlaceholderSurface(title = surface.title, onBack = { surface = HomeSurface.Home })
        return
    }

    // Sampled per recomposition — good enough for the 4am boundary (ADR 0003):
    // any state change or activity resume re-evaluates validity.
    val now = LocalDateTime.now()

    // Remaining inputs fill in as their features ship (suggestions #6, digest #10, …).
    val state = resolveHome(
        HomeInputs(
            dailyIntention = intention?.textOn(now),
            pinned = pinned,
            hidden = hidden,
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
        )

        val due by intentPrompt.promptDue
        if (due != null) {
            IntentPromptSheet(
                onSelect = { category, text -> intentPrompt.select(category, text) },
                onDismiss = intentPrompt::dismiss,
            )
        }
    }

    if (pickerOpen) {
        val apps = remember { catalog.installedApps() }
        AppPickerDialog(
            apps = apps.filter { it.id !in pinnedIds },
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
