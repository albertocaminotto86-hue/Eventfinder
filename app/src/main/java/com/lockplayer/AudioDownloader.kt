package com.lockplayer

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads a YouTube audio file in chunks, using the same request shape as playback
 * (POST body, &rn= counter, client headers). YouTube throttles or rejects single large
 * requests, so the file is fetched with ranges of [CHUNK] bytes.
 */
class AudioDownloader(private val client: OkHttpClient = defaultClient) {

    /** Writes the whole file to [out]; [onProgress] gets (bytesDone, totalBytes or -1). */
    fun download(url: String, knownLength: Long, out: OutputStream, onProgress: (Long, Long) -> Unit) {
        var total = knownLength
        var done = 0L
        var rn = 0
        while (total < 0 || done < total) {
            val end = if (total > 0) minOf(done + CHUNK, total) - 1 else done + CHUNK - 1
            val request = buildRequest(url, rn++, done, end)
            var attempt = 0
            while (true) {
                try {
                    client.newCall(request).execute().use { resp ->
                        if (resp.code != 200 && resp.code != 206) throw IOException("HTTP ${resp.code}")
                        if (total < 0) total = totalFrom(resp.header("Content-Range")) ?: -1
                        val body = resp.body ?: throw IOException("Risposta vuota")
                        val bytes = body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            var n: Int
                            var got = 0L
                            while (input.read(buf).also { n = it } > 0) {
                                out.write(buf, 0, n)
                                got += n
                                onProgress(done + got, total)
                            }
                            got
                        }
                        done += bytes
                        if (bytes == 0L) total = done // server has nothing more: finished
                    }
                    break
                } catch (e: IOException) {
                    if (++attempt >= 3) throw e
                    Thread.sleep(1000L * attempt)
                }
            }
            if (total < 0) break
        }
        out.flush()
    }

    private fun buildRequest(url: String, rn: Int, start: Long, end: Long): Request {
        val b = Request.Builder()
            .url("$url&rn=$rn")
            .post(byteArrayOf(0x78, 0).toRequestBody())
            .header("Range", "bytes=$start-$end")
            .header("Accept-Encoding", "identity")
            .header(
                "User-Agent",
                if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) YoutubeParsingHelper.getVisionOsUserAgent(null)
                else ExtractorDownloader.USER_AGENT,
            )
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            b.header("Origin", "https://www.youtube.com").header("Referer", "https://www.youtube.com")
        }
        return b.build()
    }

    companion object {
        const val CHUNK = 2L * 1024 * 1024

        private val defaultClient = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /** "bytes 0-2097151/4567890" → 4567890 */
        fun totalFrom(contentRange: String?): Long? =
            contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()

        /** File name safe on every filesystem. */
        fun safeName(title: String): String =
            title.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ").replace(Regex("\\s+"), " ").trim().take(120)
                .ifEmpty { "audio" }
    }
}
