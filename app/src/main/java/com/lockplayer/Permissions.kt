package com.lockplayer

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings as SystemSettings

/** Status checks and deep links to the exact system settings page for each permission. */
object Permissions {

    fun notificationsGranted(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }

    fun notificationAccessGranted(c: Context) = Settings.hasNotificationAccess(c)

    fun overlayGranted(c: Context) = SystemSettings.canDrawOverlays(c)

    fun batteryUnrestricted(c: Context) =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    /**
     * Android 13+ blocks "restricted settings" (like notification access) for apps installed
     * from an APK until the user allows them in App info. Returns null when it can't tell.
     */
    fun restrictedSettingsBlocked(c: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 33) return false
        return runCatching {
            val mode = c.getSystemService(AppOpsManager::class.java)
                .unsafeCheckOpNoThrow("android:access_restricted_settings", Process.myUid(), c.packageName)
            mode != AppOpsManager.MODE_ALLOWED
        }.getOrNull()
    }

    /** App's own notification settings page. */
    fun notificationsIntent(c: Context) =
        Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(SystemSettings.EXTRA_APP_PACKAGE, c.packageName)

    /** LockPlayer's own entry in "notification access" (Android 11+), else the list. */
    fun notificationAccessIntent(c: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                SystemSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(c, YouTubeWatcherService::class.java).flattenToString(),
            )
        } else {
            Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    fun notificationAccessFallback() = Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun overlayIntent(c: Context) =
        Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(c))

    fun appInfoIntent(c: Context) =
        Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(c))

    /** System dialog "Let LockPlayer always run in the background?". */
    @android.annotation.SuppressLint("BatteryLife")
    fun batteryIntent(c: Context) =
        Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(c))

    fun batteryFallback() = Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun accessibilityGranted(c: Context) = Settings.hasAccessibility(c)

    /** LockPlayer's own page in Accessibility (works on most Android 11+ phones), else the list. */
    fun accessibilityIntent(c: Context): Intent =
        Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(c, FeedBadgeService::class.java))

    fun accessibilityFallback() = Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Needed for the badges on YouTube thumbnails; the rest is optional. */
    fun setupComplete(c: Context) = notificationsGranted(c) && accessibilityGranted(c)

    private fun packageUri(c: Context) = Uri.fromParts("package", c.packageName, null)
}
