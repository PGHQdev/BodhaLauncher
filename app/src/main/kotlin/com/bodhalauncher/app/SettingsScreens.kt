package com.bodhalauncher.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.bodhalauncher.engine.SearchAction

/** One Android settings screen Bodha will offer as a search result (#188). */
data class SettingsScreen(
    val label: String,
    val intentAction: String,
    /** Spellings people type that the label's own words cannot answer for. */
    val keywords: List<String> = emptyList(),
)

/**
 * The settings screens Search knows, Bodha-authored: Android exposes no queryable
 * index of its own screens, so a hand-kept list of the stable `Settings.ACTION_*`
 * entry points is the whole mechanism. Labels are what people type, not what the
 * Settings app titles the screen — "Wi-Fi", not "Network & internet".
 *
 * Every action here exists at minSdk 29; whether *this device* honours it is
 * [resolvedSettingsScreens]'s question.
 */
val SETTINGS_SCREENS = listOf(
    SettingsScreen("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, keywords = listOf("wifi")),
    SettingsScreen("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingsScreen("Battery", Intent.ACTION_POWER_USAGE_SUMMARY),
    SettingsScreen("Battery saver", Settings.ACTION_BATTERY_SAVER_SETTINGS),
    SettingsScreen("Display", Settings.ACTION_DISPLAY_SETTINGS),
    SettingsScreen("Sound", Settings.ACTION_SOUND_SETTINGS),
    SettingsScreen("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SettingsScreen("Date and time", Settings.ACTION_DATE_SETTINGS),
    SettingsScreen("Language", Settings.ACTION_LOCALE_SETTINGS),
    SettingsScreen("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SettingsScreen("Security", Settings.ACTION_SECURITY_SETTINGS),
    SettingsScreen("Apps", Settings.ACTION_APPLICATION_SETTINGS),
    // The SDK exports no constant for the top-level notifications screen; the
    // action string is public behaviour, and an OEM that lacks it just fails to
    // resolve and drops out.
    SettingsScreen("Notifications", "android.settings.NOTIFICATION_SETTINGS"),
    SettingsScreen("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingsScreen("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    SettingsScreen("Mobile data", Settings.ACTION_DATA_USAGE_SETTINGS),
    SettingsScreen("VPN", Settings.ACTION_VPN_SETTINGS),
)

/** A [SearchAction] id namespaced so it collides with no other action domain. */
fun SettingsScreen.searchAction() =
    SearchAction(id = "settings:$intentAction", label = label, keywords = keywords)

/**
 * The catalogue, kept to what this device resolves: a screen no activity answers
 * for never appears as a result, so selection cannot fail (#188).
 */
fun resolvedSettingsScreens(context: Context): List<SettingsScreen> =
    SETTINGS_SCREENS.filter {
        Intent(it.intentAction).resolveActivity(context.packageManager) != null
    }

/** Opens [screen]; a no-op if the device stopped resolving it since the query ran. */
fun openSettingsScreen(context: Context, screen: SettingsScreen) {
    val intent = Intent(screen.intentAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
}
