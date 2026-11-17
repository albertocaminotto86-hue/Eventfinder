package com.lockplayer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.ByteArrayInputStream

/** Builds the right ExoPlayer source (DASH / HLS / progressive) for each resolved queue item. */
@UnstableApi
class YouTubeMediaSourceFactory : MediaSource.Factory {

    private val dataSource = YouTubeDataSource.factory()
    private val dash = DashMediaSource.Factory(dataSource)
    private val hls = HlsMediaSource.Factory(dataSource)
    private val progressive = ProgressiveMediaSource.Factory(dataSource)

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider) = apply {
        dash.setDrmSessionManagerProvider(provider)
        hls.setDrmSessionManagerProvider(provider)
        progressive.setDrmSessionManagerProvider(provider)
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy) = apply {
        dash.setLoadErrorHandlingPolicy(policy)
        hls.setLoadErrorHandlingPolicy(policy)
        progressive.setLoadErrorHandlingPolicy(policy)
    }

    override fun getSupportedTypes(): IntArray =
        intArrayOf(C.CONTENT_TYPE_DASH, C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_OTHER)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val track = TrackStore.get(mediaItem.mediaId)
            ?: return progressive.createMediaSource(mediaItem)
        val item = mediaItem.buildUpon().setUri(Uri.parse(track.source.uri)).build()
        return when (val s = track.source) {
            is Source.Dash -> {
                val manifest = DashManifestParser().parse(
                    Uri.parse(s.uri), ByteArrayInputStream(s.manifest.toByteArray())
                )
                dash.createMediaSource(manifest, item)
            }
            is Source.Hls -> hls.createMediaSource(item)
            is Source.Progressive -> progressive.createMediaSource(item)
        }
    }
}
