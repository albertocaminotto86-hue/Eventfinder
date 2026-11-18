package com.lockplayer

import android.content.ComponentName
import android.content.Context
import android.provider.Settings as SystemSettings

object Settings {
    private const val PREFS = "settings"
    private const val KEY_OFFER = "offer_on_youtube_play"
    private const val KEY_LAST = "last_handoff"

    fun offerOnYouTubePlay(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OFFER, true)

    fun setOfferOnYouTubePlay(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_OFFER, value).apply()

    /** Trace of the last bubble/notification handoff, shown in the app for troubleshooting. */
    fun lastHandoff(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    fun setLastHandoff(context: Context, trace: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ITALY).format(java.util.Date())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST, "[$time]\n$trace").apply()
    }

    /** Whether the user granted "notification access", needed to see what YouTube is playing. */
    fun hasNotificationAccess(context: Context): Boolean {
        val me = ComponentName(context, YouTubeWatcherService::class.java).flattenToString()
        val enabled = SystemSettings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }
}
