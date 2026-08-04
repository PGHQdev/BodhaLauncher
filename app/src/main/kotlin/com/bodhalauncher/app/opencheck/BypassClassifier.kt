package com.bodhalauncher.app.opencheck

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.telecom.TelecomManager

/**
 * Classifies the emergency/utility apps whose launches bypass Open Check (#77):
 * the default dialer (which also carries emergency calling) and the default
 * clock/alarm app. Classification is a default, never a lock — the caller skips
 * it for an app the user explicitly ruled. Defaults are resolved once per
 * launcher process; changing the system dialer mid-session is rare enough that
 * the next launch picks it up.
 */
class BypassClassifier(context: Context) {

    private val packages: Set<String> by lazy {
        setOfNotNull(
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
            context.packageManager
                .resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
                ?.activityInfo?.packageName,
        )
    }

    fun bypasses(packageName: String?): Boolean = packageName != null && packageName in packages
}
