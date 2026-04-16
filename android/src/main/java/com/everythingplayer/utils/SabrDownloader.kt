package com.everythingplayer.utils

import android.util.Base64
import com.google.protobuf.ByteString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import misc.Common
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import video_streaming.ClientAbrStateOuterClass
import video_streaming.FormatInitializationMetadataOuterClass
import video_streaming.MediaHeaderOuterClass
import video_streaming.NextRequestPolicyOuterClass
import video_streaming.ReloadPlayerResponse
import video_streaming.SabrErrorOuterClass
import video_streaming.StreamProtectionStatusOuterClass
import video_streaming.StreamerContextOuterClass
import video_streaming.VideoPlaybackAbrRequestOuterClass

// ── Data classes ──────────────────────────────────────────────────────────────

data class SabrFormatDescriptor(
    val itag: Int,
    val lastModified: Long = 0,
    val xtags: String = "",
    val mimeType: String? = null,
    val approxDurationMs: Int = 0,
    val bitrate: Int = 0
)

data class SabrConfig(
    var serverUrl: String,
    var ustreamerConfig: String,   // base64-encoded VideoPlaybackUstreamerConfig
    var poToken: String? = null,   // base64-encoded PO token
    var formats: List<SabrFormatDescriptor> = emptyList(),
    var durationMs: Double = 0.0,
    var cookie: String? = null,
    var clientName: Int? = null,
    var clientVersion: String? = null,
    var preferOpus: Boolean = false,
    var startTimeMs: Long = 0L
)

private fun sabrBase64Decode(s: String): ByteArray {
    var normalized = s.replace('-', '+').replace('_', '/')
    val rem = normalized.length % 4
    if (rem > 0) normalized += "=".repeat(4 - rem)
    return try { Base64.decode(normalized, Base64.DEFAULT) } catch (_: Exception) { ByteArray(0) }
}

private inline fun <T> parseProto(bytes: ByteArray, parser: () -> T): T? {
    if (bytes.isEmpty()) return null
    return try {
        parser()
    } catch (_: Exception) {
        null
    }
}

private object UmpPartId {
    const val NEXT_REQUEST_POLICY = 35
    const val FORMAT_INITIALIZATION_METADATA = 42
    const val SABR_ERROR = 44
    const val RELOAD_PLAYER_RESPONSE = 46
    const val STREAM_PROTECTION_STATUS = 58
    const val MEDIA_HEADER = 20
    const val MEDIA = 21
    const val MEDIA_END = 22
}

// ── UMP parse state ───────────────────────────────────────────────────────────

private data class PartialSegment(
    val itag: Int,
    val isAudio: Boolean,
    val durationMs: Long,
    val chunks: MutableList<ByteArray> = mutableListOf()
)

private data class CompletedSegment(val data: ByteArray, val durationMs: Long, val isAudio: Boolean)

private class UmpState(audioFormats: List<SabrFormatDescriptor>) {
    // Itags from JS-provided format list that are audio
    private val hintAudioItags: Set<Int> =
        audioFormats.filter { it.mimeType?.contains("audio") == true }.map { it.itag }.toSet()
    // Itags confirmed audio by FORMAT_INITIALIZATION_METADATA
    private val confirmedAudioItags = mutableSetOf<Int>()
    // endSegmentNumber per audio itag
    val endSegmentNumbers = mutableMapOf<Int, Long>()
    // completed audio segments per itag
    val completedAudioSegments = mutableMapOf<Int, Int>()
    // partial in-progress segments keyed by headerID
    private val pending = mutableMapOf<Int, PartialSegment>()

    init { confirmedAudioItags.addAll(hintAudioItags) }

    fun onFormatInitMetadata(bytes: ByteArray) {
        val metadata = parseProto(bytes) {
            FormatInitializationMetadataOuterClass.FormatInitializationMetadata.parseFrom(bytes)
        } ?: return
        if (!metadata.hasFormatId() || !metadata.hasEndSegmentNumber()) return
        val itag = metadata.formatId.itag
        val endSeg = metadata.endSegmentNumber
        val mimeType = if (metadata.hasMimeType()) metadata.mimeType else ""
        if (mimeType.contains("audio")) {
            confirmedAudioItags.add(itag)
            endSegmentNumbers[itag] = endSeg
        }
    }

    fun onMediaHeader(bytes: ByteArray) {
        val mediaHeader = parseProto(bytes) {
            MediaHeaderOuterClass.MediaHeader.parseFrom(bytes)
        } ?: return
        if (!mediaHeader.hasHeaderId() || !mediaHeader.hasItag()) return
        val headerId = mediaHeader.headerId
        val itag = mediaHeader.itag
        val durationMs = if (mediaHeader.hasDurationMs()) mediaHeader.durationMs else 0L
        val isAudio = itag in confirmedAudioItags || hintAudioItags.isEmpty()
        pending[headerId] = PartialSegment(itag = itag, isAudio = isAudio, durationMs = durationMs)
    }

