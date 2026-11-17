package com.example.data.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Lightweight pure-Kotlin HTTP-FLV demuxer that writes H.264 (AVC) video and AAC audio
 * directly into an Android MediaMuxer MP4 container without external native binaries.
 */
class FlvToMp4Demuxer(
    private val outputMp4: File,
    private val onProgressBytes: (Long) -> Unit
) {
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false

    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var naluLengthSize = 4

    private val pendingSamples = mutableListOf<PendingSample>()
    private var firstAudioPtsUs = -1L
    private var firstVideoPtsUs = -1L
    private var lastVideoPtsUs = -1L
    private var lastAudioPtsUs = -1L

    private data class PendingSample(
        val isVideo: Boolean,
        val data: ByteArray,
        val ptsUs: Long,
        val flags: Int
    )

    fun demux(
        rawStream: InputStream,
        shouldStop: () -> Boolean
    ): Boolean {
        val input = BufferedInputStream(rawStream, 64 * 1024)
        val header = readExactlyOrNull(input, 9) ?: return false
        if (header[0].toInt() != 'F'.code || header[1].toInt() != 'L'.code || header[2].toInt() != 'V'.code) {
            throw IllegalStateException("Stream is not a valid FLV container")
        }
        val dataOffset = ((header[5].toInt() and 0xFF) shl 24) or
            ((header[6].toInt() and 0xFF) shl 16) or
            ((header[7].toInt() and 0xFF) shl 8) or
            (header[8].toInt() and 0xFF)
        if (dataOffset > 9) {
            skipExactly(input, (dataOffset - 9).toLong())
        }
        // PreviousTagSize0
        readExactlyOrNull(input, 4) ?: return false

        val startWallMs = System.currentTimeMillis()
        var wroteAnySample = false

        try {
            while (!shouldStop()) {
                val tagHeader = readExactlyOrNull(input, 11) ?: break
                val tagType = tagHeader[0].toInt() and 0x1F
                val dataSize = ((tagHeader[1].toInt() and 0xFF) shl 16) or
                    ((tagHeader[2].toInt() and 0xFF) shl 8) or
                    (tagHeader[3].toInt() and 0xFF)
                val tsLower = ((tagHeader[4].toInt() and 0xFF) shl 16) or
                    ((tagHeader[5].toInt() and 0xFF) shl 8) or
                    (tagHeader[6].toInt() and 0xFF)
                val tsUpper = (tagHeader[7].toInt() and 0xFF) shl 24
                val timestampMs = (tsUpper or tsLower).toLong() and 0xFFFFFFFFL

                if (dataSize <= 0 || dataSize > 16 * 1024 * 1024) {
                    if (dataSize > 0) skipExactly(input, dataSize.toLong())
                    readExactlyOrNull(input, 4) ?: break
                    continue
                }

                val payload = readExactlyOrNull(input, dataSize) ?: break
                // PreviousTagSize
                readExactlyOrNull(input, 4) ?: break

                when (tagType) {
                    0x08 -> handleAudioTag(payload, timestampMs)
                    0x09 -> handleVideoTag(payload, timestampMs)
                }

                if (!muxerStarted) {
                    val readyBoth = videoFormat != null && audioFormat != null
                    val timedOutWaitingSecondTrack =
                        (videoFormat != null || audioFormat != null) &&
                            (System.currentTimeMillis() - startWallMs > 3000L)
                    if (readyBoth || timedOutWaitingSecondTrack) {
                        startMuxerAndFlush()
                        wroteAnySample = true
                    }
                } else {
                    wroteAnySample = true
                }

                if (outputMp4.exists()) {
                    onProgressBytes(outputMp4.length())
                }
            }

            if (!muxerStarted && (videoFormat != null || audioFormat != null)) {
                startMuxerAndFlush()
                wroteAnySample = true
            }
        } finally {
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
            } catch (_: Exception) {
            } finally {
                runCatching { muxer?.release() }
                muxer = null
            }
        }
        return wroteAnySample && outputMp4.exists() && outputMp4.length() > 1024
    }

    private fun handleVideoTag(payload: ByteArray, timestampMs: Long) {
        if (payload.size < 5) return
        val first = payload[0].toInt() and 0xFF
        val frameType = (first ushr 4) and 0x0F
        val codecId = first and 0x0F
        if (codecId != 7) return // Only AVC (H.264) supported by standard MediaMuxer

        val avcPacketType = payload[1].toInt() and 0xFF
        val ctsRaw = ((payload[2].toInt() and 0xFF) shl 16) or
            ((payload[3].toInt() and 0xFF) shl 8) or
            (payload[4].toInt() and 0xFF)
        val compositionOffsetMs = if ((ctsRaw and 0x800000) != 0) ctsRaw or -0x1000000 else ctsRaw

        if (avcPacketType == 0 && videoFormat == null) {
            parseAvcDecoderConfig(payload, 5)
        } else if (avcPacketType == 1) {
            val annexB = convertLengthPrefixedToAnnexB(payload, 5, naluLengthSize) ?: return
            val rawPtsUs = (timestampMs + compositionOffsetMs.toLong()).coerceAtLeast(0L) * 1000L
            if (firstVideoPtsUs < 0L) firstVideoPtsUs = rawPtsUs
            val normalizedPtsUs = (rawPtsUs - firstVideoPtsUs).coerceAtLeast(0L)
            val flags = if (frameType == 1) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0

            if (muxerStarted && videoTrackIndex >= 0) {
                val safePts = maxOf(normalizedPtsUs, lastVideoPtsUs + 1L)
                lastVideoPtsUs = safePts
                writeSample(videoTrackIndex, annexB, safePts, flags)
            } else if (pendingSamples.size < 300) {
                pendingSamples.add(PendingSample(true, annexB, normalizedPtsUs, flags))
            }
        }
    }

    private fun handleAudioTag(payload: ByteArray, timestampMs: Long) {
        if (payload.size < 2) return
        val soundFormat = (payload[0].toInt() ushr 4) and 0x0F
        if (soundFormat != 10) return // AAC
        val aacPacketType = payload[1].toInt() and 0xFF

        if (aacPacketType == 0 && audioFormat == null) {
            if (payload.size < 4) return
            val asc = payload.copyOfRange(2, payload.size)
            val b0 = asc[0].toInt() and 0xFF
            val b1 = asc[1].toInt() and 0xFF
            val freqIndex = ((b0 and 0x07) shl 1) or ((b1 ushr 7) and 0x01)
            val channels = ((b1 ushr 3) and 0x0F).coerceIn(1, 8)
            val sampleRate = AAC_SAMPLE_RATES.getOrElse(freqIndex) { 44100 }

            audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channels
            ).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(asc))
            }
        } else if (aacPacketType == 1 && payload.size > 2) {
            val rawAac = payload.copyOfRange(2, payload.size)
            val rawPtsUs = timestampMs.coerceAtLeast(0L) * 1000L
            if (firstAudioPtsUs < 0L) firstAudioPtsUs = rawPtsUs
            val normalizedPtsUs = (rawPtsUs - firstAudioPtsUs).coerceAtLeast(0L)

            if (muxerStarted && audioTrackIndex >= 0) {
                val safePts = maxOf(normalizedPtsUs, lastAudioPtsUs + 1L)
                lastAudioPtsUs = safePts
                writeSample(audioTrackIndex, rawAac, safePts, 0)
            } else if (pendingSamples.size < 300) {
                pendingSamples.add(PendingSample(false, rawAac, normalizedPtsUs, 0))
            }
        }
    }

    private fun parseAvcDecoderConfig(payload: ByteArray, offset: Int) {
        if (payload.size < offset + 7) return
        naluLengthSize = (payload[offset + 4].toInt() and 0x03) + 1
        var pos = offset + 5
        val numSps = payload[pos++].toInt() and 0x1F
        val spsList = mutableListOf<ByteArray>()
        for (i in 0 until numSps) {
            if (pos + 2 > payload.size) return
            val len = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
            pos += 2
            if (pos + len > payload.size) return
            spsList.add(byteArrayOf(0, 0, 0, 1) + payload.copyOfRange(pos, pos + len))
            pos += len
        }
        if (pos >= payload.size) return
        val numPps = payload[pos++].toInt() and 0xFF
        val ppsList = mutableListOf<ByteArray>()
        for (i in 0 until numPps) {
            if (pos + 2 > payload.size) return
            val len = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
            pos += 2
            if (pos + len > payload.size) return
            ppsList.add(byteArrayOf(0, 0, 0, 1) + payload.copyOfRange(pos, pos + len))
            pos += len
        }

        if (spsList.isEmpty()) return
        val csd0 = spsList.reduce { acc, bytes -> acc + bytes }
        val csd1 = if (ppsList.isNotEmpty()) ppsList.reduce { acc, bytes -> acc + bytes } else null

        videoFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            720,
            1280
        ).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
            if (csd1 != null) {
                setByteBuffer("csd-1", ByteBuffer.wrap(csd1))
            }
        }
    }

    private fun convertLengthPrefixedToAnnexB(
        payload: ByteArray,
        offset: Int,
        lengthSize: Int
    ): ByteArray? {
        var pos = offset
        val chunks = mutableListOf<ByteArray>()
        var totalSize = 0
        while (pos + lengthSize <= payload.size) {
            var nalLen = 0
            for (i in 0 until lengthSize) {
                nalLen = (nalLen shl 8) or (payload[pos + i].toInt() and 0xFF)
            }
            pos += lengthSize
            if (nalLen <= 0 || pos + nalLen > payload.size) break
            val nal = ByteArray(4 + nalLen)
            nal[0] = 0; nal[1] = 0; nal[2] = 0; nal[3] = 1
            System.arraycopy(payload, pos, nal, 4, nalLen)
            chunks.add(nal)
            totalSize += nal.size
            pos += nalLen
        }
        if (chunks.isEmpty()) return null
        val out = ByteArray(totalSize)
        var outPos = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, out, outPos, chunk.size)
            outPos += chunk.size
        }
        return out
    }

    private fun startMuxerAndFlush() {
        val m = MediaMuxer(outputMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        videoFormat?.let { videoTrackIndex = m.addTrack(it) }
        audioFormat?.let { audioTrackIndex = m.addTrack(it) }
        m.start()
        muxer = m
        muxerStarted = true

        for (sample in pendingSamples) {
            if (sample.isVideo && videoTrackIndex >= 0) {
                val safePts = maxOf(sample.ptsUs, lastVideoPtsUs + 1L)
                lastVideoPtsUs = safePts
                writeSample(videoTrackIndex, sample.data, safePts, sample.flags)
            } else if (!sample.isVideo && audioTrackIndex >= 0) {
                val safePts = maxOf(sample.ptsUs, lastAudioPtsUs + 1L)
                lastAudioPtsUs = safePts
                writeSample(audioTrackIndex, sample.data, safePts, sample.flags)
            }
        }
        pendingSamples.clear()
    }

    private fun writeSample(trackIndex: Int, bytes: ByteArray, ptsUs: Long, flags: Int) {
        val info = MediaCodec.BufferInfo().apply {
            offset = 0
            size = bytes.size
            presentationTimeUs = ptsUs
            this.flags = flags
        }
        muxer?.writeSampleData(trackIndex, ByteBuffer.wrap(bytes), info)
    }

    private fun readExactlyOrNull(input: InputStream, count: Int): ByteArray? {
        val buf = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buf, read, count - read)
            if (n < 0) {
                return if (read == 0) null else throw EOFException("Unexpected EOF in FLV stream")
            }
            read += n
        }
        return buf
    }

    private fun skipExactly(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    companion object {
        private val AAC_SAMPLE_RATES = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350
        )
    }
}
