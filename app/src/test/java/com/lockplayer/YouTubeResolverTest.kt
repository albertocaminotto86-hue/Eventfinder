package com.lockplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import java.net.InetSocketAddress
import java.net.Socket

class YouTubeResolverTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun init() = NewPipe.init(ExtractorDownloader.instance)
    }

    @Test
    fun extractsLinkFromSharedText() {
        assertEquals(
            "https://youtu.be/jNQXAC9IVRw?si=abc",
            YouTubeResolver.extractYouTubeUrl("Guarda questo video https://youtu.be/jNQXAC9IVRw?si=abc"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            YouTubeResolver.extractYouTubeUrl("https://www.youtube.com/watch?v=jNQXAC9IVRw"),
        )
        assertNull(YouTubeResolver.extractYouTubeUrl("https://example.com/video"))
        assertNull(YouTubeResolver.extractYouTubeUrl("nessun link"))
    }

    /** Live network test: needs access to www.youtube.com (skipped when offline). */
    @Test
    fun resolvesRealVideoToAudioSource() {
        val online = runCatching {
            Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 5000) }
        }.isSuccess || System.getenv("HTTPS_PROXY") != null
        assumeTrue(online)

        val track = YouTubeResolver.resolve("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        println("title=${track.title} artist=${track.artist} duration=${track.durationSec}")
        println("source=${track.source::class.simpleName} host=${java.net.URI(track.source.uri).host}")
        assertEquals("Me at the zoo", track.title)
        assertTrue(track.durationSec in 15..25)
        assertTrue(track.source.uri.startsWith("https://"))
        if (track.source is Source.Dash) {
            assertTrue((track.source as Source.Dash).manifest.contains("<MPD"))
        }
    }
}
