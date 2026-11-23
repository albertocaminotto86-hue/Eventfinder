package com.lockplayer

/**
 * Turns the accessibility description YouTube gives each video in its lists into
 * title / channel / duration, e.g. (English UI):
 * "Song title - 1 hour, 16 minutes, 23 seconds - Go to channel - CIRCOLOCO - 654K views - 2 months ago - play video"
 */
object FeedItemParser {

    private val unit = Regex(
        """(\d+)\s*(hours|hour|hrs|hr|ore|ora|minutes|minute|minuti|minuto|mins|min|seconds|second|secondi|secondo|secs|sec)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val clock = Regex("""^\d{1,2}(:\d{2}){1,2}$""")
    private val goToChannel = Regex("""^(go to channel|vai al canale)$""", RegexOption.IGNORE_CASE)
    private val goToChannelPrefix = Regex("""^(go to channel|vai al canale)\s+""", RegexOption.IGNORE_CASE)
    private val views = Regex("""(views?|visualizzazion[ei]|watching|spettatori)""", RegexOption.IGNORE_CASE)

    /**
     * @param requireViews when true, only descriptions that also mention a view count are
     *   accepted (filters out playlists and buttons); large thumbnail-sized nodes relax this.
     */
    fun parse(description: String?, requireViews: Boolean = true): NowPlayingOnYouTube? {
        if (description.isNullOrBlank()) return null
        // YouTube separates fields with " - ", " – ", " · " … depending on version/language.
        val parts = description.split(" - ", " – ", " — ", " · ", " • ").map { it.trim() }.filter { it.isNotEmpty() }
        val durIndex = parts.indexOfFirst { isDuration(it) }
        if (durIndex <= 0) return null
        if (requireViews && parts.none { views.containsMatchIn(it) }) return null

        val title = parts.subList(0, durIndex).joinToString(" - ")
        val rest = parts.drop(durIndex + 1)
        val channel = rest.indexOfFirst { goToChannel.matches(it) }
            .let { i -> if (i >= 0) rest.getOrNull(i + 1) else rest.firstOrNull() }
            .orEmpty()
            .replace(goToChannelPrefix, "")
            .takeUnless { views.containsMatchIn(it) }
            .orEmpty()
        return NowPlayingOnYouTube(title, channel, durationSec(parts[durIndex]))
    }

    private fun isDuration(s: String) = clock.matches(s) || (unit.containsMatchIn(s) && s.length < 60 &&
        unit.replace(s, "").replace(",", "").replace(" e ", "").replace(" and ", "").isBlank())

    fun durationSec(s: String): Long {
        if (clock.matches(s)) return s.split(':').fold(0L) { acc, p -> acc * 60 + p.toLong() }
        return unit.findAll(s).sumOf { m ->
            val n = m.groupValues[1].toLong()
            val u = m.groupValues[2].lowercase()
            when {
                u.startsWith("h") || u.startsWith("or") -> n * 3600
                u.startsWith("m") -> n * 60
                else -> n
            }
        }
    }
}
