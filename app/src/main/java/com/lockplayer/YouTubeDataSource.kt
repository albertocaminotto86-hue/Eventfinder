package com.lockplayer

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.atomic.AtomicLong

/**
 * Rewrites requests for YouTube media files the way YouTube's own clients make them
 * (POST with a tiny body, a request counter and client-matching headers). Without this
 * YouTube tends to throttle or reject the requests.
 */
@UnstableApi
object YouTubeDataSource {

    private val POST_BODY = byteArrayOf(0x78, 0)
    private val requestNumber = AtomicLong(0)

    fun factory(): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ExtractorDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        return ResolvingDataSource.Factory(http, ::rewrite)
    }

    private fun rewrite(spec: DataSpec): DataSpec {
        val url = spec.uri.toString()
        if (spec.uri.path?.startsWith("/videoplayback") != true) return spec

        val headers = HashMap(spec.httpRequestHeaders)
        headers["TE"] = "trailers"
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com"
            headers["Sec-Fetch-Dest"] = "empty"
            headers["Sec-Fetch-Mode"] = "cors"
            headers["Sec-Fetch-Site"] = "cross-site"
        }
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            headers["User-Agent"] = YoutubeParsingHelper.getVisionOsUserAgent(null)
        }

        val newUrl = if (url.contains("&rn=")) url else "$url&rn=${requestNumber.getAndIncrement()}"

        return spec.buildUpon()
            .setUri(newUrl)
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(POST_BODY)
            .setHttpRequestHeaders(headers)
            .build()
    }
}