    fun onMedia(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val headerId = bytes[0].toInt() and 0xFF
        pending[headerId]?.chunks?.add(bytes.copyOfRange(1, bytes.size))
    }

    fun onMediaEnd(bytes: ByteArray): CompletedSegment? {
        if (bytes.isEmpty()) return null
        val headerId = bytes[0].toInt() and 0xFF
        val seg = pending.remove(headerId) ?: return null
        val out = ByteArrayOutputStream()
        seg.chunks.forEach { out.write(it) }
        if (seg.isAudio) completedAudioSegments[seg.itag] = (completedAudioSegments[seg.itag] ?: 0) + 1
        return CompletedSegment(out.toByteArray(), seg.durationMs, seg.isAudio)
    }
}

// ── SabrDownloader ────────────────────────────────────────────────────────────

class SabrDownloader(private var config: SabrConfig) {

    var onRefreshPoToken: ((String) -> Unit)? = null
    var onReloadPlayerResponse: ((String?) -> Unit)? = null

    private var requestNumber = 0
    private val reloadChannel = Channel<Unit>(Channel.CONFLATED)
    private val proactiveRefreshThresholdBytes = 512_000L
    @Volatile private var aborted = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun updateStream(serverUrl: String, ustreamerConfig: String) {
        config = config.copy(serverUrl = serverUrl, ustreamerConfig = ustreamerConfig)
        reloadChannel.trySend(Unit)
    }

    fun updatePoToken(poToken: String) {
        config = config.copy(poToken = poToken)
    }

    fun abort() {
        aborted = true
        reloadChannel.close()
    }

    suspend fun stream(
        startTimeMs: Long = config.startTimeMs,
        onSegment: (ByteArray) -> Unit
    ) {
        executeStreaming(startTimeMs, null, onSegment)
    }

    /** Downloads SABR audio to [outputPath]. Calls [onProgress] with 0..1 fraction. Returns local path. */
    suspend fun download(outputPath: String, onProgress: ((Double) -> Unit)? = null): String =
        withContext(Dispatchers.IO) {
            File(outputPath).apply { parentFile?.mkdirs() }
            var fos = FileOutputStream(outputPath)
            try {
                executeStreaming(0L, onProgress) { bytes ->
                    fos.write(bytes)
                }
            } finally {
                fos.close()
            }

            onProgress?.invoke(1.0)
            return@withContext outputPath
        }

