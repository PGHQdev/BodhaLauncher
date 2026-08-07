package com.bodhalauncher.app.capability

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.bodhalauncher.engine.Capability

/**
 * The thin Android edge behind the capability reducer (#18): reads actual grant
 * state and maps "continue" to the relevant system screen. No education rules
 * live here.
 */
class CapabilityEdge(
    private val context: Context,
    /**
     * Launches the in-app runtime permission dialog — the host's
     * `ActivityResultLauncher` behind a plain function. Null where no activity
     * is present (tests); the app-details screen stays the honest fallback.
     */
    private val requestPermission: ((String) -> Unit)? = null,
) {

    fun granted(capability: Capability): Boolean = when (capability) {
        Capability.UsageAccess -> hasUsageAccess()
        Capability.NotificationAccess ->
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        Capability.Contacts -> hasPermission(android.Manifest.permission.READ_CONTACTS)
        Capability.Calendar -> hasPermission(android.Manifest.permission.READ_CALENDAR)
        Capability.CoarseLocation -> hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /**
     * Step 5 of the education flow: the exact Android surface for the grant.
     * Calendar continues into the in-app runtime request (#159) — education
     * always precedes it, so the dialog only ever follows an explicit continue.
     * Contacts and location join it when their features exist; app details is
     * the honest destination until then.
     */
    fun openSystemScreen(capability: Capability) {
        val intent = when (capability) {
            Capability.UsageAccess -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            Capability.NotificationAccess -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Capability.Calendar -> {
                val request = requestPermission
                if (request != null) {
                    request(android.Manifest.permission.READ_CALENDAR)
                    return
                }
                appDetails()
            }
            else -> appDetails()
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun appDetails(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return when (appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                hasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
            else -> false
        }
    }
}

/** Which education screens have been delivered — denial memory that survives restarts (#18). */
class EducationStateStore(context: Context) {

    private val prefs = context.getSharedPreferences("capability_education", Context.MODE_PRIVATE)

    fun shown(capability: Capability): Boolean = prefs.getBoolean(capability.name, false)

    fun markShown(capability: Capability) {
        prefs.edit { putBoolean(capability.name, true) }
    }

    /** Whether the education-then-grant outcome was already recorded (#25) — once per capability. */
    fun grantLogged(capability: Capability): Boolean = prefs.getBoolean("${capability.name}_grant", false)

    fun markGrantLogged(capability: Capability) {
        prefs.edit { putBoolean("${capability.name}_grant", true) }
    }
}
