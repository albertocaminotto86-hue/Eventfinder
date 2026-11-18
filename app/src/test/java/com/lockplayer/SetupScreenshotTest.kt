package com.lockplayer

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings
import java.io.File

/** Renders the setup screen to PNGs for visual review. Run with SCREENSHOTS=1. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi", application = Application::class)
class SetupScreenshotTest {

    private fun capture(name: String) {
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        val root = controller.get().window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val out = File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("screenshot ${out.absolutePath}")
    }

    @Test
    fun nothingGranted() {
        assumeTrue(System.getenv("SCREENSHOTS") == "1")
        capture("setup-1-start")
    }

    @Test
    fun partlyGranted() {
        assumeTrue(System.getenv("SCREENSHOTS") == "1")
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowSettings.setCanDrawOverlays(true)
        capture("setup-2-partial")
    }

    @Test
    fun allGranted() {
        assumeTrue(System.getenv("SCREENSHOTS") == "1")
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowSettings.setCanDrawOverlays(true)
        SystemSettings.Secure.putString(
            app.contentResolver, "enabled_notification_listeners",
            ComponentName(app, YouTubeWatcherService::class.java).flattenToString(),
        )
        shadowOf(app.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(app.packageName, true)
        capture("setup-3-done")
    }
}
