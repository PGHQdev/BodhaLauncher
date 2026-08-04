package com.bodhalauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bodhalauncher.app.home.AppCatalog
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.intent.IntentPromptRuntime
import com.bodhalauncher.app.ui.ActionOptionsDialog
import com.bodhalauncher.app.ui.AppPickerDialog
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.HomeScreen
import com.bodhalauncher.app.ui.LocalBodhaColors
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.HomeInputs
import com.bodhalauncher.engine.resolveHome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BodhaApp
        val pinStore = PinStore(this)
        val catalog = AppCatalog(this)
        setContent {
            BodhaTheme {
                HomeRoot(pinStore, catalog, app.intentPrompt)
            }
        }
    }
}

@Composable
private fun HomeRoot(
    pinStore: PinStore,
    catalog: AppCatalog,
    intentPrompt: IntentPromptRuntime,
) {
    val pinnedIds by pinStore.pinned
    val hidden by pinStore.hidden
    val pinned = remember(pinnedIds) { catalog.resolve(pinnedIds) }
    var pickerOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<HomeAction?>(null) }

    // Remaining inputs fill in as their features ship (intention #53, suggestions #6, …).
    val state = resolveHome(HomeInputs(pinned = pinned, hidden = hidden))

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            state = state,
            onAction = catalog::launch,
            onActionLongPress = { optionsFor = it },
            onAddAction = { pickerOpen = true },
        )

        // Temporary prompt-due signal; the bottom sheet (#55) replaces it.
        val due by intentPrompt.promptDue
        if (due != null) {
            Text(
                text = "What are you here for?",
                color = LocalBodhaColors.current.accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 96.dp)
                    .clickable { intentPrompt.dismiss() },
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
