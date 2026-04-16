package com.everythingplayer.kotlinaudio.players

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.IllegalSeekPositionException
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.everythingplayer.kotlinaudio.models.*
import com.everythingplayer.model.TrackAudioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.max
import kotlin.math.min

class QueuedAudioPlayer(
    private val context: Context,
    options: PlayerOptions = PlayerOptions()
) : AudioPlayer(context, options) {

    private val queue = LinkedList<MediaItem>()
    private val queueScope = MainScope()
    private var trimMonitorJob: Job? = null

    /** Duration in seconds for crossfade between tracks. 0 = disabled. */
    var crossfadeDuration: Float = 0f
    private var secondaryPlayer: ExoPlayer? = null
    private var crossfadeJob: Job? = null

    init {
        queueScope.launch {
            playerEventHolder.audioItemTransition.collect {
                handleItemTransitionForTrim()
            }
        }
    }

    var repeatMode: RepeatMode
        get() = when (exoPlayer.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        set(value) {
            when (value) {
                RepeatMode.ALL -> exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                RepeatMode.OFF -> exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            }
        }

    val currentIndex
        get() = exoPlayer.currentMediaItemIndex

    var shuffleMode
        get() = exoPlayer.shuffleModeEnabled
        set(v) { exoPlayer.shuffleModeEnabled = v }

    override val currentItem: AudioItem?
        get() = queue.getOrNull(currentIndex)?.let { AudioItem.fromMediaItem(it) }

    val nextIndex: Int?
        get() = if (exoPlayer.nextMediaItemIndex == C.INDEX_UNSET) null else exoPlayer.nextMediaItemIndex

    val previousIndex: Int?
        get() = if (exoPlayer.previousMediaItemIndex == C.INDEX_UNSET) null else exoPlayer.previousMediaItemIndex

    val items: List<AudioItem>
        get() = queue.map { AudioItem.fromMediaItem(it) }

    val previousItems: List<AudioItem>
        get() = if (queue.isEmpty()) emptyList()
        else queue.subList(0, exoPlayer.currentMediaItemIndex).map { AudioItem.fromMediaItem(it) }

    val nextItems: List<AudioItem>
        get() = if (queue.isEmpty()) emptyList()
        else queue.subList(exoPlayer.currentMediaItemIndex, queue.lastIndex).map { AudioItem.fromMediaItem(it) }

    val nextItem: AudioItem?
        get() = items.getOrNull(currentIndex + 1)

    val previousItem: AudioItem?
        get() = items.getOrNull(currentIndex - 1)

    override fun load(item: AudioItem, playWhenReady: Boolean) {
        load(item)
        exoPlayer.playWhenReady = playWhenReady
    }

    override fun load(item: AudioItem) {
        if (queue.isEmpty()) {
            add(item)
        } else {
            exoPlayer.addMediaItem(currentIndex + 1, item.toMediaItem())
            exoPlayer.removeMediaItem(currentIndex)
            exoPlayer.seekTo(currentIndex, C.TIME_UNSET)
            exoPlayer.prepare()
        }
    }

    fun add(item: AudioItem, playWhenReady: Boolean) {
        exoPlayer.playWhenReady = playWhenReady
        add(item)
    }

    fun add(item: AudioItem) {
        val mediaSource = item.toMediaItem()
        queue.add(mediaSource)
        exoPlayer.addMediaItem(mediaSource)
        exoPlayer.prepare()
    }

    fun add(items: List<AudioItem>, playWhenReady: Boolean) {
        exoPlayer.playWhenReady = playWhenReady
        add(items)
    }

    fun add(items: List<AudioItem>) {
        val mediaItems = items.map { it.toMediaItem() }
        queue.addAll(mediaItems)
        exoPlayer.addMediaItems(mediaItems)
        exoPlayer.prepare()
    }

    fun add(items: List<AudioItem>, atIndex: Int) {
        val mediaItems = items.map { it.toMediaItem() }
        queue.addAll(atIndex, mediaItems)
        exoPlayer.addMediaItems(atIndex, mediaItems)
        exoPlayer.prepare()
    }

    fun remove(index: Int) {
        queue.removeAt(index)
        exoPlayer.removeMediaItem(index)
    }

    fun remove(indexes: List<Int>) {
        val sorted = indexes.toMutableList()
        sorted.sortDescending()
        sorted.forEach { remove(it) }
    }

    fun next() {
        exoPlayer.seekToNextMediaItem()
        exoPlayer.prepare()
    }

    fun previous() {
        exoPlayer.seekToPreviousMediaItem()
        exoPlayer.prepare()
    }

    fun move(fromIndex: Int, toIndex: Int) {
        exoPlayer.moveMediaItem(fromIndex, toIndex)
        val item = queue[fromIndex]
        queue.removeAt(fromIndex)
        queue.add(max(0, min(items.size, if (toIndex > fromIndex) toIndex else toIndex - 1)), item)
    }

    fun jumpToItem(index: Int, playWhenReady: Boolean) {
        exoPlayer.playWhenReady = playWhenReady
        jumpToItem(index)
    }

    fun jumpToItem(index: Int) {
        try {
            exoPlayer.seekTo(index, C.TIME_UNSET)
            exoPlayer.prepare()
        } catch (e: IllegalSeekPositionException) {
            throw Error("This item index $index does not exist. The size of the queue is ${queue.size} items.")
        }
    }

    fun replaceItem(index: Int, item: AudioItem) {
        val mediaItem = item.toMediaItem()
        queue[index] = mediaItem
        exoPlayer.replaceMediaItem(index, mediaItem)
    }

    fun removeUpcomingItems() {
        if (queue.lastIndex == -1 || currentIndex == -1) return
        val lastIndex = queue.lastIndex + 1
        val fromIndex = currentIndex + 1
        exoPlayer.removeMediaItems(fromIndex, lastIndex)
        queue.subList(fromIndex, lastIndex).clear()
    }

    fun removePreviousItems() {
        exoPlayer.removeMediaItems(0, currentIndex)
        queue.subList(0, currentIndex).clear()
    }

    // ── Track trimming ──────────────────────────────────────────────────────

    private fun handleItemTransitionForTrim() {
        val track = (currentItem as? TrackAudioItem)?.track ?: return

        track.startTime?.let { start ->
            if (start > 0) {
                exoPlayer.seekTo((start * 1000).toLong())
            }
        }

        startTrimMonitoring(track)
    }

    private fun startTrimMonitoring(track: com.everythingplayer.model.Track) {
        trimMonitorJob?.cancel()
        crossfadeJob?.cancel()
        val endTimeMs: Long? = track.endTime?.let { (it * 1000).toLong() }

        trimMonitorJob = queueScope.launch {
            var crossfadeStarted = false
            while (isActive) {
                delay(200)
                val pos = exoPlayer.currentPosition
                val crossfadeMs = (crossfadeDuration * 1000).toLong()

                // Determine effective end time (explicit or natural duration)
                val effectiveEndMs: Long = endTimeMs ?: run {
                    val dur = exoPlayer.duration
                    if (dur == C.TIME_UNSET || dur <= 0) Long.MAX_VALUE else dur
                }

                // Start crossfade when approaching the end
                if (!crossfadeStarted && crossfadeDuration > 0 && effectiveEndMs != Long.MAX_VALUE
                    && pos >= effectiveEndMs - crossfadeMs && nextIndex != null) {
                    crossfadeStarted = true
                    startCrossfadeToNext()
                }

                // For explicit endTime, force-advance when reached (crossfade handles natural-end case)
                if (endTimeMs != null && pos >= endTimeMs) {
                    if (crossfadeDuration <= 0) {
                        withContext(Dispatchers.Main) {
                            if (nextIndex != null) next()
                            else {
                                exoPlayer.pause()
                                playerEventHolder.updateAudioPlayerState(AudioPlayerState.ENDED)
                            }
                        }
                    }
                    // When crossfade is active, it will handle the advance at the end of the fade
                    break
                }
            }
        }
    }

    /**
     * Starts a dual-player crossfade to the next track.
     * Creates a secondary ExoPlayer, fades primary out / secondary in over [crossfadeDuration]
     * seconds, then seeks the main ExoPlayer to the next item at the secondary's current position.
     */
    private fun startCrossfadeToNext() {
        val nextIdx = nextIndex ?: return
        val nextMediaItem = queue.getOrNull(nextIdx) ?: return

        // Create secondary player for the next track
        secondaryPlayer?.release()
        val secondary = ExoPlayer.Builder(context).build()
        secondaryPlayer = secondary
        secondary.volume = 0f
        secondary.addMediaItem(nextMediaItem)
        secondary.prepare()
        secondary.playWhenReady = true

        val steps = 20
        val totalMs = (crossfadeDuration * 1000).toLong()
        val stepDelayMs = (totalMs / steps).coerceAtLeast(16)

        crossfadeJob = queueScope.launch {
            for (i in 1..steps) {
                delay(stepDelayMs)
                val fraction = i.toFloat() / steps
                withContext(Dispatchers.Main) {
                    exoPlayer.volume = 1f - fraction
                    secondary.volume = fraction
                }
            }
            withContext(Dispatchers.Main) {
                val syncPos = secondary.currentPosition
                trimMonitorJob?.cancel()
                // Jump main player to next item at secondary's position
                exoPlayer.seekTo(nextIdx, syncPos)
                exoPlayer.volume = 1f
                secondary.release()
                secondaryPlayer = null
            }
        }
    }

    override fun destroy() {
        trimMonitorJob?.cancel()
        crossfadeJob?.cancel()
        secondaryPlayer?.release()
        secondaryPlayer = null
        queueScope.cancel()
        queue.clear()
        super.destroy()
    }

    override fun clear() {
        queue.clear()
        super.clear()
    }
}
