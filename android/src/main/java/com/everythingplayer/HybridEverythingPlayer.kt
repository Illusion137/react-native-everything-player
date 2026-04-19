@file:OptIn(UnstableApi::class)
package com.everythingplayer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.RatingCompat
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.everythingplayer.kotlinaudio.models.Capability
import com.everythingplayer.kotlinaudio.models.RepeatMode
import com.everythingplayer.model.State
import com.everythingplayer.model.Track
import com.everythingplayer.utils.AppForegroundTracker
import com.everythingplayer.utils.RejectionException
import com.everythingplayer.utils.SabrConfig
import com.everythingplayer.utils.SabrDownloader
import com.everythingplayer.utils.SabrFormatDescriptor
import com.margelo.nitro.NitroModules
import com.margelo.nitro.com.everythingplayer.HybridNativeEverythingPlayerSpec
import com.margelo.nitro.com.everythingplayer.HybridVideoView
import com.margelo.nitro.com.everythingplayer.Variant_NullType__event__AnyMap_____Unit
import com.margelo.nitro.com.everythingplayer.Variant_NullType_______Unit
import com.margelo.nitro.com.everythingplayer.Variant_NullType_AnyMap
import com.margelo.nitro.com.everythingplayer.Variant_NullType_Double
import com.margelo.nitro.core.AnyMap
import com.margelo.nitro.core.NullType
import com.margelo.nitro.core.Promise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Suppress("unused")
open class HybridEverythingPlayer : HybridNativeEverythingPlayerSpec(), ServiceConnection {

    // ─── Nitro Callback Properties ────────────────────────────────────────────