    private suspend fun executeStreaming(
        startTimeMs: Long,
        onProgress: ((Double) -> Unit)? = null,
        onSegment: (ByteArray) -> Unit
    ) = withContext(Dispatchers.IO) {
        aborted = false
        var downloadedDurationMs = 0L
        var downloadedBytes = 0L
        val totalDurationMs = config.durationMs.toLong()
        var proactiveRefreshSent = false

        outer@ while (true) {
            if (aborted) throw CancellationException("SABR stream aborted")
            val body = buildRequestBody(playerTimeMs = startTimeMs + downloadedDurationMs)
            val url = buildUrl(requestNumber)

            val reqBuilder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/x-protobuf".toMediaType()))
                .header("content-type", "application/x-protobuf")
                .header("accept", "application/vnd.yt-ump")
                .header("accept-encoding", "identity")
            config.cookie?.let { reqBuilder.header("Cookie", it) }

            val resp = httpClient.newCall(reqBuilder.build()).execute()
            requestNumber++

            if (!resp.isSuccessful) {
                resp.close()
                throw Exception("SABR HTTP error: ${resp.code}")
            }

            val respBytes = resp.body?.bytes() ?: ByteArray(0)
            resp.close()

            val state = UmpState(config.formats)
            var gotMedia = false
            var reloadRequested = false
            var backoffMs = 0L

            parseUmp(respBytes) { partId, partBytes ->
                when (partId) {
                    UmpPartId.FORMAT_INITIALIZATION_METADATA -> state.onFormatInitMetadata(partBytes)
                    UmpPartId.MEDIA_HEADER -> state.onMediaHeader(partBytes)
                    UmpPartId.MEDIA -> state.onMedia(partBytes)
                    UmpPartId.MEDIA_END -> {
                        val seg = state.onMediaEnd(partBytes)
                        if (seg != null && seg.isAudio && seg.data.isNotEmpty()) {
                            onSegment(seg.data)
                            downloadedDurationMs += seg.durationMs
                            downloadedBytes += seg.data.size
                            gotMedia = true
                            if (totalDurationMs > 0) {
                                val frac = ((startTimeMs + downloadedDurationMs).toDouble() / totalDurationMs).coerceIn(0.0, 0.99)
                                onProgress?.invoke(frac)
                            }
                            if (!proactiveRefreshSent && downloadedBytes >= proactiveRefreshThresholdBytes) {
                                proactiveRefreshSent = true
                                onRefreshPoToken?.invoke("proactive")
                            }
                        }
                    }
                    UmpPartId.NEXT_REQUEST_POLICY -> {
                        val policy = parseProto(partBytes) {
                            NextRequestPolicyOuterClass.NextRequestPolicy.parseFrom(partBytes)
                        }
                        backoffMs = if (policy?.hasBackoffTimeMs() == true) policy.backoffTimeMs.toLong() else 0L
                    }
                    UmpPartId.SABR_ERROR -> {
                        val sabrError = parseProto(partBytes) {
                            SabrErrorOuterClass.SabrError.parseFrom(partBytes)
                        }
                        val code = if (sabrError?.hasCode() == true) sabrError.code else -1
                        val type = if (sabrError?.hasType() == true) sabrError.type else "unknown"
                        throw Exception("SABR server error in response: code=$code type=$type")
                    }
                    UmpPartId.RELOAD_PLAYER_RESPONSE -> {
                        val reloadContext = parseProto(partBytes) {
                            ReloadPlayerResponse.ReloadPlaybackContext.parseFrom(partBytes)
                        }
                        val token = if (
                            reloadContext?.hasReloadPlaybackParams() == true &&
                            reloadContext.reloadPlaybackParams.hasToken()
                        ) reloadContext.reloadPlaybackParams.token else null
                        reloadRequested = true
                        onReloadPlayerResponse?.invoke(token)
                    }
                    UmpPartId.STREAM_PROTECTION_STATUS -> {
                        val protectionStatus = parseProto(partBytes) {
                            StreamProtectionStatusOuterClass.StreamProtectionStatus.parseFrom(partBytes)
                        }
                        val status = if (protectionStatus?.hasStatus() == true) protectionStatus.status else 0
                        if (status == 2) onRefreshPoToken?.invoke("expired")
                    }
                }
            }

            if (backoffMs > 0) delay(backoffMs)

            if (reloadRequested) {
                val resumed = withTimeoutOrNull(15_000L) { reloadChannel.receive() } != null
                if (!resumed) throw Exception("SABR reload timed out — call updateSabrStream within 15s")
                downloadedDurationMs = 0
                downloadedBytes = 0
                proactiveRefreshSent = false
                requestNumber = 0
                continue@outer
            }

            if (!gotMedia) break@outer
            if (totalDurationMs > 0 && startTimeMs + downloadedDurationMs >= totalDurationMs) break@outer
        }
    }

    // ── Request building ───────────────────────────────────────────────────────

    private fun buildUrl(rn: Int): String {
        val sep = if ('?' in config.serverUrl) '&' else '?'
        return "${config.serverUrl}${sep}rn=$rn"
    }

    private fun buildRequestBody(playerTimeMs: Long): ByteArray {
        val builder = VideoPlaybackAbrRequestOuterClass.VideoPlaybackAbrRequest.newBuilder()
        builder.clientAbrState = buildClientAbrState(playerTimeMs)

        val cfgBytes = sabrBase64Decode(config.ustreamerConfig)
        if (cfgBytes.isNotEmpty()) {
            builder.videoPlaybackUstreamerConfig = ByteString.copyFrom(cfgBytes)
        }

        for (fmt in selectPreferredAudioFormats()) {
            builder.addPreferredAudioFormatIds(buildFormatId(fmt))
        }

        buildStreamerContext()?.let { builder.streamerContext = it }

        return builder.build().toByteArray()
    }

    private fun selectPreferredAudioFormats(): List<SabrFormatDescriptor> {
        val audioFormats = config.formats.filter { it.mimeType?.contains("audio", ignoreCase = true) == true }
        if (audioFormats.isEmpty()) return emptyList()

        fun score(format: SabrFormatDescriptor): Int {
            val mimeType = format.mimeType.orEmpty().lowercase()
            return if (config.preferOpus) {
                when {
                    "opus" in mimeType || "webm" in mimeType -> 3
                    "mp4" in mimeType || "m4a" in mimeType -> 2
                    else -> 1
                }
            } else {
                when {
                    "mp4" in mimeType || "m4a" in mimeType -> 3
                    "opus" in mimeType || "webm" in mimeType -> 2
                    else -> 1
                }
            }
        }

        return audioFormats
            .sortedWith(compareByDescending<SabrFormatDescriptor> { score(it) }.thenByDescending { it.bitrate })
            .take(3)
    }

