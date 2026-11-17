package com.lockplayer

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Keeps playing with the screen locked. Media3 publishes the media session, which gives
 * the lock-screen / notification controls, Bluetooth and car buttons for free.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val retried = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(YouTubeMediaSourceFactory())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)   // keep CPU + Wi-Fi awake with the screen off
            .build()

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                PlayerWidget.updateAll(this@PlaybackService, player)
            }

            override fun onPlayerError(error: PlaybackException) = recover(player, error)
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(Callback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Stream links expire after a few hours: on a load error, fetch a fresh link once and retry. */
    private fun recover(player: Player, error: PlaybackException) {
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        val track = TrackStore.get(item.mediaId) ?: return
        if (!retried.add(item.mediaId)) {
            Log.w(TAG, "Playback failed again, skipping", error)
            toast(getString(R.string.error_playback))
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
            return
        }
        scope.launch {
            val fresh = runCatching {
                withContext(Dispatchers.IO) { YouTubeResolver.resolve(track.pageUrl) }
            }.getOrElse {
                Log.w(TAG, "Re-resolve failed", it)
                toast(getString(R.string.error_playback))
                return@launch
            }
            TrackStore.put(item.mediaId, fresh)
            val position = player.currentPosition
            player.replaceMediaItem(index, item.buildUpon().setUri(Uri.parse(fresh.source.uri)).build())
            player.seekTo(index, position)
            player.prepare()
            player.play()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private inner class Callback : MediaSession.Callback {
        /**
         * Controllers (our UI, the widget) add items whose mediaId is just a YouTube URL.
         * Here they are turned into playable items before reaching the player.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = scope.future {
            mediaItems.mapNotNull { requested ->
                val url = requested.requestMetadata.mediaUri?.toString() ?: requested.mediaId
                if (TrackStore.get(requested.mediaId) != null) return@mapNotNull requested
                runCatching { withContext(Dispatchers.IO) { YouTubeResolver.resolve(url) } }
                    .onFailure {
                        Log.w(TAG, "Could not resolve $url", it)
                        toast(getString(R.string.error_resolve))
                    }
                    .getOrNull()
                    ?.let(::toMediaItem)
            }
        }
    }

    private fun toMediaItem(track: ResolvedTrack): MediaItem {
        val id = UUID.randomUUID().toString()
        TrackStore.put(id, track)
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(track.source.uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.artworkUrl?.let(Uri::parse))
                    .setDurationMs(track.durationSec * 1000)
                    .setExtras(Bundle().apply { putString(EXTRA_PAGE_URL, track.pageUrl) })
                    .build()
            )
            .build()
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val EXTRA_PAGE_URL = "page_url"

        /** A queue request that the service resolves in [Callback.onAddMediaItems]. */
        fun request(youTubeUrl: String): MediaItem = MediaItem.Builder()
            .setMediaId(youTubeUrl)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(youTubeUrl)).build()
            )
            .build()
    }
}
