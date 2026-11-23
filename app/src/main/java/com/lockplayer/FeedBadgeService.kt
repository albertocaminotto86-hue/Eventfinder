package com.lockplayer

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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
        /** Known when YouTube describes the card; null for cards found only by their shape. */
        var video: NowPlayingOnYouTube? = null
        val card = Rect()
        var attached = false
    }

    /** A card on screen: its bounds, and the video if YouTube described it. */
    private data class Card(val bounds: Rect, val video: NowPlayingOnYouTube?)

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WindowManager::class.java)
        Settings.saveBadgeStatus(this, getString(R.string.badge_status_connected))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (pkg !in YouTubeWatcherService.YOUTUBE_PACKAGES) {
            // Adding our own badges fires window events too: reacting to them would remove the
            // badges just drawn, in a loop. For any other app, hide only if YouTube really left
            // the front (not for the status bar, a toast or a keyboard).
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != packageName &&
                rootInActiveWindow?.packageName?.toString() !in YouTubeWatcherService.YOUTUBE_PACKAGES
            ) hideAll()
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) closeMenu()
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
        val cards = mutableListOf<Card>()
        listTop = screen.top
        val samples = mutableListOf<String>()
        collect(root, cards, samples, depth = 0, screen = screen)
        reportStatus(cards, samples, root)

        val size = dp(36)
        val margin = dp(8)
        var used = 0
        for ((r, video) in cards) {
            // Thumbnails are 16:9 and span the card width; the badge sits on its bottom-left.
            // If the described node is only the text row under the thumbnail, the thumbnail ends at its top.
            val thumbH = r.width() * 9 / 16
            // Bounds are clipped to the screen: a card cut at the bottom is not "only the text row",
            // and a card cut at the top has its thumbnail position unknown.
            val cutBottom = r.bottom >= screen.bottom - 1
            val cutTop = r.top <= listTop + 1
            val thumbBottom = when {
                r.height() >= thumbH && !cutTop -> r.top + thumbH
                r.height() >= thumbH -> continue
                video != null && !cutBottom -> r.top
                else -> continue
            }
            val x = r.left + margin
            val y = thumbBottom - margin - size
            if (y < screen.top + dp(90) || y + size > screen.bottom - dp(60)) continue // under the toolbars
            if (r.width() < dp(140)) continue
            val badge = badges.getOrNull(used) ?: newBadge().also { badges += it }
            badge.video = video
            badge.card.set(r)
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

    /**
     * Finds video cards. Two ways, because YouTube's new feed often exposes nothing but a bare,
     * undescribed view per card:
     *  - by description, when YouTube gives one ("Title - 3 minutes - Channel - 1K views …");
     *  - by shape: full-width, thumbnail-tall children of the "results" list.
     */
    private fun collect(
        node: AccessibilityNodeInfo,
        out: MutableList<Card>,
        samples: MutableList<String>,
        depth: Int,
        screen: Rect,
    ) {
        if (depth > 50) return
        val desc = (node.contentDescription ?: node.text)?.toString()
        if (desc != null && desc.length > 15 && samples.size < 6) samples += desc.take(140)
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (isSponsored(desc)) return
        val big = r.width() > screen.width() * 0.6 && r.height() > r.width() * 0.4
        val video = FeedItemParser.parse(desc) ?: if (big) FeedItemParser.parse(desc, requireViews = false) else null
        if (video != null && !r.isEmpty) {
            if (out.none { it.bounds.contains(r) }) out += Card(r, video)
            return // children of a card are parts of the same card
        }
        if (node.viewIdResourceName?.endsWith(":id/results") == true) {
            listTop = r.top
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val cr = Rect().also { child.getBoundsInScreen(it) }
                val childDesc = (child.contentDescription ?: child.text)?.toString()
                if (isSponsored(childDesc)) continue
                val described = FeedItemParser.parse(childDesc, requireViews = false)
                // Undescribed video cards are single bare views; shelves (Shorts, playlists…) of the
                // same size have children (their title and items) and are searched inside instead.
                val shaped = child.childCount == 0 &&
                    cr.width() >= screen.width() * 0.8 && cr.height() >= cr.width() * 0.7
                if ((described != null || shaped) && out.none { Rect.intersects(it.bounds, cr) }) {
                    out += Card(cr, described)
                } else {
                    collect(child, out, samples, depth + 1, screen)
                }
            }
            return
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out, samples, depth + 1, screen) }
    }

    /** Top edge of YouTube's video list on screen (below its toolbar), found during [collect]. */
    private var listTop = 0

    private fun isSponsored(desc: String?) =
        desc != null && Regex("^(sponsored|sponsorizzat|annuncio|ad\\b)", RegexOption.IGNORE_CASE).containsMatchIn(desc.trim())

    private var lastReport = 0L
    private var autoDumped = false

    /** Saves what the last scan saw, shown in the app; dumps the screen once if nothing matched. */
    private fun reportStatus(
        cards: List<Card>,
        samples: List<String>,
        root: AccessibilityNodeInfo,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastReport < 1500) return
        lastReport = now
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ITALY).format(java.util.Date(now))
        val sb = StringBuilder("Ultima lettura di YouTube: $time\nVideo riconosciuti: ${cards.size}\n")
        val described = cards.count { it.video != null }
        sb.append("  (con titolo: $described, solo forma: ${cards.size - described})\n")
        cards.mapNotNull { it.video }.take(2).forEach { sb.append("  ✓ ${it.title.take(60)} · ${it.durationSec}s\n") }
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
        view.setOnClickListener {
            val video = badge.video
            if (video != null) showMenu(video, null, badge.params.x, badge.params.y)
            else openCardThenAsk(Rect(badge.card))
        }
        return badge
    }

    private var menu: View? = null

    /** Small LockPlayer menu next to the tapped badge: play now, add to queue, download. */
    @android.annotation.SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showMenu(video: NowPlayingOnYouTube, token: MediaSession.Token?, badgeX: Int, badgeY: Int) {
        closeMenu()
        val themed = android.view.ContextThemeWrapper(this, R.style.Theme_LockPlayer)
        val view = android.view.LayoutInflater.from(themed).inflate(R.layout.overlay_menu, null)
        view.findViewById<android.widget.TextView>(R.id.menuTitle).text = video.title
        view.findViewById<View>(R.id.menuPlay).setOnClickListener { closeMenu(); handoff(video, token, HandoffActivity.MODE_PLAY) }
        view.findViewById<View>(R.id.menuQueue).setOnClickListener { closeMenu(); handoff(video, null, HandoffActivity.MODE_QUEUE) }
        view.findViewById<View>(R.id.menuDownload).setOnClickListener { closeMenu(); handoff(video, null, HandoffActivity.MODE_DOWNLOAD) }
        view.setOnTouchListener { _, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_OUTSIDE) { closeMenu(); true } else false
        }

        val screen = resources.displayMetrics
        val width = dp(260)
        val height = dp(230)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = badgeX.coerceAtMost(screen.widthPixels - width - dp(8))
            // Open above the badge when there's room, otherwise below it.
            y = if (badgeY - height - dp(8) > dp(80)) badgeY - height - dp(8) else badgeY + dp(44)
        }
        if (runCatching { wm.addView(view, params) }.isSuccess) menu = view
    }

    private fun closeMenu() {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
    }

    private fun handoff(video: NowPlayingOnYouTube, token: MediaSession.Token?, mode: String) {
        if (mode != HandoffActivity.MODE_QUEUE) hideAll()
        startActivity(
            Intent(this, HandoffActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(HandoffActivity.EXTRA_TITLE, video.title)
                .putExtra(HandoffActivity.EXTRA_CHANNEL, video.channel)
                .putExtra(HandoffActivity.EXTRA_DURATION, video.durationSec)
                .putExtra(HandoffActivity.EXTRA_MODE, mode)
                .apply { if (token != null) putExtra(HandoffActivity.EXTRA_TOKEN, token) }
        )
    }

    // ---- Cards YouTube doesn't describe: open the video, then read what started playing ----

    private var askUntil = 0L

    /**
     * YouTube doesn't say which video an undescribed card is. So: tap the card for the user
     * (YouTube opens the video), then read the title/channel/duration YouTube publishes in its
     * media session (needs notification access) and show the LockPlayer menu for it.
     */
    private fun openCardThenAsk(card: Rect) {
        hideAll()
        if (!Settings.hasNotificationAccess(this)) {
            android.widget.Toast.makeText(this, R.string.need_notification_access, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (!clickCardNode(card)) tapAt(card.centerX(), card.top + card.width() * 9 / 32)
        askUntil = System.currentTimeMillis() + ASK_TIMEOUT_MS
        // Give YouTube time to switch from the feed's muted preview to the opened video.
        handler.postDelayed(::pollOpenedVideo, 1200)
    }

    private fun clickCardNode(card: Rect): Boolean {
        val root = rootInActiveWindow ?: return false
        fun find(n: AccessibilityNodeInfo, d: Int): AccessibilityNodeInfo? {
            if (d > 50) return null
            val r = Rect().also { n.getBoundsInScreen(it) }
            if (r == card) return n
            if (!Rect.intersects(r, card)) return null
            for (i in 0 until n.childCount) n.getChild(i)?.let { c -> find(c, d + 1)?.let { return it } }
            return null
        }
        var node = find(root, 0)
        while (node != null && !node.isClickable) node = node.parent
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun tapAt(x: Int, y: Int) {
        val path = android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 60)
        dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun pollOpenedVideo() {
        if (System.currentTimeMillis() > askUntil) {
            android.widget.Toast.makeText(this, R.string.error_not_found, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val controller = runCatching {
            getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(this, YouTubeWatcherService::class.java))
                .firstOrNull { it.packageName in YouTubeWatcherService.YOUTUBE_PACKAGES }
        }.getOrNull()
        val meta = controller?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (controller == null || title.isNullOrBlank() ||
            controller.playbackState?.state != PlaybackState.STATE_PLAYING
        ) {
            handler.postDelayed(::pollOpenedVideo, 400)
            return
        }
        val video = NowPlayingOnYouTube(
            title,
            meta.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            meta.getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000,
        )
        val dm = resources.displayMetrics
        // On the video page: show the menu low in the middle, under the player.
        showMenu(video, controller.sessionToken, (dm.widthPixels - dp(260)) / 2, dm.heightPixels / 2)
    }

    private fun detach(b: Badge) {
        if (b.attached) runCatching { wm.removeView(b.view) }
        b.attached = false
    }

    private fun hideAll() {
        closeMenu()
        badges.forEach { detach(it) }
    }

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
        private const val ASK_TIMEOUT_MS = 12_000L
    }
}
