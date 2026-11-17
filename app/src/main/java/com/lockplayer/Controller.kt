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

    /** Plays now if nothing is playing, otherwise appends to the queue. Returns true if queued. */
    fun playOrQueue(controller: MediaController, youTubeUrl: String): Boolean {
        val item = PlaybackService.request(youTubeUrl)
        val busy = controller.mediaItemCount > 0 && controller.playWhenReady
        controller.addMediaItem(item)
        if (!busy) {
            // Commands are applied in order, so this runs after the service has resolved the link.
            controller.seekTo(controller.mediaItemCount - 1, 0)
            controller.prepare()
            controller.play()
        }
        return busy
    }
}
