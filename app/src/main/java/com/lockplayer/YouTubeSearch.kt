package com.lockplayer

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object YouTubeSearch {

    /** Result of a lookup, plus a human-readable trace shown in the app's diagnostics. */
    data class Result(val url: String?, val trace: String)

    /** Blocking: finds the YouTube URL of the video described by the YouTube app's media session. */
    fun find(target: NowPlayingOnYouTube): Result {
        val trace = StringBuilder()
        trace.appendLine("Da YouTube: «${target.title}» · canale «${target.channel}» · ${target.durationSec}s")

        val queries = listOf(
            listOf(target.title, target.channel).filter { it.isNotBlank() }.joinToString(" "),
            target.title,
        ).distinct()

        val seen = LinkedHashMap<String, Candidate>()
        for (query in queries) {
            val found = runCatching { search(query) }
                .onFailure { trace.appendLine("Ricerca «$query»: errore ${it.javaClass.simpleName}: ${it.message?.take(120)}") }
                .getOrNull() ?: continue
            trace.appendLine("Ricerca «$query»: ${found.size} risultati")
            found.forEach { seen.putIfAbsent(it.url, it) }
            val best = VideoMatcher.pickBest(target, seen.values.toList())
            if (best != null) {
                trace.appendLine("Scelto: «${best.title}» · ${best.channel} · ${best.durationSec}s")
                return Result(best.url, trace.toString())
            }
        }

        trace.appendLine("Nessuna corrispondenza (serve punteggio ≥ ${VideoMatcher.minScore(target)}). Migliori:")
        seen.values
            .sortedByDescending { VideoMatcher.score(target, it) }
            .take(3)
            .forEach { trace.appendLine("  ${VideoMatcher.score(target, it)} · «${it.title}» · ${it.channel} · ${it.durationSec}s") }
        return Result(null, trace.toString())
    }

    private fun search(query: String): List<Candidate> {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(
            query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), ""
        )
        return SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { Candidate(it.url, it.name, it.uploaderName.orEmpty(), it.duration) }
    }
}
