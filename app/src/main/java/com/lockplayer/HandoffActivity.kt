package com.lockplayer

import android.app.NotificationManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import kotlin.concurrent.thread

/**
 * Opened from the "Listen with the screen locked" notification: pauses the YouTube app,
 * finds the same video and continues it in LockPlayer from the same second.
 */
class HandoffActivity : DeliverActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java).cancel(YouTubeWatcherService.NOTIFICATION_ID)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: return finish()
        val video = NowPlayingOnYouTube(
            title = title,
            channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty(),
            durationSec = intent.getLongExtra(EXTRA_DURATION, 0),
        )
        val token = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_TOKEN, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_TOKEN)
        }

        // Pause YouTube right away and remember where it was.
        val startMs = token?.let { pauseAndGetPosition(it) }

        toast(R.string.loading)
        thread {
            val result = runCatching { YouTubeSearch.find(video) }
                .getOrElse {
                    Log.w(TAG, "Search failed", it)
                    YouTubeSearch.Result(null, "Errore: ${it.javaClass.simpleName}: ${it.message}")
                }
            Settings.setLastHandoff(this, result.trace)
            val url = result.url
            handler.post {
                if (url == null) {
                    toast(R.string.error_not_found, long = true)
                    finish()
                } else {
                    deliver(url, startMs, playNow = true)
                }
            }
        }
    }

    private fun pauseAndGetPosition(token: MediaSession.Token): Long? = runCatching {
        val yt = MediaController(this, token)
        val state = yt.playbackState
        var position = state?.position ?: return@runCatching null
        if (state.state == PlaybackState.STATE_PLAYING) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        yt.transportControls.pause()
        position.coerceAtLeast(0)
    }.getOrNull()

    companion object {
        private const val TAG = "Handoff"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_TOKEN = "token"
    }
}
