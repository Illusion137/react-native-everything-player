package com.everythingplayer.model

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.everythingplayer.kotlinaudio.models.AudioItem
import com.everythingplayer.kotlinaudio.models.AudioItemOptions
import com.everythingplayer.kotlinaudio.models.MediaType

data class TrackAudioItem(
    val track: Track,
    override val type: MediaType,
    override var audioUrl: String,
    override var artist: String? = null,
    override var title: String? = null,
    override var albumTitle: String? = null,
    override val artwork: String? = null,
    override val duration: Long? = null,
    override val options: AudioItemOptions? = null,
    override val mediaId: String? = null,
    val sabrSessionId: String? = null,
    val sabrMimeType: String? = null,
    val sabrVideoSessionId: String? = null,
    val sabrVideoMimeType: String? = null,
    val sabrVideoUrl: String? = null,
    val sabrStartPositionMs: Long? = null
) : AudioItem(audioUrl, type, artist, title, albumTitle, artwork, duration, options, mediaId) {

    override fun toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            options?.headers?.let { putSerializable("headers", HashMap(it)) }
            options?.userAgent?.let { putString("user-agent", it) }
            options?.resourceId?.let { putInt("resource-id", it) }
            putString("type", type.toString())
            putString("uri", audioUrl)
            duration?.let { putLong("duration", it) }
            if (track.isSabr) {
                putBoolean("isSabr", true)
                putString("sabrServerUrl", track.sabrServerUrl)
                putString("sabrUstreamerConfig", track.sabrUstreamerConfig)
                putString("poToken", track.poToken)
                putString("cookie", track.cookie)
                putString("sabrSessionId", sabrSessionId)
                putString("sabrMimeType", sabrMimeType)
                putString("sabrVideoSessionId", sabrVideoSessionId)
                putString("sabrVideoMimeType", sabrVideoMimeType)
                putString("sabrVideoUrl", sabrVideoUrl)
                sabrStartPositionMs?.let { putLong("sabrStartPositionMs", it) }
                if (track.sabrClientName != null || track.sabrClientVersion != null) {
                    putBundle("clientInfo", Bundle().apply {
                        track.sabrClientName?.let { putInt("clientName", it) }
                        track.sabrClientVersion?.let { putString("clientVersion", it) }
                    })
                }
                putSerializable("sabrFormats", ArrayList(track.sabrFormats.map { format ->
                    Bundle().apply {
                        putInt("itag", format.itag)
                        putLong("lastModified", format.lastModified)
                        putString("xtags", format.xtags)
                        putString("mimeType", format.mimeType)
                        putInt("approxDurationMs", format.approxDurationMs)
                        putInt("bitrate", format.bitrate)
                    }
                }))
            }
        }
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(Uri.parse(artwork))
            .setExtras(extras)
            .build()
        val builder = MediaItem.Builder()
            .setMediaId(mediaId ?: audioUrl)
            .setUri(audioUrl)
            .setMimeType(sabrMimeType)
            .setMediaMetadata(mediaMetadata)
            .setTag(this)

        if (track.drmType == "widevine" && track.drmLicenseServer != null) {
            val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(track.drmLicenseServer!!)
                .apply {
                    track.drmHeaders?.let { setLicenseRequestHeaders(it) }
                }
                .build()
            builder.setDrmConfiguration(drmConfig)
        }

        return builder.build()
    }
}
