package com.lockplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedItemParserTest {
    @Test
    fun parsesEnglishCard() {
        val v = FeedItemParser.parse(
            "Ricardo Villalobos - Live from Circoloco DC-10 Ibiza (20.07.26 Garden) - 1 hour, 16 minutes, 23 seconds - " +
                "Go to channel - CIRCOLOCO - 654K views - 2 months ago - play video"
        )!!
        assertEquals("Ricardo Villalobos - Live from Circoloco DC-10 Ibiza (20.07.26 Garden)", v.title)
        assertEquals("CIRCOLOCO", v.channel)
        assertEquals(4583L, v.durationSec)
    }

    @Test
    fun parsesItalianCard() {
        val v = FeedItemParser.parse(
            "Groove Armada | Live from Boomtown 2026 | Full Set - 1 ora, 34 minuti e 52 secondi - Vai al canale - " +
                "Boomtown Festival - 43.000 visualizzazioni - 5 giorni fa - riproduci video"
        )!!
        assertEquals("Groove Armada | Live from Boomtown 2026 | Full Set", v.title)
        assertEquals("Boomtown Festival", v.channel)
        assertEquals(5692L, v.durationSec)
    }

    @Test
    fun parsesClockDuration() {
        val v = FeedItemParser.parse("Titolo - 3:41 - Canale - 120K views - 1 year ago")!!
        assertEquals(221L, v.durationSec)
        assertEquals("Canale", v.channel)
    }

    @Test
    fun ignoresNonVideoDescriptions() {
        assertNull(FeedItemParser.parse("Search"))
        assertNull(FeedItemParser.parse("Notifications - 3 new"))
        assertNull(FeedItemParser.parse("Mix - 50 minutes - playlist"))
    }
}
