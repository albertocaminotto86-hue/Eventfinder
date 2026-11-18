package com.lockplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live check that a resolved audio link can be downloaded with the same request shape
 * YouTubeDataSource uses (POST body, &rn= counter, client headers, Range header).
 * Set LIVE_STREAM_TEST=1 to run it; it needs network access to googlevideo.com.
 */
class StreamDownloadTest {

    @Test
    fun downloadsFirstChunkOfAudio() {
        assumeTrue(System.getenv("LIVE_STREAM_TEST") == "1")
        NewPipe.init(ExtractorDownloader.instance)
        val track = YouTubeResolver.resolve("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        val url = track.source.uri
        println("stream host=${URL(url).host} source=${track.source::class.simpleName}")

        val conn = URL("$url&rn=0").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent",
            if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) YoutubeParsingHelper.getVisionOsUserAgent(null)
            else ExtractorDownloader.USER_AGENT)
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            conn.setRequestProperty("Origin", "https://www.youtube.com")
            conn.setRequestProperty("Referer", "https://www.youtube.com")
        }
        conn.setRequestProperty("Range", "bytes=0-65535")
        conn.setRequestProperty("Accept-Encoding", "identity")
        conn.outputStream.use { it.write(byteArrayOf(0x78, 0)) }

        val code = conn.responseCode
        val bytes = (if (code < 400) conn.inputStream else conn.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        println("http=$code type=${conn.contentType} bytes=${bytes.size}")
        assertEquals(206, code)
        assertTrue(bytes.size > 1000)
        // m4a starts with an 'ftyp' box, webm with the EBML magic number.
        val head = bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII)
        assertTrue("unexpected file header", head == "ftyp" || bytes[0] == 0x1A.toByte())
    }
}
