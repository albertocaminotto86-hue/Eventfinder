package com.lockplayer

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Moves the Opus audio of a WebM file into an Ogg file, without re-encoding.
 *
 * Android's media library refuses "audio/webm" files in the Music collection, while Ogg Opus
 * is accepted there and plays in more players.
 */
@RequiresApi(29)
object OpusRemuxer {

    fun webmToOgg(src: File, dst: FileDescriptor) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(src.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_OPUS
            } ?: throw IllegalStateException("Nessuna traccia Opus nel file")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)

            muxer = MediaMuxer(dst, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            val out = muxer.addTrack(format)
            muxer.start()

            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                256 * 1024
            }
            val buffer = ByteBuffer.allocate(size)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val n = extractor.readSampleData(buffer, 0)
                if (n < 0) break
                val key = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                info.set(0, n, extractor.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(out, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }
}