    private fun buildClientAbrState(playerTimeMs: Long): ClientAbrStateOuterClass.ClientAbrState {
        return ClientAbrStateOuterClass.ClientAbrState.newBuilder().apply {
            if (playerTimeMs > 0) this.playerTimeMs = playerTimeMs
            visibility = 1
            playbackRate = 1.0f
            enabledTrackTypesBitfield = 1
            stickyResolution = 360
            bandwidthEstimate = 5_000_000
        }.build()
    }

    private fun buildFormatId(fmt: SabrFormatDescriptor): Common.FormatId {
        return Common.FormatId.newBuilder().apply {
            if (fmt.itag != 0) itag = fmt.itag
            if (fmt.lastModified != 0L) lastModified = fmt.lastModified
            if (fmt.xtags.isNotEmpty()) xtags = fmt.xtags
        }.build()
    }

    private fun buildStreamerContext(): StreamerContextOuterClass.StreamerContext? {
        val builder = StreamerContextOuterClass.StreamerContext.newBuilder()

        config.clientName?.let { clientName ->
            val clientInfoBuilder = StreamerContextOuterClass.StreamerContext.ClientInfo.newBuilder()
            clientInfoBuilder.clientName = clientName
            config.clientVersion?.takeIf { it.isNotEmpty() }?.let { clientInfoBuilder.clientVersion = it }
            builder.clientInfo = clientInfoBuilder.build()
        }

        config.poToken?.let { token ->
            val tokenBytes = sabrBase64Decode(token)
            if (tokenBytes.isNotEmpty()) {
                builder.poToken = ByteString.copyFrom(tokenBytes)
            }
        }

        return if (builder.hasClientInfo() || builder.hasPoToken()) builder.build() else null
    }

    // ── UMP parsing ────────────────────────────────────────────────────────────

    private fun parseUmp(data: ByteArray, handler: (partId: Int, partBytes: ByteArray) -> Unit) {
        var offset = 0
        while (offset < data.size) {
            val (partId, o1) = readUmpVarint(data, offset)
            if (partId < 0) break
            offset = o1

            val (partSize, o2) = readUmpVarint(data, offset)
            if (partSize < 0) break
            offset = o2

            if (offset + partSize > data.size) break
            handler(partId.toInt(), data.copyOfRange(offset, offset + partSize.toInt()))
            offset += partSize.toInt()
        }
    }

    /**
     * YouTube UMP varint encoding (NOT standard protobuf varint):
     *   byte < 128    → 1 byte,  value = b0
     *   byte 128–191  → 2 bytes, value = (b0 & 0x3F) + 64 * b1
     *   byte 192–223  → 3 bytes, value = (b0 & 0x1F) + 32 * (b1 + 256 * b2)
     *   byte 224–239  → 4 bytes, value = (b0 & 0x0F) + 16 * (b1 + 256 * (b2 + 256 * b3))
     *   byte ≥ 240    → 5 bytes, value = b1 + 256 * (b2 + 256 * (b3 + 256 * b4))
     */
    private fun readUmpVarint(data: ByteArray, offset: Int): Pair<Long, Int> {
        if (offset >= data.size) return Pair(-1L, offset)
        val b0 = data[offset].toInt() and 0xFF
        return when {
            b0 < 128 -> Pair(b0.toLong(), offset + 1)
            b0 < 192 -> {
                if (offset + 1 >= data.size) return Pair(-1L, offset)
                Pair(((b0 and 0x3F).toLong() + 64L * (data[offset + 1].toInt() and 0xFF)), offset + 2)
            }
            b0 < 224 -> {
                if (offset + 2 >= data.size) return Pair(-1L, offset)
                val b1 = data[offset + 1].toInt() and 0xFF
                val b2 = data[offset + 2].toInt() and 0xFF
                Pair(((b0 and 0x1F).toLong() + 32L * (b1 + 256L * b2)), offset + 3)
            }
            b0 < 240 -> {
                if (offset + 3 >= data.size) return Pair(-1L, offset)
                val b1 = data[offset + 1].toInt() and 0xFF
                val b2 = data[offset + 2].toInt() and 0xFF
                val b3 = data[offset + 3].toInt() and 0xFF
                Pair(((b0 and 0x0F).toLong() + 16L * (b1 + 256L * (b2 + 256L * b3))), offset + 4)
            }
            else -> {
                if (offset + 4 >= data.size) return Pair(-1L, offset)
                val b1 = data[offset + 1].toInt() and 0xFF
                val b2 = data[offset + 2].toInt() and 0xFF
                val b3 = data[offset + 3].toInt() and 0xFF
                val b4 = data[offset + 4].toInt() and 0xFF
                Pair((b1 + 256L * (b2 + 256L * (b3 + 256L * b4))), offset + 5)
            }
        }
    }
}
