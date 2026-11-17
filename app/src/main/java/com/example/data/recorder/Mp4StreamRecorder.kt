package com.example.data.recorder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class RecordingStats(
    val bytesWritten: Long,
    val segmentsProcessed: Int
)

/**
 * Records a live HLS (.m3u8) or HTTP-FLV (.flv) stream into a local MP4 file
 * using Android's native MediaExtractor + MediaMuxer (and FlvToMp4Demuxer fallback).
 */
class Mp4StreamRecorder(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val buffer = ByteBuffer.allocate(1024 * 1024)

    suspend fun recordToTempMp4(
        hlsUrl: String?,
        flvUrl: String?,
        outputMp4: File,
        shouldStop: () -> Boolean,
        onStatsUpdate: (RecordingStats) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hlsUrl.isNullOrBlank()) {
            val hlsOk = runCatching {
                recordHlsToMp4(hlsUrl, outputMp4, shouldStop, onStatsUpdate)
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                false
            }
            if (hlsOk && outputMp4.exists() && outputMp4.length() > 1024) {
                return@withContext true
            }
        }

        if (!flvUrl.isNullOrBlank() && !shouldStop()) {
            return@withContext recordFlvToMp4(flvUrl, outputMp4, shouldStop, onStatsUpdate)
        }

        false
    }

    private suspend fun recordHlsToMp4(
        initialPlaylistUrl: String,
        outputMp4: File,
        shouldStop: () -> Boolean,
        onStatsUpdate: (RecordingStats) -> Unit
    ): Boolean {
        val mediaPlaylistUrl = resolveVariantPlaylistUrl(initialPlaylistUrl)
        val seenSegments = LinkedHashSet<String>()
        var initSegmentBytes: ByteArray? = null
        var lastInitUrl: String? = null

        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val extractorTrackToMuxerTrack = mutableMapOf<Int, Int>()
        val lastWrittenPtsByMuxerTrack = mutableMapOf<Int, Long>()
        var segmentBasePtsUs = 0L
        var segmentsProcessed = 0

        val tempDir = File(context.cacheDir, "hls_tmp_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            while (!shouldStop()) {
                val playlistText = fetchText(mediaPlaylistUrl)
                if (playlistText == null) {
                    delay(1500)
                    continue
                }

                val parsed = parseMediaPlaylist(mediaPlaylistUrl, playlistText)
                if (parsed.initMapUrl != null && parsed.initMapUrl != lastInitUrl) {
                    initSegmentBytes = fetchBytes(parsed.initMapUrl)
                    lastInitUrl = parsed.initMapUrl
                }

                val newSegments = parsed.segmentUrls.filter { it !in seenSegments }
                for (segUrl in newSegments) {
                    if (shouldStop()) break
                    seenSegments.add(segUrl)
                    while (seenSegments.size > 300) {
                        val first = seenSegments.firstOrNull() ?: break
                        seenSegments.remove(first)
                    }

                    val segBytes = fetchBytes(segUrl) ?: continue
                    val segFile = File(tempDir, "seg_${segmentsProcessed}.ts")
                    try {
                        FileOutputStream(segFile).use { fos ->
                            initSegmentBytes?.let { fos.write(it) }
                            fos.write(segBytes)
                        }

                        val extractor = MediaExtractor()
                        try {
                            extractor.setDataSource(segFile.absolutePath)
                            val trackCount = extractor.trackCount
                            if (trackCount <= 0) continue

                            if (!muxerStarted) {
                                val m = MediaMuxer(
                                    outputMp4.absolutePath,
                                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                                )
                                for (i in 0 until trackCount) {
                                    val format = extractor.getTrackFormat(i)
                                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                                        val muxTrack = m.addTrack(format)
                                        extractorTrackToMuxerTrack[i] = muxTrack
                                        lastWrittenPtsByMuxerTrack[muxTrack] = -1L
                                    }
                                }
                                if (extractorTrackToMuxerTrack.isEmpty()) {
                                    m.release()
                                    continue
                                }
                                m.start()
                                muxer = m
                                muxerStarted = true
                            }

                            val activeMuxer = muxer ?: continue
                            for (i in 0 until trackCount) {
                                if (extractorTrackToMuxerTrack.containsKey(i)) {
                                    extractor.selectTrack(i)
                                }
                            }

                            var minSegPtsUs = Long.MAX_VALUE
                            var maxRelativePtsUs = 0L
                            val sampleInfo = MediaCodec.BufferInfo()

                            // First pass or direct streaming with baseline tracking
                            while (!shouldStop()) {
                                val trackIdx = extractor.sampleTrackIndex
                                if (trackIdx < 0) break
                                val muxTrack = extractorTrackToMuxerTrack[trackIdx]
                                if (muxTrack == null) {
                                    extractor.advance()
                                    continue
                                }

                                buffer.clear()
                                val sampleSize = extractor.readSampleData(buffer, 0)
                                if (sampleSize < 0) break

                                val rawPtsUs = extractor.sampleTime
                                if (rawPtsUs >= 0) {
                                    if (minSegPtsUs == Long.MAX_VALUE) {
                                        minSegPtsUs = rawPtsUs
                                    }
                                    val relPtsUs = max(0L, rawPtsUs - minSegPtsUs)
                                    if (relPtsUs > maxRelativePtsUs) {
                                        maxRelativePtsUs = relPtsUs
                                    }
                                    val targetPtsUs = segmentBasePtsUs + relPtsUs
                                    val prevPts = lastWrittenPtsByMuxerTrack[muxTrack] ?: -1L
                                    val safePts = max(targetPtsUs, prevPts + 1L)
                                    lastWrittenPtsByMuxerTrack[muxTrack] = safePts

                                    sampleInfo.offset = 0
                                    sampleInfo.size = sampleSize
                                    sampleInfo.presentationTimeUs = safePts
                                    sampleInfo.flags = extractor.sampleFlags

                                    buffer.limit(sampleSize)
                                    buffer.position(0)
                                    activeMuxer.writeSampleData(muxTrack, buffer, sampleInfo)
                                }
                                extractor.advance()
                            }

                            segmentBasePtsUs += max(maxRelativePtsUs + 33_333L, 500_000L)
                            segmentsProcessed++
                            onStatsUpdate(
                                RecordingStats(
                                    bytesWritten = outputMp4.length(),
                                    segmentsProcessed = segmentsProcessed
                                )
                            )
                        } finally {
                            runCatching { extractor.release() }
                        }
                    } finally {
                        segFile.delete()
                    }
                }

                if (parsed.isEndList) break
                val pollDelayMs = (parsed.targetDurationSec * 500L).coerceIn(1000L, 4000L)
                delay(pollDelayMs)
            }
        } finally {
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {
            } finally {
                runCatching { muxer?.release() }
            }
            tempDir.deleteRecursively()
        }

        return muxerStarted && outputMp4.exists() && outputMp4.length() > 1024
    }

    private fun recordFlvToMp4(
        flvUrl: String,
        outputMp4: File,
        shouldStop: () -> Boolean,
        onStatsUpdate: (RecordingStats) -> Unit
    ): Boolean {
        val request = Request.Builder()
            .url(flvUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.tiktok.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val bodyStream = response.body?.byteStream() ?: return false
            var segmentsApprox = 0
            val demuxer = FlvToMp4Demuxer(outputMp4) { bytes ->
                segmentsApprox++
                onStatsUpdate(RecordingStats(bytesWritten = bytes, segmentsProcessed = segmentsApprox / 40))
            }
            return demuxer.demux(bodyStream, shouldStop)
        }
    }

    private fun resolveVariantPlaylistUrl(url: String): String {
        val content = fetchText(url) ?: return url
        if (!content.contains("#EXT-X-STREAM-INF")) return url

        var bestBandwidth = -1L
        var bestUri: String? = null
        val lines = content.lines()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("""BANDWIDTH=(\d+)""").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val nextUri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }?.trim()
                if (nextUri != null && bw > bestBandwidth) {
                    bestBandwidth = bw
                    bestUri = resolveRelativeUrl(url, nextUri)
                }
            }
        }
        return bestUri ?: url
    }

    private data class ParsedPlaylist(
        val initMapUrl: String?,
        val segmentUrls: List<String>,
        val targetDurationSec: Long,
        val isEndList: Boolean
    )

    private fun parseMediaPlaylist(baseUrl: String, content: String): ParsedPlaylist {
        var initMapUrl: String? = null
        var targetDurationSec = 4L
        var isEndList = false
        val segments = mutableListOf<String>()

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-TARGETDURATION:") -> {
                    targetDurationSec = line.substringAfter(":").toLongOrNull() ?: 4L
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val uri = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.getOrNull(1)
                    if (!uri.isNullOrBlank()) {
                        initMapUrl = resolveRelativeUrl(baseUrl, uri)
                    }
                }
                line.startsWith("#EXT-X-ENDLIST") -> {
                    isEndList = true
                }
                !line.startsWith("#") -> {
                    segments.add(resolveRelativeUrl(baseUrl, line))
                }
            }
        }
        return ParsedPlaylist(initMapUrl, segments, targetDurationSec, isEndList)
    }

    private fun resolveRelativeUrl(baseUrl: String, relative: String): String {
        return runCatching { URI(baseUrl).resolve(relative).toString() }.getOrDefault(relative)
    }

    private fun fetchText(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.tiktok.com/")
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private fun fetchBytes(url: String): ByteArray? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.tiktok.com/")
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        }.getOrNull()
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
