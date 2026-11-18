package com.lockplayer

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object YouTubeSearch {
    /** Blocking: finds the YouTube URL of the video described by the YouTube app's media session. */
    fun find(target: NowPlayingOnYouTube): String? {
        val service = ServiceList.YouTube
        val query = listOf(target.title, target.channel).filter { it.isNotBlank() }.joinToString(" ")
        val handler = service.searchQHFactory.fromQuery(
            query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), ""
        )
        val candidates = SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { Candidate(it.url, it.name, it.uploaderName.orEmpty(), it.duration) }
        return VideoMatcher.pickBest(target, candidates)?.url
    }
}
