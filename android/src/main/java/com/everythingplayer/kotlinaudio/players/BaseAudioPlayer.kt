package com.everythingplayer.kotlinaudio.players

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.legacy.RatingCompat
import com.everythingplayer.kotlinaudio.event.PlayerEventHolder
import com.everythingplayer.kotlinaudio.models.AudioItem
import com.everythingplayer.kotlinaudio.models.AudioItemTransitionReason
import com.everythingplayer.kotlinaudio.models.AudioPlayerState
import com.everythingplayer.kotlinaudio.models.MediaSessionCallback
import com.everythingplayer.kotlinaudio.models.PlayWhenReadyChangeData
import com.everythingplayer.kotlinaudio.models.PlaybackError
import com.everythingplayer.kotlinaudio.models.PlayerOptions
import com.everythingplayer.kotlinaudio.models.PositionChangedReason
import com.everythingplayer.kotlinaudio.models.setWakeMode
import com.everythingplayer.kotlinaudio.players.components.Cache
import com.everythingplayer.kotlinaudio.players.components.MediaFactory
import com.everythingplayer.kotlinaudio.players.components.setupBuffer
import kotlinx.coroutines.MainScope
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class BaseAudioPlayer internal constructor(
    private val context: Context,
    val options: PlayerOptions = PlayerOptions()
) {

    val exoPlayer: ExoPlayer
    val forwardingPlayer: InnerForwardingPlayer
    val player: Player
        get() = options.interceptPlayerActionsTriggeredExternally
            .takeIf { it }
            ?.let { forwardingPlayer }
            ?: exoPlayer

    private var playerListener = InnerPlayerListener()
    private val scope = MainScope()
    private var cache: SimpleCache? = null
    val playerEventHolder = PlayerEventHolder()

    private var wasDucking = false
    private val focusManager: FocusManager = FocusManager()

    var alwaysPauseOnInterruption: Boolean
        get() = options.alwaysPauseOnInterruption
        set(v) { options.alwaysPauseOnInterruption = v }

    open val currentItem: AudioItem?
        get() = exoPlayer.currentMediaItem?.let { AudioItem.fromMediaItem(it) }

    var playbackError: PlaybackError? = null
    var playerState: AudioPlayerState = AudioPlayerState.IDLE
        private set(value) {
            if (value != field) {
                field = value
                playerEventHolder.updateAudioPlayerState(value)
                if (!options.handleAudioFocus) {
                    when (value) {
                        AudioPlayerState.IDLE,
                        AudioPlayerState.ERROR -> focusManager.abandonAudioFocusIfHeld()
                        AudioPlayerState.READY -> focusManager.requestAudioFocus()
                        else -> {}
                    }
                }
            }
        }

    var playWhenReady: Boolean
        get() = exoPlayer.playWhenReady
        set(value) { exoPlayer.playWhenReady = value }

    val duration: Long
        get() = if (exoPlayer.duration == C.TIME_UNSET) 0 else exoPlayer.duration

    val isCurrentMediaItemLive: Boolean
        get() = exoPlayer.isCurrentMediaItemLive

    private var oldPosition = 0L

    val position: Long
        get() = if (exoPlayer.currentPosition == C.INDEX_UNSET.toLong()) 0 else exoPlayer.currentPosition

    val bufferedPosition: Long
        get() = if (exoPlayer.bufferedPosition == C.INDEX_UNSET.toLong()) 0 else exoPlayer.bufferedPosition

    private var volumeMultiplier = 1f
        private set(value) {
            field = value
            volume = volume
        }

    var volume: Float
        get() = exoPlayer.volume
        set(value) { exoPlayer.volume = value * volumeMultiplier }

    var playbackSpeed: Float
        get() = exoPlayer.playbackParameters.speed
        set(value) { exoPlayer.setPlaybackSpeed(value) }

    val isPlaying
        get() = exoPlayer.isPlaying

    var ratingType: Int = RatingCompat.RATING_NONE

    // ── Equalizer ────────────────────────────────────────────────────────────
    private var equalizer: Equalizer? = null

    /**
     * Apply a multi-band equalizer. Each bundle has "frequency" (Hz, Float) and "gain" (dB, Float).
     * Bands are matched to the device EQ's center frequencies by closest frequency.
     */
    fun setEqualizer(bands: List<Bundle>) {
        equalizer?.release()
        val sessionId = exoPlayer.audioSessionId
        if (sessionId == 0 || sessionId == -1) return
        equalizer = Equalizer(0, sessionId).apply {
            enabled = true
            val numBands = numberOfBands.toInt()
            bands.forEach { band ->
                val freqHz = band.getFloat("frequency", 0f)
                val gainDb = band.getFloat("gain", 0f)

                var closestBand = 0
                var minDiff = Float.MAX_VALUE
                for (i in 0 until numBands) {
                    val centerHz = getCenterFreq(i.toShort()) / 1000f  // milliHz → Hz
                    val diff = Math.abs(centerHz - freqHz)
                    if (diff < minDiff) {
                        minDiff = diff
                        closestBand = i
                    }
                }
                setBandLevel(closestBand.toShort(), (gainDb * 100).toInt().toShort())
            }
        }
    }

    fun removeEqualizer() {
        equalizer?.release()
        equalizer = null
    }

    fun getEqualizerBands(): List<Bundle> {
        val eq = equalizer ?: return emptyList()
        val numBands = eq.numberOfBands.toInt()
        return (0 until numBands).map { i ->
            Bundle().apply {
                putFloat("frequency", eq.getCenterFreq(i.toShort()) / 1000f)
                putFloat("gain", eq.getBandLevel(i.toShort()) / 100f)
            }
        }
    }

    fun setAudioOffload(offload: Boolean = true) {
        val audioOffloadPreferences =
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (offload) TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                )
                .setIsGaplessSupportRequired(true)
                .setIsSpeedChangeSupportRequired(true)
                .build()
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(audioOffloadPreferences)
            .build()
    }

    init {
        if (options.cacheSizeKb > 0) {
            cache = Cache.initCache(context, options.cacheSizeKb)
        }
        playerEventHolder.updateAudioPlayerState(AudioPlayerState.IDLE)

        val renderer = DefaultRenderersFactory(context)
        renderer.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        exoPlayer = ExoPlayer
            .Builder(context)
            .setRenderersFactory(renderer)
            .setHandleAudioBecomingNoisy(options.handleAudioBecomingNoisy)
            .setMediaSourceFactory(MediaFactory(context, cache))
            .setWakeMode(setWakeMode(options.wakeMode))
            .apply { setLoadControl(setupBuffer(options.bufferOptions)) }
            .setSkipSilenceEnabled(options.skipSilence)
            .setName("everything-audio-player")
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(options.audioContentType)
            .build()
        exoPlayer.setAudioAttributes(audioAttributes, options.handleAudioFocus)
        forwardingPlayer = InnerForwardingPlayer(exoPlayer)
        player.addListener(playerListener)
    }

    open fun load(item: AudioItem, playWhenReady: Boolean = true) {
        exoPlayer.playWhenReady = playWhenReady
        load(item)
    }

    open fun load(item: AudioItem) {
        exoPlayer.addMediaItem(item.toMediaItem())
        exoPlayer.prepare()
    }

    fun togglePlaying() {
        if (exoPlayer.isPlaying) pause() else play()
    }

    var skipSilence: Boolean
        get() = exoPlayer.skipSilenceEnabled
        set(value) { exoPlayer.skipSilenceEnabled = value }

    fun play() {
        exoPlayer.play()
        if (currentItem != null) exoPlayer.prepare()
    }

    fun prepare() {
        if (currentItem != null) exoPlayer.prepare()
    }

    fun pause() {
        exoPlayer.pause()
    }

    @CallSuper
    open fun stop() {
        playerState = AudioPlayerState.STOPPED
        exoPlayer.playWhenReady = false
        exoPlayer.stop()
    }

    @CallSuper
    open fun clear() {
        exoPlayer.clearMediaItems()
    }

    fun setPauseAtEndOfItem(pause: Boolean) {
        exoPlayer.pauseAtEndOfMediaItems = pause
    }

    @CallSuper
    open fun destroy() {
        focusManager.abandonAudioFocusIfHeld()
        removeEqualizer()
        stop()
        player.removeListener(playerListener)
        exoPlayer.release()
        cache?.release()
        cache = null
    }

    open fun seek(duration: Long, unit: TimeUnit) {
        val positionMs = TimeUnit.MILLISECONDS.convert(duration, unit)
        exoPlayer.seekTo(positionMs)
    }

    open fun seekBy(offset: Long, unit: TimeUnit) {
        val positionMs = exoPlayer.currentPosition + TimeUnit.MILLISECONDS.convert(offset, unit)
        exoPlayer.seekTo(positionMs)
    }

    @UnstableApi
    inner class InnerPlayerListener : Listener {

        override fun onMetadata(metadata: Metadata) {
            playerEventHolder.updateOnTimedMetadata(metadata)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            playerEventHolder.updateOnCommonMetadata(mediaMetadata)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            this@BaseAudioPlayer.oldPosition = oldPosition.positionMs

            when (reason) {
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> playerEventHolder.updatePositionChangedReason(
                    PositionChangedReason.AUTO(oldPosition.positionMs, newPosition.positionMs)
                )
                Player.DISCONTINUITY_REASON_SEEK -> playerEventHolder.updatePositionChangedReason(
                    PositionChangedReason.SEEK(oldPosition.positionMs, newPosition.positionMs)
                )
                Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> playerEventHolder.updatePositionChangedReason(
                    PositionChangedReason.SEEK_FAILED(oldPosition.positionMs, newPosition.positionMs)
                )
                Player.DISCONTINUITY_REASON_REMOVE -> playerEventHolder.updatePositionChangedReason(
                    PositionChangedReason.QUEUE_CHANGED(oldPosition.positionMs, newPosition.positionMs)
                )
                Player.DISCONTINUITY_REASON_SKIP -> playerEventHolder.updatePositionChangedReason(
                    PositionChangedReason.SKIPPED_PERIOD(oldPosition.positionMs, newPosition.positionMs)
                )
                else -> playerEventHolder.updatePositionChangedReason(
                    PositionChangedReason.UNKNOWN(oldPosition.positionMs, newPosition.positionMs)
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> playerEventHolder.updateAudioItemTransition(
                    AudioItemTransitionReason.AUTO(oldPosition)
                )
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> playerEventHolder.updateAudioItemTransition(
                    AudioItemTransitionReason.QUEUE_CHANGED(oldPosition)
                )
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> playerEventHolder.updateAudioItemTransition(
                    AudioItemTransitionReason.REPEAT(oldPosition)
                )
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> playerEventHolder.updateAudioItemTransition(
                    AudioItemTransitionReason.SEEK_TO_ANOTHER_AUDIO_ITEM(oldPosition)
                )
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val pausedBecauseReachedEnd = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
            playerEventHolder.updatePlayWhenReadyChange(PlayWhenReadyChangeData(playWhenReady, pausedBecauseReachedEnd))
        }

        override fun onEvents(player: Player, events: Player.Events) {
            for (i in 0 until events.size()) {
                when (events[i]) {
                    Player.EVENT_PLAYBACK_STATE_CHANGED -> {
                        val state = when (player.playbackState) {
                            Player.STATE_BUFFERING -> AudioPlayerState.BUFFERING
                            Player.STATE_READY -> AudioPlayerState.READY
                            Player.STATE_IDLE ->
                                if (playerState == AudioPlayerState.ERROR || playerState == AudioPlayerState.STOPPED) null
                                else AudioPlayerState.IDLE
                            Player.STATE_ENDED ->
                                if (player.mediaItemCount > 0) AudioPlayerState.ENDED
                                else AudioPlayerState.IDLE
                            else -> null
                        }
                        if (state != null && state != playerState) {
                            playerState = state
                        }
                    }
                    Player.EVENT_MEDIA_ITEM_TRANSITION -> {
                        playbackError = null
                        if (currentItem != null) {
                            playerState = AudioPlayerState.LOADING
                            if (isPlaying) {
                                playerState = AudioPlayerState.READY
                                playerState = AudioPlayerState.PLAYING
                            }
                        }
                    }
                    Player.EVENT_PLAY_WHEN_READY_CHANGED -> {
                        if (!player.playWhenReady && playerState != AudioPlayerState.STOPPED) {
                            playerState = AudioPlayerState.PAUSED
                        }
                    }
                    Player.EVENT_IS_PLAYING_CHANGED -> {
                        if (player.isPlaying) {
                            playerState = AudioPlayerState.PLAYING
                        }
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val _playbackError = PlaybackError(
                error.errorCodeName
                    .replace("ERROR_CODE_", "")
                    .lowercase(Locale.getDefault())
                    .replace("_", "-"),
                error.message
            )
            playerEventHolder.updatePlaybackError(_playbackError)
            playbackError = _playbackError
            playerState = AudioPlayerState.ERROR
        }
    }

    @UnstableApi
    inner class InnerForwardingPlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) = Unit
        override fun addMediaItems(mediaItems: MutableList<MediaItem>) = Unit
        override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) = Unit
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) = Unit
        override fun setMediaItems(mediaItems: MutableList<MediaItem>) = Unit

        override fun play() {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.PLAY)
        }
        override fun pause() {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.PAUSE)
        }
        override fun seekToNext() {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.NEXT)
        }
        override fun seekToNextMediaItem() {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.NEXT)
        }
        override fun seekToPrevious() {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.PREVIOUS)
        }
        override fun seekToPreviousMediaItem() {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.PREVIOUS)
        }
        override fun seekForward() {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.FORWARD)
        }
        override fun seekBack() {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.REWIND)
        }
        override fun stop() {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.STOP)
        }
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.SEEK(positionMs))
        }
        override fun seekTo(positionMs: Long) {
            playerEventHolder.updateOnPlayerActionTriggeredExternally(MediaSessionCallback.SEEK(positionMs))
        }
    }

    inner class FocusManager {
        private var hasAudioFocus = false
        private var focus: AudioFocusRequestCompat? = null

        fun requestAudioFocus() {
            if (hasAudioFocus) return
            val manager = ContextCompat.getSystemService(context, AudioManager::class.java)
            focus = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { focusChange ->
                    Timber.d("Audio focus changed")
                    val isPermanent = focusChange == AudioManager.AUDIOFOCUS_LOSS
                    val isPaused = when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> true
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> options.alwaysPauseOnInterruption
                        else -> false
                    }
                    if (!options.handleAudioFocus) {
                        if (isPermanent) focusManager.abandonAudioFocusIfHeld()
                        val isDucking = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                                && !options.alwaysPauseOnInterruption
                        if (isDucking) {
                            volumeMultiplier = 0.5f
                            wasDucking = true
                        } else if (wasDucking) {
                            volumeMultiplier = 1f
                            wasDucking = false
                        }
                    }
                    playerEventHolder.updateOnAudioFocusChanged(isPaused, isPermanent)
                }
                .setAudioAttributes(
                    AudioAttributesCompat.Builder()
                        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setWillPauseWhenDucked(options.alwaysPauseOnInterruption)
                .build()

            val result = if (manager != null && focus != null) {
                AudioManagerCompat.requestAudioFocus(manager, focus!!)
            } else {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }

        fun abandonAudioFocusIfHeld() {
            if (!hasAudioFocus) return
            val manager = ContextCompat.getSystemService(context, AudioManager::class.java)
            val result = if (manager != null && focus != null) {
                AudioManagerCompat.abandonAudioFocusRequest(manager, focus!!)
            } else {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }
            hasAudioFocus = (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
    }
}
