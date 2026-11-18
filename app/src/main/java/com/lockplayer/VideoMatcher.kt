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

    fun pickBest(target: NowPlayingOnYouTube, candidates: List<Candidate>): Candidate? =
        candidates
            .map { it to score(target, it) }
            .filter { it.second >= MIN_SCORE }
            .maxByOrNull { it.second }
            ?.first

    fun score(target: NowPlayingOnYouTube, c: Candidate): Int {
        var score = 0
        val sameTitle = normalize(c.title) == normalize(target.title)
        if (sameTitle) score += 4
        else if (normalize(c.title).contains(normalize(target.title)) ||
            normalize(target.title).contains(normalize(c.title))
        ) score += 2

        if (target.channel.isNotBlank() && normalize(c.channel) == normalize(target.channel)) score += 3

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

    /** Needs at least a matching title plus one more signal (channel or duration). */
    private const val MIN_SCORE = 5

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
