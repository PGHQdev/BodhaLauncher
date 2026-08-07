package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.bodhalauncher.app.capability.CapabilityEducation
import com.bodhalauncher.app.inbox.BodhaNotificationListener
import com.bodhalauncher.app.ui.InboxScreen
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.resolveInbox

/**
 * The inbox as a surface of its own (#162, ADR 0015): rows from the live shade,
 * nothing read from storage and nothing written to it. Opening a row fires the
 * notification's own content intent directly — no trampoline through Bodha.
 */
@Composable
fun InboxSurface(
    education: CapabilityEducation,
    onBack: () -> Unit,
) {
    val granted = education.granted(Capability.NotificationAccess)
    val connected by BodhaNotificationListener.connected
    val liveMap by BodhaNotificationListener.live
    val state = remember(granted, connected, liveMap) {
        resolveInbox(
            granted = granted,
            listenerConnected = connected,
            rows = liveMap.values.map { it.row },
        )
    }
    val pm = LocalContext.current.packageManager
    // Marks and labels come from the package manager rather than the app
    // catalog: a notification's source need not be a launchable activity.
    val icons = remember { mutableMapOf<String, ImageBitmap?>() }
    val labels = remember { mutableMapOf<String, String>() }
    fun labelFor(appPackage: String): String = labels.getOrPut(appPackage) {
        runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(appPackage, 0)).toString()
        }.getOrNull() ?: appPackage
    }
    InboxScreen(
        state = state,
        // A redacted notification renders what the system gave us; a row with
        // no title still carries a non-empty name — its source (ADR 0020).
        titleFor = { row ->
            liveMap[row.key]?.title?.takeIf { it.isNotBlank() } ?: labelFor(row.appPackage)
        },
        lineFor = { row -> liveMap[row.key]?.line },
        iconFor = { row ->
            icons.getOrPut(row.appPackage) {
                runCatching {
                    pm.getApplicationIcon(row.appPackage).toBitmap().asImageBitmap()
                }.getOrNull()
            }
        },
        onOpen = { row ->
            // The notification's original destination, directly; a stale intent
            // (its app just cancelled it) simply does nothing.
            runCatching { liveMap[row.key]?.contentIntent?.send() }
        },
        onBack = onBack,
    )
}
