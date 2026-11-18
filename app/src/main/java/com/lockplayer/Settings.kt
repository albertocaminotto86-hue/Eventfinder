package com.lockplayer

import android.content.ComponentName
import android.content.Context
import android.provider.Settings as SystemSettings

object Settings {
    private const val PREFS = "settings"
    private const val KEY_OFFER = "offer_on_youtube_play"
    private const val KEY_LAST = "last_handoff"
    private const val KEY_BADGES = "feed_badges"
    private const val KEY_DUMP_REQ = "dump_requested"
    private const val KEY_DUMP = "dump"

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

    fun feedBadges(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BADGES, true)

    fun setFeedBadges(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BADGES, value).apply()

    /** Ask the accessibility service to record the next YouTube screen it sees. */
    fun requestDump(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DUMP_REQ, true).apply()

    fun consumeDumpRequest(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_DUMP_REQ, false)) return false
        prefs.edit().putBoolean(KEY_DUMP_REQ, false).apply()
        return true
    }

    fun saveDump(context: Context, dump: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DUMP, dump).apply()

    fun lastDump(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DUMP, null)

    fun hasAccessibility(context: Context): Boolean {
        val me = ComponentName(context, FeedBadgeService::class.java).flattenToString()
        val enabled = SystemSettings.Secure.getString(
            context.contentResolver, SystemSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
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
