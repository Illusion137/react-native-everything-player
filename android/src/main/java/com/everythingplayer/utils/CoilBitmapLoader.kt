package com.everythingplayer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.Util.isBitmapFactorySupportedMimeType
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.guava.future
import java.io.IOException
import javax.inject.Inject

@UnstableApi
class CoilBitmapLoader @Inject constructor(
    private val context: Context,
    private val cropSquare: Boolean = false,
) : BitmapLoader {

    private val scope = MainScope()
    private val imageLoader = ImageLoader(context)

    override fun supportsMimeType(mimeType: String): Boolean {
        return isBitmapFactorySupportedMimeType(mimeType)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        return scope.future {
            bitmap ?: throw IOException("Unable to decode bitmap")
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = scope.future {
        val imageRequest = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
        val response = imageLoader.execute(imageRequest.build())
        val bitmap = (response.drawable as? BitmapDrawable)?.bitmap
        bitmap ?: throw IOException("Unable to load bitmap: $uri")
    }
}
