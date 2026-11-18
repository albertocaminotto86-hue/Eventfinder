package com.lockplayer

import android.content.Intent
import android.os.Bundle

/** Receives a link from YouTube's "Share" button (or an opened link) and plays or queues it. */
class ShareActivity : DeliverActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        val url = YouTubeResolver.extractYouTubeUrl(text)
        if (url == null) {
            toast(R.string.error_not_youtube, long = true)
            finish()
            return
        }
        toast(R.string.loading)
        deliver(url)
    }
}
