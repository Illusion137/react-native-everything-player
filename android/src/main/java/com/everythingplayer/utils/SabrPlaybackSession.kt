package com.everythingplayer.utils

import androidx.media3.common.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class SabrPlaybackSessionConfig(
    val sessionId: String,
    val sabrConfig: SabrConfig,
    val mimeType: String?
)

class SabrPlaybackSession(private val config: SabrPlaybackSessionConfig) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val buffer = StreamBuffer()
    private val downloader = SabrDownloader(config.sabrConfig)
    private val lock = ReentrantLock()
    private var started = false
    private var closed = false

    var onRefreshPoToken: ((String) -> Unit)?
        get() = downloader.onRefreshPoToken
        set(value) {
            downloader.onRefreshPoToken = value
        }

    var onReloadPlayerResponse: ((String?) -> Unit)?
        get() = downloader.onReloadPlayerResponse
        set(value) {
            downloader.onReloadPlayerResponse = value
        }

    val mimeType: String?
        get() = config.mimeType

    fun start() {
        lock.withLock {
            if (started || closed) return
            started = true
        }
        scope.launch {
            try {
                downloader.stream(startTimeMs = config.sabrConfig.startTimeMs) { bytes ->
                    buffer.write(bytes)
                }
                buffer.finish()
            } catch (error: Exception) {
                buffer.fail(error)
            } finally {
                SabrPlaybackRegistry.remove(config.sessionId)
            }
        }
    }

    fun read(targetAbsolutePosition: Long, target: ByteArray, offset: Int, length: Int): Int {
        return buffer.read(targetAbsolutePosition, target, offset, length)
    }

    fun updateStream(serverUrl: String, ustreamerConfig: String) {
        downloader.updateStream(serverUrl, ustreamerConfig)
    }

    fun updatePoToken(poToken: String) {
        downloader.updatePoToken(poToken)
    }

    fun close() {
        lock.withLock {
            if (closed) return
            closed = true
        }
        downloader.abort()
        buffer.finish()
        scope.cancel()
    }

    private class StreamBuffer {
        private val lock = ReentrantLock()
        private val dataReady = lock.newCondition()
        private val chunks = mutableListOf<ByteArray>()
        private val chunkOffsets = mutableListOf<Long>()
        private var totalBufferedBytes = 0L
        private var finished = false
        private var failure: IOException? = null

        fun write(bytes: ByteArray) {
            if (bytes.isEmpty()) return
            lock.withLock {
                if (finished || failure != null) return
                chunkOffsets.add(totalBufferedBytes)
                chunks.add(bytes)
                totalBufferedBytes += bytes.size
                dataReady.signalAll()
            }
        }

        fun finish() {
            lock.withLock {
                finished = true
                dataReady.signalAll()
            }
        }

        fun fail(error: Exception) {
            lock.withLock {
                failure = if (error is IOException) error else IOException(error)
                finished = true
                dataReady.signalAll()
            }
        }

        fun read(targetAbsolutePosition: Long, target: ByteArray, offset: Int, length: Int): Int {
            lock.withLock {
                while (true) {
                    val availableBytes = totalBufferedBytes - targetAbsolutePosition
                    if (availableBytes > 0) {
                        return copyBuffered(
                            targetAbsolutePosition = targetAbsolutePosition,
                            target = target,
                            offset = offset,
                            length = minOf(length.toLong(), availableBytes).toInt()
                        )
                    }
                    failure?.let { throw it }
                    if (finished) return C.RESULT_END_OF_INPUT
                    dataReady.await()
                }
            }
        }

        private fun copyBuffered(
            targetAbsolutePosition: Long,
            target: ByteArray,
            offset: Int,
            length: Int
        ): Int {
            if (length == 0) return 0

            var chunkIndex = findChunkIndex(targetAbsolutePosition)
            if (chunkIndex < 0) return 0

            var copied = 0
            var readPosition = targetAbsolutePosition
            while (copied < length && chunkIndex < chunks.size) {
                val chunk = chunks[chunkIndex]
                val chunkStart = chunkOffsets[chunkIndex]
                val chunkOffset = (readPosition - chunkStart).toInt().coerceAtLeast(0)
                if (chunkOffset >= chunk.size) {
                    chunkIndex++
                    continue
                }

                val bytesToCopy = minOf(length - copied, chunk.size - chunkOffset)
                System.arraycopy(chunk, chunkOffset, target, offset + copied, bytesToCopy)
                copied += bytesToCopy
                readPosition += bytesToCopy
                if (chunkOffset + bytesToCopy >= chunk.size) {
                    chunkIndex++
                }
            }
            return copied
        }

        private fun findChunkIndex(targetAbsolutePosition: Long): Int {
            if (chunks.isEmpty()) return -1
            var low = 0
            var high = chunkOffsets.lastIndex
            while (low <= high) {
                val mid = (low + high) ushr 1
                val chunkStart = chunkOffsets[mid]
                val chunkEnd = chunkStart + chunks[mid].size
                when {
                    targetAbsolutePosition < chunkStart -> high = mid - 1
                    targetAbsolutePosition >= chunkEnd -> low = mid + 1
                    else -> return mid
                }
            }
            return if (targetAbsolutePosition == totalBufferedBytes) chunks.lastIndex else -1
        }
    }
}

object SabrPlaybackRegistry {
    private val sessions = ConcurrentHashMap<String, SabrPlaybackSession>()

    fun getOrCreate(config: SabrPlaybackSessionConfig): SabrPlaybackSession {
        return sessions.compute(config.sessionId) { _, existing ->
            existing ?: SabrPlaybackSession(config)
        }!!
    }

    fun get(sessionId: String): SabrPlaybackSession? = sessions[sessionId]

    fun remove(sessionId: String) {
        sessions.remove(sessionId)?.close()
    }

    fun clear() {
        sessions.values.toList().forEach { it.close() }
        sessions.clear()
    }
}
