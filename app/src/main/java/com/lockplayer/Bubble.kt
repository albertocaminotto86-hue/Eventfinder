package com.lockplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SystemSettings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Floating round button shown over the YouTube app while a video plays.
 * Tapping it hands the video to LockPlayer; it can be dragged and snaps to the screen edge.
 */
class Bubble(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences("bubble", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var root: LinearLayout? = null
    private var tip: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var target: Pair<NowPlayingOnYouTube, MediaSession.Token>? = null
    private var tipShownFor: String? = null

    private val sizePx = dp(56)
    private val marginPx = dp(8)

    val isShowing get() = root != null

    fun show(video: NowPlayingOnYouTube, token: MediaSession.Token) {
        target = video to token
        if (!SystemSettings.canDrawOverlays(context)) return
        if (root == null) attach()
        // Label only the first time a given video shows the bubble.
        val key = "${video.title}|${video.channel}"
        if (tipShownFor != key) {
            tipShownFor = key
            showTip()
        }
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        tip = null
        params = null
    }

    private fun attach() {
        val onRight = prefs.getBoolean(KEY_RIGHT, true)
        val bubble = FrameLayout(context).apply {
            // White ring around the round logo so it stands out on any thumbnail.
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            elevation = dp(6).toFloat()
            contentDescription = context.getString(R.string.offer_action)
            addView(
                ImageView(context).apply { setImageResource(R.drawable.ic_logo) },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
        }
        val label = TextView(context).apply {
            text = context.getString(R.string.offer_action)
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0xFFFFFFFF.toInt())
            }
            elevation = dp(4).toFloat()
            visibility = View.GONE
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4)) // room for the shadow
            val tipParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(6), 0, dp(6), 0) }
            // The label sits on the side facing the middle of the screen.
            if (onRight) {
                addView(label, tipParams); addView(bubble, LinearLayout.LayoutParams(sizePx, sizePx))
            } else {
                addView(bubble, LinearLayout.LayoutParams(sizePx, sizePx)); addView(label, tipParams)
            }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or if (onRight) Gravity.END else Gravity.START
            x = marginPx
            y = prefs.getInt(KEY_Y, screenHeight() / 2)
        }

        bubble.setOnTouchListener(DragListener())
        runCatching { wm.addView(layout, lp) }.onFailure { return }
        root = layout
        tip = label
        params = lp
    }

    private fun showTip() {
        val label = tip ?: return
        label.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ tip?.visibility = View.GONE }, TIP_MS)
    }

    private fun onTap() {
        val (video, token) = target ?: return
        hide()
        context.startActivity(
            Intent(context, HandoffActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(HandoffActivity.EXTRA_TITLE, video.title)
                .putExtra(HandoffActivity.EXTRA_CHANNEL, video.channel)
                .putExtra(HandoffActivity.EXTRA_DURATION, video.durationSec)
                .putExtra(HandoffActivity.EXTRA_TOKEN, token)
        )
    }

    private inner class DragListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startY = 0
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = params ?: return false
            val view = root ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX; downRawY = e.rawY; startY = lp.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downRawY
                    if (!dragging && (abs(dy) > touchSlop || abs(e.rawX - downRawX) > touchSlop)) {
                        dragging = true
                        tip?.visibility = View.GONE
                    }
                    if (dragging) {
                        lp.y = (startY + dy).toInt().coerceIn(0, screenHeight() - sizePx)
                        wm.updateViewLayout(view, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        v.performClick()
                        onTap()
                    } else {
                        val onRight = e.rawX > screenWidth() / 2
                        prefs.edit().putInt(KEY_Y, lp.y).putBoolean(KEY_RIGHT, onRight).apply()
                        val wasRight = (lp.gravity and Gravity.END) == Gravity.END
                        if (onRight != wasRight) {
                            // Rebuild on the other edge (also flips the label side).
                            val t = target
                            hide()
                            t?.let { attach() ; target = it }
                        }
                    }
                }
            }
            return true
        }
    }

    private fun screenHeight() = context.resources.displayMetrics.heightPixels
    private fun screenWidth() = context.resources.displayMetrics.widthPixels
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_Y = "y"
        private const val KEY_RIGHT = "right"
        private const val TIP_MS = 3_000L
    }
}
