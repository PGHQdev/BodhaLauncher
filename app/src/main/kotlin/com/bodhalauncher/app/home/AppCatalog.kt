package com.bodhalauncher.app.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.bodhalauncher.engine.HomeAction

/** A static or dynamic app shortcut ("New chat", "Selfie") exposed by an installed app. */
data class AppShortcut(
    val shortcutId: String,
    val packageName: String,
    val label: String,
    /** The profile it came from; launching under any other user fails. */
    val user: UserHandle,
    /** The owning app's catalog id (`pkg` or `pkg:serial`), the id [AppCatalog.apps] uses. */
    val appId: String,
)

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

    /** App id to Android's category title, for ids Android categorises at all. */
    val categories = mutableStateOf(queryCategories())

    /** Bumps on every package event, so icon caches keyed on it pick up updates. */
    val version = mutableIntStateOf(0)

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
        val user = handleOf(id) ?: Process.myUserHandle()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageOf(id))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
            )
        queryShortcuts(query, user)
    } catch (_: SecurityException) {
        emptyList()
    } catch (_: IllegalStateException) {
        emptyList()
    }

    /**
     * Every app's shortcuts across profiles, for Search's shortcuts section (#181);
     * empty unless Bodha is the default launcher (Android's rule). One unfiltered
     * query per profile rather than one per app — [LauncherApps] allows it and the
     * per-app form would make a keystroke's refresh O(apps) binder calls.
     */
    fun allShortcuts(): List<AppShortcut> = try {
        launcherApps.profiles.flatMap { user ->
            val query = LauncherApps.ShortcutQuery()
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                )
            queryShortcuts(query, user)
        }
    } catch (_: SecurityException) {
        emptyList()
    } catch (_: IllegalStateException) {
        emptyList()
    } catch (_: UnsupportedOperationException) {
        // Robolectric's LauncherApps shadow rejects manifest-shortcut queries;
        // on a device this never throws.
        emptyList()
    }

    private fun queryShortcuts(query: LauncherApps.ShortcutQuery, user: UserHandle): List<AppShortcut> =
        launcherApps.getShortcuts(query, user)
            .orEmpty()
            .map {
                AppShortcut(
                    it.id, it.`package`, (it.shortLabel ?: it.longLabel).toString(), user,
                    appId = idFor(it.`package`, user),
                )
            }
            .filter { it.label.isNotEmpty() }

    fun launchShortcut(shortcut: AppShortcut) {
        try {
            launcherApps.startShortcut(
                shortcut.packageName, shortcut.shortcutId, null, null, shortcut.user,
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

    /** The app's launcher icon, ready for Compose. */
    fun icon(id: String): ImageBitmap? =
        launcherApps.getActivityList(packageOf(id), handleOf(id) ?: Process.myUserHandle())
            .firstOrNull()?.getIcon(0)?.toBitmap()?.asImageBitmap()

    private fun refresh() {
        apps.value = queryApps()
        categories.value = queryCategories()
        version.intValue++
    }

    private fun queryCategories(): Map<String, String> =
        launcherApps.profiles.flatMap { profile ->
            launcherApps.getActivityList(null, profile).mapNotNull { activity ->
                val info = activity.applicationInfo
                ApplicationInfo.getCategoryTitle(context, info.category)
                    ?.let { idFor(info.packageName, profile) to it.toString() }
            }
        }.toMap()

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

    /**
     * The package name for a primary-profile id; null for work-profile ids.
     * Usage stats cover only the primary profile, so a work-profile app must
     * read as "no data" rather than borrow its personal clone's numbers.
     */
    fun primaryPackage(id: String): String? = id.takeIf { ':' !in it }

    /** The profile behind a `pkg:serial` id; null for the main profile or a gone profile. */
    private fun handleOf(id: String): UserHandle? {
        val serial = id.substringAfter(':', missingDelimiterValue = "").toLongOrNull() ?: return null
        return userManager.getUserForSerialNumber(serial)
    }
}