    override var onPlaybackStateChanged: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onPlaybackError: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onPlaybackQueueEnded: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onActiveTrackChanged: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onPlayWhenReadyChanged: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onProgressUpdated: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onPlaybackMetadata: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onRemotePlay: Variant_NullType_______Unit = Variant_NullType_______Unit.create(NullType.NULL)
    override var onRemotePause: Variant_NullType_______Unit = Variant_NullType_______Unit.create(NullType.NULL)
    override var onRemoteStop: Variant_NullType_______Unit = Variant_NullType_______Unit.create(NullType.NULL)
    override var onRemoteNext: Variant_NullType_______Unit = Variant_NullType_______Unit.create(NullType.NULL)
    override var onRemotePrevious: Variant_NullType_______Unit = Variant_NullType_______Unit.create(NullType.NULL)
    override var onRemoteJumpForward: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onRemoteJumpBackward: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onRemoteSeek: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onRemoteSetRating: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onRemoteDuck: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onRemoteLike: Variant_NullType_______Unit = Variant_NullType_______Unit.create(NullType.NULL)
    override var onRemoteDislike: Variant_NullType_______Unit = Variant_NullType_______Unit.create(NullType.NULL)
    override var onRemoteBookmark: Variant_NullType_______Unit = Variant_NullType_______Unit.create(NullType.NULL)
    override var onChapterMetadataReceived: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onTimedMetadataReceived: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onCommonMetadataReceived: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onSabrDownloadProgress: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onSabrReloadPlayerResponse: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onSabrRefreshPoToken: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onAndroidControllerConnected: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onAndroidControllerDisconnected: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)
    override var onPlaybackResume: Variant_NullType__event__AnyMap_____Unit = Variant_NullType__event__AnyMap_____Unit.create(NullType.NULL)

    // ─── Internal State ───────────────────────────────────────────────────────

    private val scope = MainScope()
    private var isServiceBound = false
    private lateinit var musicService: MusicService
    private val sabrDownloaders = mutableMapOf<String, SabrDownloader>()
    private val context get() = NitroModules.applicationContext!!

    /** Weak-ish reference to the currently attached VideoView (nullable). */
    private var attachedVideoView: HybridVideoView? = null

    // ─── Service Connection ───────────────────────────────────────────────────

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        scope.launch {
            if (!::musicService.isInitialized) {
                val binder = service as MusicService.MusicBinder
                musicService = binder.service
                installNitroEventCallback()
            }
            isServiceBound = true
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        scope.launch { isServiceBound = false }
    }

    private fun installNitroEventCallback() {
        musicService.nitroEventCallback = { eventName, data ->
            val anyMap = AnyMap.fromMap(data, true)
            dispatchEvent(eventName, anyMap)
        }
    }

    private fun dispatchEvent(eventName: String, anyMap: AnyMap) {
        when (eventName) {
            MusicEvents.PLAYBACK_STATE -> onPlaybackStateChanged.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.PLAYBACK_ERROR -> onPlaybackError.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.PLAYBACK_QUEUE_ENDED -> onPlaybackQueueEnded.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.PLAYBACK_ACTIVE_TRACK_CHANGED -> {
                onActiveTrackChanged.asSecondOrNull()?.invoke(anyMap)
                attachedVideoView?.let { view ->
                    val track = anyMap.toHashMap()["track"] as? Map<*, *>
                    val artwork = track?.get("artwork") as? String
                    view.showThumbnail(artwork)
                    if (!trackPayloadHasVideo(track)) {
                        view.clearVideo()
                    }
                    // For video tracks, the first-frame listener on ExoPlayer will
                    // call showVideoSurface() once a frame is actually decoded.
                }
            }
            MusicEvents.PLAYBACK_PLAY_WHEN_READY_CHANGED -> onPlayWhenReadyChanged.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.PLAYBACK_PROGRESS_UPDATED -> onProgressUpdated.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.PLAYBACK_METADATA -> onPlaybackMetadata.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.PLAYBACK_RESUME -> onPlaybackResume.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.BUTTON_PLAY -> onRemotePlay.asSecondOrNull()?.invoke()
            MusicEvents.BUTTON_PAUSE -> onRemotePause.asSecondOrNull()?.invoke()
            MusicEvents.BUTTON_STOP -> onRemoteStop.asSecondOrNull()?.invoke()
            MusicEvents.BUTTON_SKIP_NEXT -> onRemoteNext.asSecondOrNull()?.invoke()
            MusicEvents.BUTTON_SKIP_PREVIOUS -> onRemotePrevious.asSecondOrNull()?.invoke()
            MusicEvents.BUTTON_JUMP_FORWARD -> onRemoteJumpForward.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.BUTTON_JUMP_BACKWARD -> onRemoteJumpBackward.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.BUTTON_SEEK_TO -> onRemoteSeek.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.BUTTON_SET_RATING -> onRemoteSetRating.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.BUTTON_DUCK -> onRemoteDuck.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.METADATA_TIMED_RECEIVED -> onTimedMetadataReceived.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.METADATA_COMMON_RECEIVED -> onCommonMetadataReceived.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.CONNECTOR_CONNECTED -> onAndroidControllerConnected.asSecondOrNull()?.invoke(anyMap)
            MusicEvents.CONNECTOR_DISCONNECTED -> onAndroidControllerDisconnected.asSecondOrNull()?.invoke(anyMap)
        }
    }

    private fun verifyServiceBound() {
        if (!isServiceBound) throw RuntimeException("player_not_initialized: Call setupPlayer first.")
    }

    private fun bundleToTrack(bundle: Bundle): Track = Track(context, bundle, musicService.ratingType)

    private fun anyMapToBundle(map: AnyMap): Bundle = hashMapToBundle(map.toHashMap())

    private fun hashMapToBundle(map: Map<String, Any?>): Bundle {
        val bundle = Bundle()
        for ((key, value) in map) {
            when (value) {
                is String -> bundle.putString(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putInt(key, value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    bundle.putBundle(key, hashMapToBundle(value as Map<String, Any?>))
                }
                is List<*> -> {
                    when {
                        value.all { it is Map<*, *> } -> {
                            @Suppress("UNCHECKED_CAST")
                            val bundles = value.map { item -> hashMapToBundle(item as Map<String, Any?>) }
                            bundle.putSerializable(key, ArrayList(bundles))
                        }
                        value.all { it is String } -> {
                            bundle.putStringArrayList(key, ArrayList(value.filterIsInstance<String>()))
                        }
                        value.all { it is Int } -> {
                            bundle.putIntegerArrayList(key, ArrayList(value.filterIsInstance<Int>()))
                        }
                        value.all { it is Double } -> {
                            bundle.putDoubleArray(key, value.filterIsInstance<Double>().toDoubleArray())
                        }
                        value.all { it is Boolean } -> {
                            val booleans = value.filterIsInstance<Boolean>()
                            bundle.putBooleanArray(key, BooleanArray(booleans.size) { booleans[it] })
                        }
                    }
                }
                else -> {}
            }
        }
        return bundle
    }

    private fun anyMapToTrack(map: AnyMap): Track = bundleToTrack(anyMapToBundle(map))

    private fun <T> wrapAsync(run: suspend () -> T): Promise<Promise<T>> {
        return Promise.resolved(
            Promise.async {
                withContext(Dispatchers.Main.immediate) {
                    run()
                }
            }
        )
    }

    private fun <T> wrapBackgroundAsync(run: suspend () -> T): Promise<Promise<T>> {
        return Promise.resolved(Promise.async(run = run))
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    override var setupPlayer = { options: AnyMap ->
        wrapAsync {
            if (isServiceBound) throw RuntimeException("player_already_initialized")
            AppForegroundTracker.start()
            val playerOptions = anyMapToBundle(options)
            Intent(context, MusicService::class.java).also { intent ->
                context.bindService(intent, this@HybridEverythingPlayer, Context.BIND_AUTO_CREATE)
            }
            // Wait for service to connect
            var waited = 0
            while (!isServiceBound && waited < 10000) {
                delay(50)
                waited += 50
            }
            if (!isServiceBound) throw RuntimeException("player_setup_timeout")
            musicService.setupPlayer(playerOptions)
            shared = this@HybridEverythingPlayer
        }
    }

    override var updateOptions = { options: AnyMap ->
        wrapAsync {
            verifyServiceBound()
            musicService.updateOptions(anyMapToBundle(options))
        }
    }

    // ─── Queue ────────────────────────────────────────────────────────────────

    override var add = { tracks: Array<AnyMap>, insertBeforeIndex: Variant_NullType_Double? ->
        wrapAsync {
            verifyServiceBound()
            val insertIdx = insertBeforeIndex?.asSecondOrNull()?.toInt() ?: -1
            val trackList = tracks.map { anyMapToTrack(it) }
            val index = if (insertIdx < 0 || insertIdx > musicService.tracks.size) {
                musicService.tracks.size
            } else insertIdx
            musicService.add(trackList, index)
            Variant_NullType_Double.create(index.toDouble())
        }
    }

    override var load = { track: AnyMap ->
        wrapAsync {
            verifyServiceBound()
            musicService.load(anyMapToTrack(track))
            Variant_NullType_Double.create(NullType.NULL)
        }
    }

    override var move = { fromIndex: Double, toIndex: Double ->
        wrapAsync {
            verifyServiceBound()
            musicService.move(fromIndex.toInt(), toIndex.toInt())
        }
    }

    override var remove = { indexes: DoubleArray ->
        wrapAsync {
            verifyServiceBound()
            val indexInts = indexes.map { it.toInt() }.toMutableList() as ArrayList<Int>
            musicService.remove(indexInts)
        }
    }

    override var removeUpcomingTracks = {
        wrapAsync {
            verifyServiceBound()
            musicService.removeUpcomingTracks()
        }
    }

    override var skip = { index: Double, initialPosition: Variant_NullType_Double? ->
        wrapAsync {
            verifyServiceBound()
            musicService.skip(index.toInt())
            val pos = initialPosition?.asSecondOrNull()
            if (pos != null && pos >= 0) musicService.seekTo(pos.toFloat())
        }
    }

    override var skipToNext = { initialPosition: Variant_NullType_Double? ->
        wrapAsync {
            verifyServiceBound()
            musicService.skipToNext()
            val pos = initialPosition?.asSecondOrNull()
            if (pos != null && pos >= 0) musicService.seekTo(pos.toFloat())
        }
    }

    override var skipToPrevious = { initialPosition: Variant_NullType_Double? ->
        wrapAsync {
            verifyServiceBound()
            musicService.skipToPrevious()
            val pos = initialPosition?.asSecondOrNull()
            if (pos != null && pos >= 0) musicService.seekTo(pos.toFloat())
        }
    }

    override var setQueue = { tracks: Array<AnyMap> ->
        wrapAsync {
            verifyServiceBound()
            musicService.clear()
            musicService.add(tracks.map { anyMapToTrack(it) })
        }
    }

    override var getQueue = {
        wrapAsync {
            verifyServiceBound()
            musicService.tracks.map { AnyMap.fromMap(bundleToMap(it.originalItem), true) }.toTypedArray()
        }
    }

    override var getTrack = { index: Double ->
        wrapAsync {
            verifyServiceBound()
            val idx = index.toInt()
            if (idx >= 0 && idx < musicService.tracks.size) {
                Variant_NullType_AnyMap.create(AnyMap.fromMap(bundleToMap(musicService.tracks[idx].originalItem), true))
            } else {
                Variant_NullType_AnyMap.create(NullType.NULL)
            }
        }
    }

    override var getActiveTrackIndex = {
        wrapAsync {
            verifyServiceBound()
            if (musicService.tracks.isEmpty()) Variant_NullType_Double.create(NullType.NULL)
            else Variant_NullType_Double.create(musicService.getCurrentTrackIndex().toDouble())
        }
    }

    override var getActiveTrack = {
        wrapAsync {
            verifyServiceBound()
            val track = musicService.currentTrack
            if (track != null) Variant_NullType_AnyMap.create(AnyMap.fromMap(bundleToMap(track.originalItem), true))
            else Variant_NullType_AnyMap.create(NullType.NULL)
        }
    }

    override var updateMetadataForTrack = { trackIndex: Double, metadata: AnyMap ->
        wrapAsync {
            verifyServiceBound()
            musicService.updateMetadataForTrack(trackIndex.toInt(), anyMapToBundle(metadata))
        }
    }

    override var updateNowPlayingMetadata = { metadata: AnyMap ->
        wrapAsync {
            verifyServiceBound()
            musicService.updateNowPlayingMetadata(anyMapToBundle(metadata))
        }
    }

    // ─── Playback Control ─────────────────────────────────────────────────────

    override var reset = {
        wrapAsync {
            verifyServiceBound()
            musicService.stop()
            delay(300)
            musicService.clear()
        }
    }

    override var play = {
        wrapAsync {
            verifyServiceBound()
            musicService.play()
        }
    }

    override var pause = {
        wrapAsync {
            verifyServiceBound()
            musicService.pause()
        }
    }

    override var stop = {
        wrapAsync {
            verifyServiceBound()
            musicService.stop()
        }
    }

    override var retry = {
        wrapAsync {
            verifyServiceBound()
            musicService.retry()
        }
    }

    override var setPlayWhenReady = { playWhenReady: Boolean ->
        wrapAsync {
            verifyServiceBound()
            musicService.playWhenReady = playWhenReady
        }
    }

    override var getPlayWhenReady = {
        wrapAsync {
            verifyServiceBound()
            musicService.playWhenReady
        }
    }

    override var seekTo = { position: Double ->
        wrapAsync {
            verifyServiceBound()
            musicService.seekTo(position.toFloat())
        }
    }

    override var seekBy = { offset: Double ->
        wrapAsync {
            verifyServiceBound()
            musicService.seekBy(offset.toFloat())
        }
    }

    override var setVolume = { level: Double ->
        wrapAsync {
            verifyServiceBound()
            musicService.setVolume(level.toFloat())
        }
    }

    override var getVolume = {
        wrapAsync {
            verifyServiceBound()
            musicService.getVolume().toDouble()
        }
    }

    override var setRate = { rate: Double ->
        wrapAsync {
            verifyServiceBound()
            musicService.setRate(rate.toFloat())
        }
    }

    override var getRate = {
        wrapAsync {
            verifyServiceBound()
            musicService.getRate().toDouble()
        }
    }

    override var setRepeatMode = { mode: Double ->
        wrapAsync {
            verifyServiceBound()
            musicService.setRepeatMode(RepeatMode.fromOrdinal(mode.toInt()))
        }
    }

    override var getRepeatMode = {
        wrapAsync {
            verifyServiceBound()
            musicService.getRepeatMode().ordinal.toDouble()
        }
    }

    override var getProgress = {
        wrapAsync {
            verifyServiceBound()
            AnyMap.fromMap(mapOf(
                "duration" to musicService.getDurationInSeconds(),
                "position" to musicService.getPositionInSeconds(),
                "buffered" to musicService.getBufferedPositionInSeconds()
            ), false)
        }
    }

    override var getPlaybackState = {
        wrapAsync {
            verifyServiceBound()
            AnyMap.fromMap(bundleToMap(musicService.getPlayerStateBundle(musicService.state)), true)
        }
    }

    // ─── Audio Effects ────────────────────────────────────────────────────────

    override var setCrossFade = { seconds: Double ->
        wrapAsync {
            verifyServiceBound()
            musicService.setCrossFade(seconds)
        }
    }

    override var setEqualizer = { bands: Array<AnyMap> ->
        wrapAsync {
            verifyServiceBound()
            val bandBundles = bands.map { band ->
                val bandMap = band.toHashMap()
                Bundle().apply {
                    putFloat("frequency", (bandMap["frequency"] as? Double)?.toFloat() ?: 0f)
                    putFloat("gain", (bandMap["gain"] as? Double)?.toFloat() ?: 0f)
                }
            }
            musicService.setEqualizer(bandBundles)
        }
    }

    override var getEqualizer = {
        wrapAsync {
            verifyServiceBound()
            musicService.getEqualizerBands().map { band ->
                AnyMap.fromMap(mapOf(
                    "frequency" to (band.getFloat("frequency").toDouble()),
                    "gain" to (band.getFloat("gain").toDouble())
                ), false)
            }.toTypedArray()
        }
    }

    override var removeEqualizer = {
        wrapAsync {
            verifyServiceBound()
            musicService.removeEqualizer()
        }
    }

    // ─── SABR ─────────────────────────────────────────────────────────────────

    override var downloadSabrStream = { params: AnyMap, outputPath: String ->
        wrapBackgroundAsync {
            val p = params.toHashMap()
            val serverUrl = p["sabrServerUrl"] as? String
                ?: throw RuntimeException("Missing sabrServerUrl")
            val ustreamerConfig = p["sabrUstreamerConfig"] as? String
                ?: throw RuntimeException("Missing sabrUstreamerConfig")

            val formatsRaw = p["sabrFormats"]
            val formats = mutableListOf<SabrFormatDescriptor>()
            if (formatsRaw is List<*>) {
                for (f in formatsRaw) {
                    if (f is Map<*, *>) {
                        val itag = (f["itag"] as? Double)?.toInt() ?: continue
                        formats.add(SabrFormatDescriptor(
                            itag = itag,
                            lastModified = (f["lastModified"] as? Double)?.toLong() ?: 0L,
                            xtags = f["xtags"] as? String ?: "",
                            mimeType = f["mimeType"] as? String,
                            approxDurationMs = (f["approxDurationMs"] as? Double)?.toInt() ?: 0,
                            bitrate = (f["bitrate"] as? Double)?.toInt() ?: 0
                        ))
                    }
                }
            }

            val durationMs = ((p["duration"] as? Double) ?: 0.0) * 1000.0
            val clientInfo = p["clientInfo"] as? Map<*, *>
            val sabrConfig = SabrConfig(
                serverUrl = serverUrl,
                ustreamerConfig = ustreamerConfig,
                poToken = p["poToken"] as? String,
                cookie = p["cookie"] as? String,
                formats = formats,
                durationMs = durationMs,
                clientName = (clientInfo?.get("clientName") as? Double)?.toInt(),
                clientVersion = clientInfo?.get("clientVersion") as? String,
                preferOpus = p["preferOpus"] as? Boolean ?: false
            )

            val downloader = SabrDownloader(sabrConfig)
            sabrDownloaders[outputPath] = downloader

            downloader.onRefreshPoToken = { reason ->
                val map = AnyMap.fromMap(mapOf("outputPath" to outputPath, "reason" to reason), false)
                onSabrRefreshPoToken.asSecondOrNull()?.invoke(map)
            }
            downloader.onReloadPlayerResponse = { token ->
                val m = mutableMapOf<String, Any?>("outputPath" to outputPath)
                if (token != null) m["token"] = token
                val map = AnyMap.fromMap(m, true)
                onSabrReloadPlayerResponse.asSecondOrNull()?.invoke(map)
            }

            downloader.download(outputPath) { fraction ->
                val map = AnyMap.fromMap(mapOf("outputPath" to outputPath, "progress" to fraction), false)
                onSabrDownloadProgress.asSecondOrNull()?.invoke(map)
            }
            sabrDownloaders.remove(outputPath)
            outputPath
        }
    }

    override var updateSabrDownloadStream = { outputPath: String, serverUrl: String, ustreamerConfig: String ->
        wrapBackgroundAsync {
            val downloader = sabrDownloaders[outputPath]
                ?: throw RuntimeException("No active SABR download for: $outputPath")
            downloader.updateStream(serverUrl, ustreamerConfig)
        }
    }

    override var updateSabrDownloadPoToken = { outputPath: String, poToken: String ->
        wrapBackgroundAsync {
            val downloader = sabrDownloaders[outputPath]
                ?: throw RuntimeException("No active SABR download for: $outputPath")
            downloader.updatePoToken(poToken)
        }
    }

    override var updateSabrPlaybackPoToken = { poToken: String ->
        wrapAsync {
            verifyServiceBound()
            musicService.updatePlaybackPoToken(poToken)
        }
    }

    override var updateSabrPlaybackStream = { serverUrl: String, ustreamerConfig: String ->
        wrapAsync {
            verifyServiceBound()
            musicService.updateSabrStreamPlayback(serverUrl, ustreamerConfig)
        }
    }

    // ─── Android Only ─────────────────────────────────────────────────────────

    override var acquireWakeLock = {
        wrapAsync {
            verifyServiceBound()
            musicService.acquireWakeLock()
        }
    }

    override var abandonWakeLock = {
        wrapAsync {
            verifyServiceBound()
            musicService.abandonWakeLock()
        }
    }

    override var validateOnStartCommandIntent = {
        wrapAsync {
            verifyServiceBound()
            musicService.onStartCommandIntentValid
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun bundleToMap(bundle: Bundle): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in bundle.keySet()) {
            when (val value = bundle.get(key)) {
                is Bundle -> map[key] = bundleToMap(value)
                is Int -> map[key] = value.toDouble()
                is Float -> map[key] = value.toDouble()
                is Long -> map[key] = value.toDouble()
                else -> map[key] = value
            }
        }
        return map
    }

    override fun dispose() {
        if (isServiceBound) {
            musicService.nitroEventCallback = null
            try { context.unbindService(this) } catch (_: Exception) {}
            isServiceBound = false
        }
        if (shared === this) shared = null
    }

    // ─── Video View ───────────────────────────────────────────────────────────

    fun videoViewDidAttach(videoView: HybridVideoView) {
        attachedVideoView = videoView
        if (isServiceBound) {
            scope.launch(Dispatchers.Main) {
                // Show current track thumbnail immediately as fallback.
                val artwork = musicService.currentTrack?.originalItem?.getString("artwork")
                videoView.showThumbnail(artwork)

                if (trackLikelyHasVideo(musicService.currentTrack)) {
                    // Connect ExoPlayer's video output via the first-frame-aware attach.
                    // Thumbnail stays visible until ExoPlayer's onRenderedFirstFrame fires.
                    val exoPlayer = musicService.getExoPlayer()
                    if (exoPlayer != null) {
                        videoView.attachToExoPlayer(exoPlayer)
                    }
                } else {
                    videoView.clearVideo()
                }

                // If SABR audio is already running, restart at current position with
                // fresh SABR sessions so video starts being requested immediately.
                musicService.enableCurrentSabrVideoPlayback()
            }
        }
    }

    fun videoViewDidDetach(videoView: HybridVideoView) {
        if (attachedVideoView !== videoView) return
        attachedVideoView = null
        if (isServiceBound) {
            scope.launch(Dispatchers.Main) {
                musicService.clearVideoSurface()
            }
        }
        videoView.clearVideo()
    }

    companion object {
        /** Set in setupPlayer; used by HybridVideoView for auto-connect. */
        var shared: HybridEverythingPlayer? = null
    }

    private fun trackPayloadHasVideo(track: Map<*, *>?): Boolean {
        if (track == null) return false
        if ((track["isSabr"] as? Boolean) == true) {
            val sabrFormats = track["sabrFormats"] as? List<*>
            return sabrFormats?.any { format ->
                val mime = (format as? Map<*, *>)?.get("mimeType") as? String
                mime?.contains("video", ignoreCase = true) == true
            } == true
        }
        val contentType = (track["contentType"] as? String)?.lowercase()
        if (contentType != null) {
            if (contentType.startsWith("audio/")) return false
            if (contentType.startsWith("video/")) return true
        }
        val url = (track["url"] as? String)?.lowercase() ?: return true
        return !(url.endsWith(".mp3") || url.endsWith(".m4a") || url.endsWith(".aac") || url.endsWith(".opus") || url.endsWith(".ogg") || url.endsWith(".wav") || url.endsWith(".flac"))
    }

    private fun trackLikelyHasVideo(track: Track?): Boolean {
        if (track == null) return false
        if (track.isSabr) return track.resolvePreferredSabrVideoMimeType() != null
        val contentType = track.contentType?.lowercase()
        if (contentType != null) {
            if (contentType.startsWith("audio/")) return false
            if (contentType.startsWith("video/")) return true
        }
        val url = track.uri?.toString()?.lowercase() ?: return true
        return !(url.endsWith(".mp3") || url.endsWith(".m4a") || url.endsWith(".aac") || url.endsWith(".opus") || url.endsWith(".ogg") || url.endsWith(".wav") || url.endsWith(".flac"))
    }
}
