package com.lockplayer

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

object Controller {
    fun connect(context: Context): ListenableFuture<MediaController> =
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).buildAsync()

    /**
     * Plays the link now if nothing is playing (or [playNow] is set), otherwise appends it
     * to the queue. Returns true if it was queued.
     */
    fun playOrQueue(
        controller: MediaController,
        youTubeUrl: String,
        startMs: Long = 0,
        playNow: Boolean = false,
    ): Boolean {
        val queue = !playNow && controller.mediaItemCount > 0 && controller.playWhenReady
        controller.addMediaItem(PlaybackService.request(youTubeUrl))
        if (!queue) {
            // Commands are applied in order, so this runs after the service has resolved the link.
            controller.seekTo(controller.mediaItemCount - 1, startMs)
            controller.prepare()
            controller.play()
        }
        return queue
    }
}
