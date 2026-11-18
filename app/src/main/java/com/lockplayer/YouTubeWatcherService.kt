package com.lockplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Watches the YouTube app's media session (this is why "notification access" is needed).
 * While a video plays it shows a floating [Bubble] over YouTube (or, without the
 * "display over other apps" permission, a one-time heads-up notification) offering to
 * continue it in LockPlayer with the screen locked.
 */
class YouTubeWatcherService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessions: MediaSessionManager? = null
    private val watched = mutableMapOf<SessionToken, Watch>()

    private val bubble by lazy { Bubble(this) }
    private var bubbleOwner: Watch? = null

    /** Last video we offered, so ignoring the offer doesn't make it come back. */
    private var lastOfferedKey: String? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers -> watch(controllers.orEmpty()) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ensureChannel()
        val manager = getSystemService(MediaSessionManager::class.java)
        sessions = manager
        val me = ComponentName(this, YouTubeWatcherService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, me, handler)
            watch(manager.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        sessions?.removeOnActiveSessionsChangedListener(sessionsListener)
        watched.values.forEach { it.controller.unregisterCallback(it) }
        watched.clear()
        bubble.hide()
        bubbleOwner = null
        super.onListenerDisconnected()
    }

    // We only care about media sessions, not the notifications themselves.
    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    private fun watch(controllers: List<MediaController>) {
        val youtube = controllers.filter { it.packageName in YOUTUBE_PACKAGES }
        val tokens = youtube.map { it.sessionToken }.toSet()
        watched.keys.filter { it !in tokens }.forEach {
            watched.remove(it)?.let { w -> w.controller.unregisterCallback(w); w.stop() }
        }
        youtube.filter { it.sessionToken !in watched }.forEach { c ->
            val w = Watch(c)
            watched[c.sessionToken] = w
            c.registerCallback(w, handler)
            w.check()
        }
    }

    private inner class Watch(val controller: MediaController) : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = check()
        override fun onMetadataChanged(metadata: MediaMetadata?) = check()

        fun check() {
            val video = currentVideo()
            if (video == null) {
                // Paused, stopped, too short or feature off: no bubble for this session.
                if (bubbleOwner == this) { bubble.hide(); bubbleOwner = null }
                return
            }
            if (android.provider.Settings.canDrawOverlays(this@YouTubeWatcherService)) {
                bubbleOwner = this
                bubble.show(video, controller.sessionToken)
            } else {
                // No overlay permission: fall back to the one-time notification.
                val key = "${video.title}|${video.channel}"
                if (key == lastOfferedKey) return
                lastOfferedKey = key
                offer(controller, video)
            }
        }

        fun stop() {
            if (bubbleOwner == this) { bubble.hide(); bubbleOwner = null }
        }

        private fun currentVideo(): NowPlayingOnYouTube? {
            if (!Settings.offerOnYouTubePlay(this@YouTubeWatcherService)) return null
            if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) return null
            val meta = controller.metadata ?: return null
            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
            val channel = meta.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            val durationSec = meta.getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000
            return NowPlayingOnYouTube(title, channel, durationSec)
        }
    }

    private fun offer(controller: MediaController, video: NowPlayingOnYouTube) {
        val intent = Intent(this, HandoffActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(HandoffActivity.EXTRA_TITLE, video.title)
            .putExtra(HandoffActivity.EXTRA_CHANNEL, video.channel)
            .putExtra(HandoffActivity.EXTRA_DURATION, video.durationSec)
            .putExtra(HandoffActivity.EXTRA_TOKEN, controller.sessionToken)
        val tap = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_mono)
            .setContentTitle(video.title)
            .setContentText(getString(R.string.offer_text))
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setTimeoutAfter(OFFER_TIMEOUT_MS)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.offer_action), tap).build()
            )
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.offer_channel), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = getString(R.string.offer_channel_desc)
                    setSound(null, null)
                    enableVibration(false)
                }
        )
    }

    companion object {
        val YOUTUBE_PACKAGES = setOf("com.google.android.youtube")
        private const val CHANNEL_ID = "youtube_offer"
        const val NOTIFICATION_ID = 42
        private const val OFFER_TIMEOUT_MS = 8_000L
    }
}

private typealias SessionToken = android.media.session.MediaSession.Token
