package com.lockplayer

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

class YouTubeSearchTest {
    /** Live network test against www.youtube.com: run with LIVE_YT_TEST=1. */
    @Test
    fun findsVideoFromYouTubeAppMetadata() {
        assumeTrue(System.getenv("LIVE_YT_TEST") == "1")
        NewPipe.init(ExtractorDownloader.instance)
        val result = YouTubeSearch.find(NowPlayingOnYouTube("Me at the zoo", "jawed", 19))
        println(result.trace)
        val url = result.url
        println("found=$url")
        assertEquals("jNQXAC9IVRw", url?.substringAfter("v=")?.take(11))
    }
}
