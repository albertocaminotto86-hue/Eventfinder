package com.lockplayer

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * Base for the invisible screens that hand a YouTube link to the player and close,
 * so the user stays in whatever app they were using.
 */
abstract class DeliverActivity : AppCompatActivity() {

    private var future: ListenableFuture<MediaController>? = null
    protected val handler = Handler(Looper.getMainLooper())

    /**
     * @param startMs where to start, or null to start from the beginning.
     * @param playNow play immediately even if something else is playing (otherwise queue it).
     */
    protected fun deliver(url: String, startMs: Long? = null, playNow: Boolean = false) {
        val f = Controller.connect(this).also { future = it }
        f.addListener({
            val controller = runCatching { f.get() }.getOrNull() ?: return@addListener finish()
            val countBefore = controller.mediaItemCount
            val queued = Controller.playOrQueue(controller, url, startMs ?: 0, playNow)
            // Stay alive (invisibly) until the service has accepted the item, then close.
            controller.addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    val count = controller.mediaItemCount
                    // The placeholder item has no title; the resolved one does.
                    if (count >= countBefore + 1 &&
                        controller.getMediaItemAt(count - 1).mediaMetadata.title != null
                    ) {
                        if (queued) toast(R.string.queued)
                        finish()
                    }
                }
            })
            handler.postDelayed({ finish() }, 20_000)
        }, MoreExecutors.directExecutor())
    }

    protected fun toast(res: Int, long: Boolean = false) =
        Toast.makeText(this, res, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        future?.let(MediaController::releaseFuture)
        super.onDestroy()
    }
}
