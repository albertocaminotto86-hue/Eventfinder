package com.lockplayer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.lockplayer.databinding.ActivitySetupBinding
import com.lockplayer.databinding.ItemSetupStepBinding

/**
 * Guided setup: one card per permission with its status and a button that opens the exact
 * settings page. Statuses refresh when the user comes back from the system settings.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivitySetupBinding

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // After a second "no" Android stops showing the dialog: send the user to the page.
            if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                open(Permissions.notificationsIntent(this))
            }
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.stepNotifications.setup(1, R.string.step_notif_title, R.string.step_notif_body, R.string.step_notif_button) {
            if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else open(Permissions.notificationsIntent(this))
        }
        b.stepA11y.setup(2, R.string.step_a11y_title, R.string.step_a11y_body, R.string.step_a11y_button) {
            open(Permissions.accessibilityIntent(this), Permissions.accessibilityFallback())
        }
        b.stepA11y.secondary.setOnClickListener { open(Permissions.appInfoIntent(this)) }
        b.stepAccess.setup(4, R.string.step_access_title, R.string.step_access_body, R.string.step_access_button) {
            open(Permissions.notificationAccessIntent(this), Permissions.notificationAccessFallback())
        }
        b.stepAccess.secondary.setOnClickListener { open(Permissions.appInfoIntent(this)) }
        b.stepOverlay.setup(5, R.string.step_overlay_title, R.string.step_overlay_body, R.string.step_overlay_button) {
            open(Permissions.overlayIntent(this))
        }
        b.stepBattery.setup(3, R.string.step_battery_title, R.string.step_battery_body, R.string.step_battery_button) {
            open(Permissions.batteryIntent(this), Permissions.batteryFallback())
        }

        b.done.setOnClickListener { finish() }
        b.skip.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val notif = Permissions.notificationsGranted(this)
        val a11y = Permissions.accessibilityGranted(this)
        val access = Permissions.notificationAccessGranted(this)
        val overlay = Permissions.overlayGranted(this)
        val battery = Permissions.batteryUnrestricted(this)

        b.stepNotifications.state(notif)
        b.stepA11y.state(a11y)
        b.stepBattery.state(battery)
        b.stepAccess.state(access)
        b.stepOverlay.state(overlay)

        // "It's greyed out" help, only while Android may be blocking restricted settings.
        val blocked = Permissions.restrictedSettingsBlocked(this) != false
        b.stepA11y.helpBox.visibility = if (!a11y && blocked) View.VISIBLE else View.GONE
        b.stepAccess.helpBox.visibility = if (!access && blocked) View.VISIBLE else View.GONE

        val required = listOf(notif, a11y).count { it }
        b.progress.text = getString(R.string.setup_progress, required, 2)
        b.progressBar.max = 2
        b.progressBar.progress = required
        val complete = required == 2
        b.done.visibility = if (complete) View.VISIBLE else View.GONE
        b.skip.visibility = if (complete) View.GONE else View.VISIBLE
        b.subtitle.setText(if (complete) R.string.setup_subtitle_done else R.string.setup_subtitle)
    }

    private fun open(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            fallback?.let { runCatching { startActivity(it) } }
                ?: runCatching { startActivity(Permissions.appInfoIntent(this)) }
        } catch (e: SecurityException) {
            fallback?.let { runCatching { startActivity(it) } }
        }
    }

    private fun ItemSetupStepBinding.setup(n: Int, title: Int, body: Int, button: Int, onClick: () -> Unit) {
        number.text = n.toString()
        this.title.setText(title)
        this.body.setText(body)
        action.setText(button)
        action.setOnClickListener { onClick() }
    }

    private fun ItemSetupStepBinding.state(done: Boolean) {
        number.visibility = if (done) View.GONE else View.VISIBLE
        check.visibility = if (done) View.VISIBLE else View.GONE
        status.setText(if (done) R.string.step_done else R.string.step_todo)
        status.isActivated = done
        // Completed steps collapse to a single line.
        body.visibility = if (done) View.GONE else View.VISIBLE
        action.visibility = if (done) View.GONE else View.VISIBLE
        root.alpha = if (done) 0.75f else 1f
    }
}
