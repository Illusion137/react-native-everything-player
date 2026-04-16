package com.everythingplayer.kotlinaudio.players.components

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.smoothstreaming.DefaultSsChunkSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import com.everythingplayer.kotlinaudio.utils.isUriLocalFile
import com.everythingplayer.utils.SabrConfig
import com.everythingplayer.utils.SabrDataSource
import com.everythingplayer.utils.SabrFormatDescriptor
import com.everythingplayer.utils.SabrPlaybackRegistry
import com.everythingplayer.utils.SabrPlaybackSessionConfig

@OptIn(UnstableApi::class)
class MediaFactory(
    private val context: Context,
    private val cache: SimpleCache?
) : MediaSource.Factory {

    companion object {
        private const val DEFAULT_USER_AGENT = "react-native-everything-player"
    }

    private val mediaFactory = DefaultMediaSourceFactory(context)

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        return mediaFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        return mediaFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    }

    override fun getSupportedTypes(): IntArray {
        return mediaFactory.supportedTypes
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val userAgent = mediaItem.mediaMetadata.extras?.getString("user-agent") ?: DEFAULT_USER_AGENT
        val headers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaItem.mediaMetadata.extras?.getSerializable("headers", HashMap::class.java)
        } else {
            @Suppress("DEPRECATION")
            mediaItem.mediaMetadata.extras?.getSerializable("headers")
        }
        val resourceId = mediaItem.mediaMetadata.extras?.getInt("resource-id")
        val resourceType = mediaItem.mediaMetadata.extras?.getString("type")?.lowercase()
        val uri = Uri.parse(mediaItem.mediaMetadata.extras?.getString("uri")!!)
        val isSabr = mediaItem.mediaMetadata.extras?.getBoolean("isSabr", false) == true

        if (isSabr) {
            return createSabrSource(mediaItem)
        }

        val factory: DataSource.Factory = when {
            resourceId != 0 && resourceId != null -> {
                val raw = RawResourceDataSource(context)
                raw.open(DataSpec(uri))
                DataSource.Factory { raw }
            }
            isUriLocalFile(uri) -> {
                DefaultDataSource.Factory(context)
            }
            else -> {
                val tempFactory = DefaultHttpDataSource.Factory().apply {
                    setUserAgent(userAgent)
                    setAllowCrossProtocolRedirects(true)
                    headers?.let {
                        @Suppress("UNCHECKED_CAST")
                        setDefaultRequestProperties(it as HashMap<String, String>)
                    }
                }
                enableCaching(tempFactory)
            }
        }

        return when (resourceType) {
            "dash" -> createDashSource(mediaItem, factory)
            "hls" -> createHlsSource(mediaItem, factory)
            "smoothstreaming" -> createSsSource(mediaItem, factory)
            else -> createProgressiveSource(mediaItem, factory)
        }
    }

    private fun createSabrSource(mediaItem: MediaItem): MediaSource {
        val extras = mediaItem.mediaMetadata.extras ?: error("Missing SABR metadata extras")
        val sessionId = extras.getString("sabrSessionId") ?: error("Missing SABR session id")
        val formats = @Suppress("DEPRECATION")
        (extras.getSerializable("sabrFormats") as? ArrayList<Bundle>).orEmpty().mapNotNull { format ->
            val itag = format.getInt("itag", 0).takeIf { it != 0 } ?: return@mapNotNull null
            SabrFormatDescriptor(
                itag = itag,
                lastModified = format.getLong("lastModified", 0L),
                xtags = format.getString("xtags") ?: "",
                mimeType = format.getString("mimeType"),
                approxDurationMs = format.getInt("approxDurationMs", 0),
                bitrate = format.getInt("bitrate", 0)
            )
        }
        val clientInfo = extras.getBundle("clientInfo")
        val config = SabrConfig(
            serverUrl = extras.getString("sabrServerUrl") ?: error("Missing SABR server url"),
            ustreamerConfig = extras.getString("sabrUstreamerConfig") ?: error("Missing SABR ustreamer config"),
            poToken = extras.getString("poToken"),
            cookie = extras.getString("cookie"),
            formats = formats,
            durationMs = extras.getLong("duration", 0L).toDouble() * 1000.0,
            clientName = clientInfo?.getInt("clientName"),
            clientVersion = clientInfo?.getString("clientVersion"),
            preferOpus = (extras.getString("sabrMimeType") ?: "").contains("webm", ignoreCase = true) ||
                (extras.getString("sabrMimeType") ?: "").contains("opus", ignoreCase = true),
            startTimeMs = extras.getLong("sabrStartPositionMs", 0L)
        )
        val session = SabrPlaybackRegistry.getOrCreate(
            SabrPlaybackSessionConfig(
                sessionId = sessionId,
                sabrConfig = config,
                mimeType = extras.getString("sabrMimeType")
            )
        )
        return ProgressiveMediaSource.Factory(
            SabrDataSource.Factory(session),
            DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        ).createMediaSource(mediaItem)
    }

    private fun createDashSource(mediaItem: MediaItem, factory: DataSource.Factory): MediaSource {
        return DashMediaSource.Factory(DefaultDashChunkSource.Factory(factory), factory)
            .createMediaSource(mediaItem)
    }

    private fun createHlsSource(mediaItem: MediaItem, factory: DataSource.Factory): MediaSource {
        return HlsMediaSource.Factory(factory).createMediaSource(mediaItem)
    }

    private fun createSsSource(mediaItem: MediaItem, factory: DataSource.Factory): MediaSource {
        return SsMediaSource.Factory(DefaultSsChunkSource.Factory(factory), factory)
            .createMediaSource(mediaItem)
    }

    private fun createProgressiveSource(mediaItem: MediaItem, factory: DataSource.Factory): ProgressiveMediaSource {
        return ProgressiveMediaSource.Factory(
            factory, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        ).createMediaSource(mediaItem)
    }

    private fun enableCaching(factory: DataSource.Factory): DataSource.Factory {
        return if (cache == null) {
            factory
        } else {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(factory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }
}
