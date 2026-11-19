package com.lockplayer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Draws the LockPlayer badge on the bottom-left corner of every video thumbnail in the
 * YouTube app's lists. Tapping a badge plays that video in LockPlayer.
 *
 * Uses the accessibility API only to read where the video cards are and their
 * descriptions (title, channel, duration), and only inside the YouTube app.
 */
class FeedBadgeService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private val badges = mutableListOf<Badge>()
    private var scanPending = false

    private class Badge(val view: FrameLayout, val params: WindowManager.LayoutParams) {
        var video: NowPlayingOnYouTube? = null
        var attached = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WindowManager::class.java)
        Settings.saveBadgeStatus(this, getString(R.string.badge_status_connected))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() !in YouTubeWatcherService.YOUTUBE_PACKAGES) {
            // Another app (or the launcher) is in front: hide everything.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) hideAll()
            return
        }
        // Coalesce bursts of scroll/content events into one scan per frame or so.
        if (!scanPending) {
            scanPending = true
            handler.postDelayed({ scanPending = false; scan() }, 60)
        }
    }

    override fun onInterrupt() = hideAll()

    override fun onDestroy() {
        hideAll()
        super.onDestroy()
    }

    private fun scan() {
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() !in YouTubeWatcherService.YOUTUBE_PACKAGES ||
            !Settings.feedBadges(this)
        ) {
            hideAll(); return
        }
        if (Settings.consumeDumpRequest(this)) Settings.saveDump(this, dump(root))

        val screen = Rect().also { root.getBoundsInScreen(it) }
        val cards = mutableListOf<Pair<Rect, NowPlayingOnYouTube>>()
        val samples = mutableListOf<String>()
        collect(root, cards, samples, depth = 0, screenWidth = screen.width())
        reportStatus(cards, samples, root)

        val size = dp(36)
        val margin = dp(8)
        var used = 0
        for ((r, video) in cards) {
            // Thumbnails are 16:9 and span the card width; the badge sits on its bottom-left.
            // If the described node is only the text row under the thumbnail, the thumbnail ends at its top.
            val thumbH = r.width() * 9 / 16
            val thumbBottom = if (r.height() >= thumbH) r.top + thumbH else r.top
            val x = r.left + margin
            val y = thumbBottom - margin - size
            if (y < screen.top + dp(90) || y + size > screen.bottom - dp(60)) continue // under the toolbars
            if (r.width() < dp(140)) continue
            val badge = badges.getOrNull(used) ?: newBadge().also { badges += it }
            badge.video = video
            badge.params.x = x
            badge.params.y = y
            if (badge.attached) {
                runCatching { wm.updateViewLayout(badge.view, badge.params) }
            } else {
                badge.attached = runCatching { wm.addView(badge.view, badge.params) }.isSuccess
            }
            used++
            if (used >= MAX_BADGES) break
        }
        badges.drop(used).forEach { detach(it) }
    }

    /** Finds nodes whose description looks like a YouTube video card. */
    private fun collect(
        node: AccessibilityNodeInfo,
        out: MutableList<Pair<Rect, NowPlayingOnYouTube>>,
        samples: MutableList<String>,
        depth: Int,
        screenWidth: Int,
    ) {
        if (depth > 50) return
        val desc = (node.contentDescription ?: node.text)?.toString()
        if (desc != null && desc.length > 15 && samples.size < 6) samples += desc.take(140)
        val r = Rect().also { node.getBoundsInScreen(it) }
        // Big, thumbnail-sized nodes are almost surely video cards: accept them even without a view count.
        val big = r.width() > screenWidth * 0.6 && r.height() > r.width() * 0.4
        val video = FeedItemParser.parse(desc) ?: if (big) FeedItemParser.parse(desc, requireViews = false) else null
        if (video != null && !r.isEmpty) {
            if (out.none { it.first.contains(r) }) out += r to video
            return // children of a card are parts of the same card
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out, samples, depth + 1, screenWidth) }
    }

    private var lastReport = 0L
    private var autoDumped = false

    /** Saves what the last scan saw, shown in the app; dumps the screen once if nothing matched. */
    private fun reportStatus(
        cards: List<Pair<Rect, NowPlayingOnYouTube>>,
        samples: List<String>,
        root: AccessibilityNodeInfo,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastReport < 1500) return
        lastReport = now
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ITALY).format(java.util.Date(now))
        val sb = StringBuilder("Ultima lettura di YouTube: $time\nVideo riconosciuti: ${cards.size}\n")
        cards.take(2).forEach { sb.append("  ✓ ${it.second.title.take(60)} · ${it.second.durationSec}s\n") }
        if (cards.isEmpty()) {
            sb.append("Testi visti (esempi):\n")
            samples.forEach { sb.append("  · ").append(it).append('\n') }
            if (!autoDumped) {
                autoDumped = true
                Settings.saveDump(this, dump(root))
            }
        }
        Settings.saveBadgeStatus(this, sb.toString())
    }

    private fun newBadge(): Badge {
        val view = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()) }
            setPadding(dp(2), dp(2), dp(2), dp(2))
            elevation = dp(4).toFloat()
            contentDescription = getString(R.string.offer_action)
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_logo) },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val params = WindowManager.LayoutParams(
            dp(36), dp(36),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val badge = Badge(view, params)
        view.setOnClickListener { badge.video?.let(::play) }
        return badge
    }

    private fun play(video: NowPlayingOnYouTube) {
        hideAll()
        startActivity(
            Intent(this, HandoffActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(HandoffActivity.EXTRA_TITLE, video.title)
                .putExtra(HandoffActivity.EXTRA_CHANNEL, video.channel)
                .putExtra(HandoffActivity.EXTRA_DURATION, video.durationSec)
        )
    }

    private fun detach(b: Badge) {
        if (b.attached) runCatching { wm.removeView(b.view) }
        b.attached = false
    }

    private fun hideAll() = badges.forEach { detach(it) }

    /** Text dump of the YouTube screen structure, for troubleshooting badge placement. */
    private fun dump(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            if (d > 25 || sb.length > 60_000) return
            val r = Rect().also { n.getBoundsInScreen(it) }
            val cls = n.className?.toString()?.substringAfterLast('.') ?: "?"
            val desc = n.contentDescription?.toString()?.take(160)
            val text = n.text?.toString()?.take(80)
            if (desc != null || text != null || n.isClickable || d < 4) {
                sb.append("  ".repeat(d.coerceAtMost(12))).append(cls)
                    .append(" [").append(r.left).append(',').append(r.top).append(' ')
                    .append(r.width()).append('x').append(r.height()).append(']')
                if (n.isClickable) sb.append(" click")
                if (desc != null) sb.append(" d=\"").append(desc).append('"')
                if (text != null) sb.append(" t=\"").append(text).append('"')
                sb.append('\n')
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, d + 1) }
        }
        walk(root, 0)
        return sb.toString()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_BADGES = 8
    }
}
