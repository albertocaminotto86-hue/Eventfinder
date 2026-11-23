package com.lockplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Saves the audio of a YouTube video to Music/LockPlayer, one download at a time,
 * with a progress notification. Keeps running if the user leaves the app.
 */
class DownloadService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pageUrl = intent?.getStringExtra(EXTRA_URL)
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        startInForeground(progress(title, 0, 0))
        if (pageUrl == null) {
            if (pending.get() == 0) stopIfIdle()
            return START_NOT_STICKY
        }
        pending.incrementAndGet()
        executor.execute {
            try {
                run(pageUrl, title)
            } finally {
                if (pending.decrementAndGet() == 0) stopIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    private fun run(pageUrl: String, fallbackTitle: String) {
        var shownTitle = fallbackTitle
        try {
            val audio = YouTubeResolver.resolveDownload(pageUrl)
            shownTitle = audio.title
            val name = AudioDownloader.safeName(audio.title) + "." + audio.extension
            val target = createTarget(name, audio)
            var lastPct = -1
            contentResolver.openOutputStream(target.uri)!!.use { out ->
                AudioDownloader().download(audio.url, audio.contentLength, out) { done, total ->
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    if (pct != lastPct) {
                        lastPct = pct
                        nm.notify(PROGRESS_ID, progress(audio.title, pct, total))
                    }
                }
            }
            target.publish()
            nm.notify(nextId(), finished(audio.title, target.uri, audio.mimeType))
        } catch (e: Exception) {
            Log.w(TAG, "Download failed", e)
            nm.notify(nextId(), failed(shownTitle))
        }
    }

    /** Where the file goes: MediaStore Music/LockPlayer on Android 10+, app music folder before. */
    private inner class Target(val uri: Uri, val publish: () -> Unit)

    private fun createTarget(name: String, audio: DownloadableAudio): Target {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, audio.mimeType)
                put(MediaStore.Audio.Media.TITLE, audio.title)
                put(MediaStore.Audio.Media.ARTIST, audio.artist)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LockPlayer")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Impossibile creare il file")
            return Target(uri) {
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
        }
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "LockPlayer").apply { mkdirs() }
        val file = File(dir, name)
        return Target(Uri.fromFile(file)) {}
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(PROGRESS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(PROGRESS_ID, n)
        }
    }

    private fun stopIfIdle() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun progress(title: String, pct: Int, total: Long) =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.download_progress))
            .setContentText(title)
            .setProgress(100, pct, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun finished(title: String, uri: Uri, mime: String): Notification {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.download_done))
            .setContentText(title)
            .setAutoCancel(true)
        if (uri.scheme != "content") return builder.build() // file:// links can't be shared with other apps
        val open = PendingIntent.getActivity(
            this, uri.hashCode(),
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return builder.setContentIntent(open).build()
    }

    private fun failed(title: String) =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(getString(R.string.download_failed))
            .setContentText(title)
            .setAutoCancel(true)
            .build()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL = "downloads"
        private const val PROGRESS_ID = 77
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private val ids = AtomicInteger(1000)
        private fun nextId() = ids.incrementAndGet()

        fun start(context: Context, pageUrl: String, title: String) {
            context.startForegroundService(
                Intent(context, DownloadService::class.java)
                    .putExtra(EXTRA_URL, pageUrl)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}
