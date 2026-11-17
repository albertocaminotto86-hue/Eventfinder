package com.lockplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * Invisible screen that receives a link from YouTube's "Share" button, hands it to the
 * player and closes, so the user stays where they were.
 */
class ShareActivity : AppCompatActivity() {

    private var future: ListenableFuture<MediaController>? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        val url = YouTubeResolver.extractYouTubeUrl(text)
        if (url == null) {
            Toast.makeText(this, R.string.error_not_youtube, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show()
        val f = Controller.connect(this).also { future = it }
        f.addListener({
            val controller = runCatching { f.get() }.getOrNull() ?: return@addListener finish()
            val countBefore = controller.mediaItemCount
            val queued = Controller.playOrQueue(controller, url)
            // Stay alive (invisibly) until the service has accepted the item, then close.
            controller.addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    val count = controller.mediaItemCount
                    // The placeholder item has no title; the resolved one does.
                    if (count >= countBefore + 1 &&
                        controller.getMediaItemAt(count - 1).mediaMetadata.title != null
                    ) {
                        if (queued) {
                            Toast.makeText(this@ShareActivity, R.string.queued, Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                }
            })
            handler.postDelayed({ finish() }, 20_000)
        }, MoreExecutors.directExecutor())
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        future?.let(MediaController::releaseFuture)
        super.onDestroy()
    }
}
