package com.lockplayer

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator

/** How the player should load a resolved track. */
sealed class Source {
    abstract val uri: String

    /** DASH manifest built locally: lets the player fetch the audio in small chunks. */
    data class Dash(override val uri: String, val manifest: String) : Source()
    data class Hls(override val uri: String) : Source()
    data class Progressive(override val uri: String) : Source()
}

data class ResolvedTrack(
    val pageUrl: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSec: Long,
    val source: Source,
)

object YouTubeResolver {

    private val urlRegex = Regex("""https?://\S+""")

    /** Pulls the first YouTube link out of shared text such as "Look at this: https://youtu.be/…". */
    fun extractYouTubeUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return urlRegex.findAll(text)
            .map { it.value.trimEnd('.', ',', ')', '"', '\'') }
            .firstOrNull { isYouTubeUrl(it) }
    }

    fun isYouTubeUrl(url: String): Boolean =
        runCatching { ServiceList.YouTube.streamLHFactory.acceptUrl(url) }.getOrDefault(false)

    /** Blocking network call: run it off the main thread. */
    fun resolve(url: String): ResolvedTrack {
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        return ResolvedTrack(
            pageUrl = info.url,
            title = info.name,
            artist = info.uploaderName.orEmpty(),
            artworkUrl = info.thumbnails.maxByOrNull { it.width * it.height }?.url,
            durationSec = info.duration,
            source = pickSource(info),
        )
    }

    private fun pickSource(info: StreamInfo): Source {
        val progressive = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            // Prefer the original-language track when a video has dubbed audio tracks.
            .sortedWith(
                compareByDescending<AudioStream> { it.audioTrackType == null || it.audioTrackType?.name == "ORIGINAL" }
                    .thenByDescending { it.averageBitrate }
            )

        for (stream in progressive) {
            val itag = stream.itagItem ?: continue
            val manifest = runCatching {
                YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                    stream.content, itag, info.duration
                )
            }.getOrNull() ?: continue
            return Source.Dash(stream.content, manifest)
        }

        progressive.firstOrNull()?.let { return Source.Progressive(it.content) }

        info.hlsUrl?.takeIf { it.isNotBlank() }?.let { return Source.Hls(it) }

        info.audioStreams.firstOrNull { it.isUrl && it.deliveryMethod == DeliveryMethod.HLS }
            ?.let { return Source.Hls(it.content) }

        throw IllegalStateException("Nessuno stream audio riproducibile trovato")
    }
}
