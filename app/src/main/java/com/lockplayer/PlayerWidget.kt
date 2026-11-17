package com.lockplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Home-screen widget: current track plus previous / play-pause / next. */
@UnstableApi
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val views = buildViews(
            context,
            prefs.getString(KEY_TITLE, null),
            prefs.getString(KEY_ARTIST, null),
            playing = false,
            hasQueue = false, // the player is not running: buttons open the app
        )
        manager.updateAppWidget(ids, views)
    }

    companion object {
        private const val PREFS = "widget"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"

        fun updateAll(context: Context, player: Player) {
            val meta = player.mediaMetadata
            val title = meta.title?.toString()
            val artist = meta.artist?.toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_TITLE, title).putString(KEY_ARTIST, artist).apply()

            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(
                ids,
                buildViews(context, title, artist, player.playWhenReady, player.mediaItemCount > 0),
            )
        }

        private fun buildViews(
            context: Context,
            title: String?,
            artist: String?,
            playing: Boolean,
            hasQueue: Boolean,
        ) = RemoteViews(context.packageName, R.layout.widget_player).apply {
            setTextViewText(R.id.widget_title, title ?: context.getString(R.string.nothing_playing))
            setTextViewText(R.id.widget_artist, artist ?: context.getString(R.string.share_hint_short))
            setImageViewResource(
                R.id.widget_play,
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
            )
            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setOnClickPendingIntent(R.id.widget_info, openApp)
            if (!hasQueue) {
                setOnClickPendingIntent(R.id.widget_prev, openApp)
                setOnClickPendingIntent(R.id.widget_play, openApp)
                setOnClickPendingIntent(R.id.widget_next, openApp)
            } else {
                setOnClickPendingIntent(R.id.widget_prev, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, false))
                // Resuming playback must be allowed to start the service in the foreground.
                setOnClickPendingIntent(R.id.widget_play, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, !playing))
                setOnClickPendingIntent(R.id.widget_next, mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT, false))
            }
        }

        /** Same kind of intent Media3's own notification buttons send to the service. */
        private fun mediaKey(context: Context, keyCode: Int, foreground: Boolean): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setClass(context, PlaybackService::class.java)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (foreground) {
                PendingIntent.getForegroundService(context, keyCode + 1000, intent, flags)
            } else {
                PendingIntent.getService(context, keyCode, intent, flags)
            }
        }
    }
}
