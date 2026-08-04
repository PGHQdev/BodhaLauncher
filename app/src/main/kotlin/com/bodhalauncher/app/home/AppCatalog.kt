package com.bodhalauncher.app.home

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import com.bodhalauncher.engine.HomeAction

/** A static or dynamic app shortcut ("New chat", "Selfie") exposed by an installed app. */
data class AppShortcut(val shortcutId: String, val packageName: String, val label: String)

/**
 * Resolves launchable apps across user profiles via [LauncherApps] (ADR 0002)
 * and tracks package install/update/removal and profile changes live.
 *
 * Ids are package names; apps in a managed (work) profile get `pkg:serial` ids
 * so the two copies stay distinct. Call [startWatching]/[stopWatching] around
 * the activity's lifetime.
 */
class AppCatalog(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /** All launchable apps across profiles; updates as packages and profiles change. */
    val apps = mutableStateOf(queryApps())

    /** Fires with the ids of apps that were uninstalled, so stale state can drop out. */
    var onAppsRemoved: ((Set<String>) -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(p: Array<String>, u: UserHandle, r: Boolean) = refresh()
        // Unavailable = profile off or storage ejected — the app may come back, so
        // the list refreshes but nothing is pruned; only true removal prunes.
        override fun onPackagesUnavailable(p: Array<String>, u: UserHandle, r: Boolean) = refresh()

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refresh()
            onAppsRemoved?.invoke(setOf(idFor(packageName, user)))
        }
    }

    fun startWatching() = launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))

    fun stopWatching() = launcherApps.unregisterCallback(callback)

    fun installedApps(): List<HomeAction> = apps.value

    /** Resolved actions for pinned ids, keeping the user's order; stale pins drop out. */
    fun resolve(ids: List<String>): List<HomeAction> {
        val byId = apps.value.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun launch(action: HomeAction) {
        val handle = handleOf(action.id)
        if (handle != null) {
            val activity = launcherApps.getActivityList(packageOf(action.id), handle).firstOrNull()
                ?: return
            launcherApps.startMainActivity(activity.componentName, handle, null, null)
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(action.id) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** An app's shortcuts; empty unless Bodha is the default launcher (Android's rule). */
    fun shortcuts(id: String): List<AppShortcut> = try {
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageOf(id))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
            )
        launcherApps.getShortcuts(query, handleOf(id) ?: Process.myUserHandle())
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
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${packageOf(id)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun refresh() {
        apps.value = queryApps()
    }

    private fun queryApps(): List<HomeAction> =
        launcherApps.profiles.flatMap { profile ->
            val work = profile != Process.myUserHandle()
            launcherApps.getActivityList(null, profile).map {
                HomeAction(
                    id = idFor(it.applicationInfo.packageName, profile),
                    label = it.label.toString() + if (work) " (work)" else "",
                )
            }
        }.distinctBy { it.id }

    private fun idFor(packageName: String, user: UserHandle): String =
        if (user == Process.myUserHandle()) packageName
        else "$packageName:${userManager.getSerialNumberForUser(user)}"

    private fun packageOf(id: String): String = id.substringBefore(':')

    /** The profile behind a `pkg:serial` id; null for the main profile or a gone profile. */
    private fun handleOf(id: String): UserHandle? {
        val serial = id.substringAfter(':', missingDelimiterValue = "").toLongOrNull() ?: return null
        return userManager.getUserForSerialNumber(serial)
    }
}
