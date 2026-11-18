package com.lockplayer

import java.text.Normalizer
import kotlin.math.abs

/** What YouTube's media session tells us about the video it is playing. */
data class NowPlayingOnYouTube(
    val title: String,
    val channel: String,
    val durationSec: Long,
)

data class Candidate(
    val url: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
)

/** Picks the search result that is most likely the video playing in the YouTube app. */
object VideoMatcher {

    fun pickBest(target: NowPlayingOnYouTube, candidates: List<Candidate>): Candidate? {
        val min = minScore(target)
        return candidates
            .map { it to score(target, it) }
            .filter { it.second >= min }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Title match plus one more signal; just the title when YouTube gave nothing else. */
    fun minScore(target: NowPlayingOnYouTube): Int =
        if (target.channel.isBlank() && target.durationSec <= 0) 3 else 5

    fun score(target: NowPlayingOnYouTube, c: Candidate): Int {
        var score = 0
        val a = normalize(target.title)
        val b = normalize(c.title)
        val sim = jaccard(a, b)
        score += when {
            a == b -> 4
            sim >= 0.7 || (a.length >= 8 && (a.contains(b) || b.contains(a))) -> 3
            sim >= 0.4 -> 1
            else -> 0
        }

        val ch = normalize(target.channel)
        val cc = normalize(c.channel)
        if (ch.isNotEmpty() && cc.isNotEmpty() && (ch == cc || ch.contains(cc) || cc.contains(ch))) score += 3

        if (target.durationSec > 0 && c.durationSec > 0) {
            val diff = abs(target.durationSec - c.durationSec)
            score += when {
                diff <= 2 -> 3
                diff <= 10 -> 1
                else -> -3
            }
        }
        return score
    }

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun jaccard(a: String, b: String): Double {
        val x = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val y = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return (x intersect y).size.toDouble() / (x union y).size
    }
}
