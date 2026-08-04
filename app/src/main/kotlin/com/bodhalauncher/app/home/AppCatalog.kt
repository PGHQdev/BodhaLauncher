package com.bodhalauncher.app.home

import android.content.Context
import android.content.Intent
import com.bodhalauncher.engine.HomeAction

/** Resolves launchable apps through the manifest's scoped queries filter (ADR 0002). */
class AppCatalog(private val context: Context) {

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
}
