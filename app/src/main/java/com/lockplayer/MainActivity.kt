package com.lockplayer

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lockplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val queueAdapter = QueueAdapter()
    private var userSeeking = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = render()
    }

    private val ticker = object : Runnable {
        override fun run() {
            renderProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // First launch: guided setup.
        if (savedInstanceState == null && !Permissions.setupComplete(this) &&
            !getSharedPreferences("settings", MODE_PRIVATE).getBoolean("setup_seen", false)
        ) {
            getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("setup_seen", true).apply()
            startActivity(Intent(this, SetupActivity::class.java))
        }

        b.queue.layoutManager = LinearLayoutManager(this)
        b.queue.adapter = queueAdapter

        b.play.setOnClickListener {
            controller?.run {
                if (isPlaying) pause() else {
                    if (playbackState == Player.STATE_IDLE) prepare()
                    play()
                }
            }
        }
        b.next.setOnClickListener { controller?.seekToNextMediaItem() }
        b.prev.setOnClickListener { controller?.seekToPrevious() }
        b.add.setOnClickListener { addFromField() }
        b.download.setOnClickListener { downloadCurrent() }
        b.paste.setOnClickListener { pasteFromClipboard() }
        b.openYoutube.setOnClickListener { openYouTube() }
        b.offerSwitch.isChecked = Settings.offerOnYouTubePlay(this)
        b.offerSwitch.setOnCheckedChangeListener { _, checked ->
            Settings.setOfferOnYouTubePlay(this, checked)
            renderAccess()
        }
        b.badgesSwitch.isChecked = Settings.feedBadges(this)
        b.badgesSwitch.setOnCheckedChangeListener { _, checked ->
            Settings.setFeedBadges(this, checked)
            renderAccess()
        }
        b.dump.setOnClickListener {
            Settings.requestDump(this)
            Toast.makeText(this, R.string.dump_hint, Toast.LENGTH_LONG).show()
            openYouTube()
        }
        b.dumpShare.setOnClickListener {
            val dump = Settings.lastDump(this)
            if (dump == null) {
                Toast.makeText(this, R.string.dump_none, Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, dump),
                    getString(R.string.dump_share),
                ))
            }
        }
        b.openSetup.setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }

        b.seek.addOnSliderTouchListener(object :
            com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                userSeeking = true
            }
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                userSeeking = false
                controller?.seekTo(slider.value.toLong())
            }
        })
    }

    override fun onResume() {
        super.onResume()
        renderAccess() // the user may be coming back from the system settings
    }

    private fun renderAccess() {
        val needsSetup = b.badgesSwitch.isChecked && !Permissions.setupComplete(this)
        b.setupBanner.visibility = if (needsSetup) View.VISIBLE else View.GONE
        val badge = when {
            !b.badgesSwitch.isChecked -> null
            !Settings.hasAccessibility(this) -> getString(R.string.badge_status_off)
            else -> Settings.badgeStatus(this) ?: getString(R.string.badge_status_never)
        }
        val trace = Settings.lastHandoff(this)
        val text = listOfNotNull(
            badge?.let { getString(R.string.badge_status_title) + "\n" + it },
            trace?.let { getString(R.string.diagnostics_title) + "\n" + it },
        ).joinToString("\n\n")
        b.diagnostics.text = text
        b.diagnostics.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun onStart() {
        super.onStart()
        val f = Controller.connect(this).also { future = it }
        f.addListener({
            controller = runCatching { f.get() }.getOrNull()?.also {
                it.addListener(listener)
                render()
            }
        }, MoreExecutors.directExecutor())
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        controller?.removeListener(listener)
        controller = null
        future?.let(MediaController::releaseFuture)
        super.onStop()
    }

    private fun addFromField() {
        val url = YouTubeResolver.extractYouTubeUrl(b.link.text?.toString())
        if (url == null) {
            Toast.makeText(this, R.string.error_not_youtube, Toast.LENGTH_SHORT).show()
            return
        }
        val c = controller ?: return
        b.link.text = null
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show()
        if (Controller.playOrQueue(c, url)) {
            Toast.makeText(this, R.string.queued, Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadCurrent() {
        val item = controller?.currentMediaItem ?: return
        val track = TrackStore.get(item.mediaId) ?: return
        DownloadService.start(this, track.pageUrl, track.title)
        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
    }

    private fun pasteFromClipboard() {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
        b.link.setText(text)
    }

    private fun openYouTube() {
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"))
        startActivity(intent)
    }

    private fun render() {
        val c = controller ?: return
        val meta = c.mediaMetadata
        val hasItem = c.mediaItemCount > 0
        b.empty.visibility = if (hasItem) View.GONE else View.VISIBLE
        b.nowPlaying.visibility = if (hasItem) View.VISIBLE else View.GONE
        b.title.text = meta.title ?: getString(R.string.loading)
        b.artist.text = meta.artist ?: ""
        b.artwork.load(meta.artworkUri) { crossfade(true) }
        b.play.setImageResource(if (c.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play)
        b.buffering.visibility =
            if (c.playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.INVISIBLE
        queueAdapter.submit((0 until c.mediaItemCount).map(c::getMediaItemAt), c.currentMediaItemIndex)
        renderProgress()
    }

    private fun renderProgress() {
        val c = controller ?: return
        val duration = c.duration.takeIf { it > 0 } ?: return
        val pos = c.currentPosition.coerceIn(0, duration)
        b.seek.valueTo = duration.toFloat()
        if (!userSeeking) b.seek.value = pos.toFloat()
        b.position.text = DateUtils.formatElapsedTime(pos / 1000)
        b.duration.text = DateUtils.formatElapsedTime(duration / 1000)
    }

    private inner class QueueAdapter : RecyclerView.Adapter<QueueAdapter.Holder>() {
        private var items: List<MediaItem> = emptyList()
        private var current = -1

        fun submit(newItems: List<MediaItem>, currentIndex: Int) {
            items = newItems
            current = currentIndex
            @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.item_title)
            val artist: TextView = view.findViewById(R.id.item_artist)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_queue, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val meta = items[position].mediaMetadata
            holder.title.text = meta.title ?: getString(R.string.loading)
            holder.artist.text = meta.artist ?: ""
            holder.itemView.isActivated = position == current
            holder.title.alpha = if (position == current) 1f else 0.75f
            holder.itemView.setOnClickListener {
                controller?.run { seekTo(position, 0); prepare(); play() }
            }
            holder.itemView.setOnLongClickListener {
                controller?.removeMediaItem(position)
                Toast.makeText(this@MainActivity, R.string.removed, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }
}
