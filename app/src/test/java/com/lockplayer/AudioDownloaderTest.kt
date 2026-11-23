package com.lockplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioDownloaderTest {
    @Test
    fun readsTotalFromContentRange() {
        assertEquals(4567890L, AudioDownloader.totalFrom("bytes 0-2097151/4567890"))
        assertNull(AudioDownloader.totalFrom("bytes 0-10/*"))
        assertNull(AudioDownloader.totalFrom(null))
    }

    @Test
    fun makesSafeFileNames() {
        assertEquals("AC DC - Live 1991 Part 1", AudioDownloader.safeName("AC/DC - Live: 1991 | Part 1").replace("  ", " ").replace(" |", ""))
        assertEquals("audio", AudioDownloader.safeName("///"))
        assertEquals("Città vuota", AudioDownloader.safeName("Città vuota"))
    }
}
