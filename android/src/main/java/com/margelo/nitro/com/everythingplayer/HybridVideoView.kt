@file:OptIn(UnstableApi::class)
package com.margelo.nitro.com.everythingplayer

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.everythingplayer.HybridEverythingPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class HybridVideoView(context: Context) : HybridNativeVideoViewSpec() {

    // MARK: - Views

    private val container = FrameLayout(context).also {
        it.setBackgroundColor(0xFF000000.toInt())
    }

    private val thumbnailView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    val surfaceView = SurfaceView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        visibility = View.GONE
    }

    override val view: View get() = container

    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var attachedExoPlayer: ExoPlayer? = null

    /** Listener that waits for the first rendered video frame before showing the surface. */
    private val firstFrameListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            mainHandler.removeCallbacks(firstFrameTimeoutRunnable)
            showVideoSurface()
            attachedExoPlayer?.removeListener(this)
        }
    }

    /** Timeout: if no first frame within 5s, show the surface anyway. */
    private val firstFrameTimeoutRunnable = Runnable {
        showVideoSurface()
    }

    init {
        container.addView(thumbnailView)
        container.addView(surfaceView)
    }

    // MARK: - Spec: Properties

    override var resizeMode: String = "contain"
        set(value) {
            field = value
            thumbnailView.scaleType = when (value) {
                "cover" -> ImageView.ScaleType.CENTER_CROP
                "fill"  -> ImageView.ScaleType.FIT_XY
                else    -> ImageView.ScaleType.FIT_CENTER
            }
        }

    // MARK: - Spec: Methods (called from JS via hybridRef)

    override fun onAttach() {
        HybridEverythingPlayer.shared?.videoViewDidAttach(this)
    }

    override fun onDetach() {
        HybridEverythingPlayer.shared?.videoViewDidDetach(this)
    }

    // MARK: - Called by HybridEverythingPlayer

    /** Connect an ExoPlayer instance so its video is rendered in this view. */
    fun attachToExoPlayer(exoPlayer: ExoPlayer) {
        // Clean up any previous listener
        attachedExoPlayer?.removeListener(firstFrameListener)
        mainHandler.removeCallbacks(firstFrameTimeoutRunnable)
        attachedExoPlayer = exoPlayer

        exoPlayer.setVideoSurfaceView(surfaceView)

        // Keep thumbnail visible; wait for first decoded frame before showing surface.
        surfaceView.visibility = View.VISIBLE
        thumbnailView.visibility = View.VISIBLE  // keep on top until first frame
        exoPlayer.addListener(firstFrameListener)
        mainHandler.postDelayed(firstFrameTimeoutRunnable, 5000)
    }

    /** Disconnect ExoPlayer from this view and show the thumbnail. */
    fun detachFromExoPlayer(exoPlayer: ExoPlayer) {
        exoPlayer.removeListener(firstFrameListener)
        mainHandler.removeCallbacks(firstFrameTimeoutRunnable)
        attachedExoPlayer = null
        exoPlayer.clearVideoSurface()
        surfaceView.visibility = View.GONE
        thumbnailView.visibility = View.VISIBLE
    }

    /** Load and display a thumbnail image from a URL. */
    fun showThumbnail(url: String?) {
        if (url == null) {
            thumbnailView.setImageDrawable(null)
            return
        }
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val conn = URL(url).openConnection()
                    conn.connect()
                    BitmapFactory.decodeStream(conn.getInputStream())
                }
                thumbnailView.setImageBitmap(bitmap)
            } catch (_: Exception) { /* ignore load errors */ }
        }
    }

    /** Hide the player layer and show the thumbnail placeholder. */
    fun clearVideo() {
        attachedExoPlayer?.removeListener(firstFrameListener)
        mainHandler.removeCallbacks(firstFrameTimeoutRunnable)
        surfaceView.visibility = View.GONE
        thumbnailView.visibility = View.VISIBLE
    }

    fun showVideoSurface() {
        surfaceView.visibility = View.VISIBLE
        thumbnailView.visibility = View.GONE
    }

    // MARK: - HybridView lifecycle

    override fun onDropView() {
        attachedExoPlayer?.removeListener(firstFrameListener)
        mainHandler.removeCallbacks(firstFrameTimeoutRunnable)
        attachedExoPlayer = null
        clearVideo()
        HybridEverythingPlayer.shared?.videoViewDidDetach(this)
    }

}
