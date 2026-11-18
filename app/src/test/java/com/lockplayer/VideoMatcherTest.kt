package com.lockplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoMatcherTest {

    private val target = NowPlayingOnYouTube("Bohemian Rhapsody (Official Video Remastered)", "Queen Official", 359)

    @Test
    fun picksSameTitleChannelAndDuration() {
        val best = VideoMatcher.pickBest(
            target,
            listOf(
                Candidate("u/cover", "Bohemian Rhapsody (Official Video Remastered)", "Some Cover Band", 412),
                Candidate("u/live", "Queen - Bohemian Rhapsody (Live Aid 1985)", "Queen Official", 360),
                Candidate("u/real", "Bohemian Rhapsody (Official Video Remastered)", "Queen Official", 359),
            ),
        )
        assertEquals("u/real", best?.url)
    }

    @Test
    fun ignoresAccentsCaseAndPunctuation() {
        val t = NowPlayingOnYouTube("Città vuota – Mina", "Mina Official", 180)
        val best = VideoMatcher.pickBest(t, listOf(Candidate("u/1", "CITTA VUOTA - MINA", "mina official", 181)))
        assertEquals("u/1", best?.url)
    }

    @Test
    fun rejectsWhenOnlyTitleMatchesButDurationIsFarOff() {
        val best = VideoMatcher.pickBest(
            target,
            listOf(Candidate("u/x", "Bohemian Rhapsody (Official Video Remastered)", "Other", 900)),
        )
        assertNull(best)
    }

    @Test
    fun acceptsTitleAndDurationWhenChannelNameDiffers() {
        val best = VideoMatcher.pickBest(
            target,
            listOf(Candidate("u/y", "Bohemian Rhapsody (Official Video Remastered)", "Queen", 359)),
        )
        assertEquals("u/y", best?.url)
    }

    @Test
    fun toleratesTruncatedOrSlightlyDifferentTitles() {
        val t = NowPlayingOnYouTube("Lezione di cucina: pasta fresca fatta in casa", "Chef Rossi", 0)
        val best = VideoMatcher.pickBest(
            t,
            listOf(
                Candidate("u/other", "Pane fatto in casa", "Chef Rossi", 600),
                Candidate("u/ok", "Lezione di cucina: pasta fresca fatta in casa | Episodio 3", "Chef Rossi", 845),
            ),
        )
        assertEquals("u/ok", best?.url)
    }

    @Test
    fun titleOnlyIsEnoughWhenYouTubeGivesNothingElse() {
        val t = NowPlayingOnYouTube("Un titolo molto particolare", "", 0)
        assertEquals("u/1", VideoMatcher.pickBest(t, listOf(Candidate("u/1", "Un titolo molto particolare", "X", 100)))?.url)
    }
}
