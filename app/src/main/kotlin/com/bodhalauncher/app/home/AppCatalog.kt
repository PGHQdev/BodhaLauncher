package com.bodhalauncher.app.home

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.bodhalauncher.engine.HomeAction

/** A static or dynamic app shortcut ("New chat", "Selfie") exposed by an installed app. */
data class AppShortcut(val shortcutId: String, val packageName: String, val label: String)

/** Resolves launchable apps through the manifest's scoped queries filter (ADR 0002). */
class AppCatalog(private val context: Context) {

    private val launcherApps get() = context.getSystemService(LauncherApps::class.java)

    /** All launchable apps, sorted by label. */
    fun installedApps(): List<HomeAction> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        return pm.queryIntentActivities(launcher, 0)
            .map { HomeAction(id = it.activityInfo.packageName, label = it.loadLabel(pm).toString()) }
            .distinctBy { it.id }
            .sortedBy { it.label.lowercase() }
    }

    /** Resolved actions for pinned package names, keeping the user's order; stale pins drop out. */
    fun resolve(ids: List<String>): List<HomeAction> {
        val byId = installedApps().associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun launch(action: HomeAction) {
        val intent = context.packageManager.getLaunchIntentForPackage(action.id) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** An app's shortcuts; empty unless Bodha is the default launcher (Android's rule). */
    fun shortcuts(id: String): List<AppShortcut> = try {
        val query = LauncherApps.ShortcutQuery()
            .setPackage(id)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
            )
        launcherApps.getShortcuts(query, Process.myUserHandle())
            .orEmpty()
            .map { AppShortcut(it.id, it.`package`, (it.shortLabel ?: it.longLabel).toString()) }
            .filter { it.label.isNotEmpty() }
    } catch (_: SecurityException) {
        emptyList()
    } catch (_: IllegalStateException) {
        emptyList()
    }

    fun launchShortcut(shortcut: AppShortcut) {
        try {
            launcherApps.startShortcut(
                shortcut.packageName, shortcut.shortcutId, null, null, Process.myUserHandle(),
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
    }

    fun openAppInfo(id: String) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
