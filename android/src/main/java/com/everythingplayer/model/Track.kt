package com.everythingplayer.model

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.everythingplayer.kotlinaudio.models.AudioItemOptions
import com.everythingplayer.kotlinaudio.models.MediaType
import com.everythingplayer.utils.BundleUtils
import com.everythingplayer.utils.SabrFormatDescriptor
import java.util.Locale
import java.util.UUID

@OptIn(UnstableApi::class)
class Track(context: Context, bundle: Bundle, ratingType: Int) : TrackMetadata() {
    var uri: Uri? = null
    var resourceId: Int?
    var type = MediaType.DEFAULT
    var contentType: String?
    var userAgent: String?
    var originalItem: Bundle
    var headers: HashMap<String, String>? = null
    val queueId: Long

    // Track trimming
    var startTime: Double? = null
    var endTime: Double? = null

    // DRM
    var drmType: String? = null
    var drmLicenseServer: String? = null
    var drmHeaders: HashMap<String, String>? = null

    // SABR (YouTube streaming)
    var isOpus: Boolean = false
    var isSabr: Boolean = false
    var sabrServerUrl: String? = null
    var sabrUstreamerConfig: String? = null
    var sabrFormats: List<SabrFormatDescriptor> = emptyList()
    var poToken: String? = null
    var cookie: String? = null
    var sabrClientName: Int? = null
    var sabrClientVersion: String? = null

    private fun Bundle.getIntCompat(key: String): Int? {
        val value = get(key) ?: return null
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun Bundle.getLongCompat(key: String): Long? {
        val value = get(key) ?: return null
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    override fun setMetadata(context: Context, bundle: Bundle?, ratingType: Int) {
        super.setMetadata(context, bundle, ratingType)
        originalItem.putAll(bundle)
    }

    fun toAudioItem(sabrStartPositionMs: Long? = null): TrackAudioItem {
        val sabrMimeType = if (isSabr) resolvePreferredSabrMimeType() else null
        val sabrSessionId = if (isSabr) "sabr-${queueId}-${UUID.randomUUID()}" else null
        val audioUrl = if (isSabr) buildSabrUri(sabrSessionId ?: queueId.toString(), sabrMimeType) else uri.toString()
        return TrackAudioItem(
            track = this,
            type = type,
            audioUrl = audioUrl,
            artist = artist,
            title = title,
            albumTitle = album,
            artwork = artwork.toString(),
            duration = duration,
            options = AudioItemOptions(headers, userAgent, resourceId),
            mediaId = mediaId,
            sabrSessionId = sabrSessionId,
            sabrMimeType = sabrMimeType,
            sabrStartPositionMs = sabrStartPositionMs
        )
    }

    private fun buildSabrUri(sessionId: String, mimeType: String?): String {
        val extension = when {
            mimeType == null && isOpus -> "webm"
            mimeType == null -> "m4a"
            mimeType.contains("webm", ignoreCase = true) || mimeType.contains("opus", ignoreCase = true) -> "webm"
            mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "m4a"
            else -> "bin"
        }
        return "sabr://$sessionId/stream.$extension"
    }

    fun resolvePreferredSabrMimeType(): String? {
        val audioFormats = sabrFormats.filter { it.mimeType?.contains("audio", ignoreCase = true) == true }
        return audioFormats
            .sortedWith(compareByDescending<SabrFormatDescriptor> {
                val mime = it.mimeType.orEmpty().lowercase(Locale.US)
                when {
                    isOpus && ("opus" in mime || "webm" in mime) -> 3
                    !isOpus && ("mp4" in mime || "m4a" in mime) -> 3
                    "opus" in mime || "webm" in mime -> 2
                    "mp4" in mime || "m4a" in mime -> 2
                    else -> 1
                }
            }.thenByDescending { it.bitrate })
            .firstOrNull()
            ?.mimeType
    }

    init {
        originalItem = bundle
        resourceId = BundleUtils.getRawResourceId(context, bundle, "url")
        uri = if (resourceId == 0) {
            resourceId = null
            BundleUtils.getUri(context, bundle, "url")
        } else {
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .path(Integer.toString(resourceId!!))
                .build()
        }

        val trackType = bundle.getString("type", "default")
        for (t in MediaType.entries) {
            if (t.name.equals(trackType, ignoreCase = true)) {
                type = t
                break
            }
        }

        contentType = bundle.getString("contentType")
        userAgent = bundle.getString("userAgent")

        val httpHeaders = bundle.getBundle("headers")
        if (httpHeaders != null) {
            headers = HashMap()
            for (header in httpHeaders.keySet()) {
                headers!![header] = httpHeaders.getString(header)!!
            }
        }

        // Trimming
        if (bundle.containsKey("startTime")) startTime = bundle.getDouble("startTime")
        if (bundle.containsKey("endTime")) endTime = bundle.getDouble("endTime")

        // DRM
        drmType = bundle.getString("drmType")
        drmLicenseServer = bundle.getString("drmLicenseServer")
        val drmHeadersBundle = bundle.getBundle("drmHeaders")
        if (drmHeadersBundle != null) {
            drmHeaders = HashMap()
            for (key in drmHeadersBundle.keySet()) {
                drmHeaders!![key] = drmHeadersBundle.getString(key)!!
            }
        }

        // SABR
        isOpus = bundle.getBoolean("isOpus", false)
        isSabr = bundle.getBoolean("isSabr", false)
        sabrServerUrl = bundle.getString("sabrServerUrl")
        sabrUstreamerConfig = bundle.getString("sabrUstreamerConfig")
        poToken = bundle.getString("poToken")
        cookie = bundle.getString("cookie")
        bundle.getBundle("clientInfo")?.let { clientInfo ->
            sabrClientName = clientInfo.getIntCompat("clientName")
            sabrClientVersion = clientInfo.getString("clientVersion")
        }
        @Suppress("UNCHECKED_CAST")
        val sabrFormatsArr = bundle.getSerializable("sabrFormats") as? ArrayList<Bundle>
        if (sabrFormatsArr != null) {
            sabrFormats = sabrFormatsArr.mapNotNull { f ->
                val itag = f.getIntCompat("itag")?.takeIf { it != 0 } ?: return@mapNotNull null
                SabrFormatDescriptor(
                    itag = itag,
                    lastModified = f.getLongCompat("lastModified") ?: 0L,
                    xtags = f.getString("xtags") ?: "",
                    mimeType = f.getString("mimeType"),
                    approxDurationMs = f.getIntCompat("approxDurationMs") ?: 0,
                    bitrate = f.getIntCompat("bitrate") ?: 0
                )
            }
        }

        setMetadata(context, bundle, ratingType)
        queueId = System.currentTimeMillis()
    }
}
