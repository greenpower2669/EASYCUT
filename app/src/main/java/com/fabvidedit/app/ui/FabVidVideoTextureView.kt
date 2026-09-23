package com.fabvidedit.app.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.transformer.CompositionPlayer
import com.fabvidedit.app.FabVidDiagnostics
import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.PreviewCanvasGeometry

/**
 * CompositionPlayer needs its specialized setVideoSurface(surface, outputSize) overload;
 * SimpleBasePlayer.setVideoSurface(surface) throws at runtime in Media3 1.11.
 * ExoPlayer can use ordinary Player.setVideoSurface; both must release BEFORE TextureView.
 */
class FabVidVideoTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var output: Surface? = null
    private var wanted: Player? = null
    private var attached: Player? = null
    private var attachedSize: Size? = null
    private var previewTransform: ClipTransform? = null
    private var previewSourceAspectRatio = 1f
    private var previewCanvasAspectRatio = 1f
    var onBindFailure: ((Throwable) -> Unit)? = null

    init {
        surfaceTextureListener = this
    }

    fun bind(player: Player?) {
        wanted = player
        val size = Size(width.coerceAtLeast(1), height.coerceAtLeast(1))
        if (attached === player && output != null && attachedSize == size) return
        unbind()
        val ready = output ?: surfaceTexture?.let { texture ->
            Surface(texture).also { output = it }
        }
        if (ready != null && player != null) {
            try {
                if (player is CompositionPlayer) {
                    player.setVideoSurface(ready, size)
                } else {
                    player.setVideoSurface(ready)
                }
                attached = player
                attachedSize = size
            } catch (error: RuntimeException) {
                FabVidDiagnostics.logError("VIDEO_SURFACE_BIND", error)
                // Do not let CompositionPlayer's unsupported output type kill the main thread.
                onBindFailure?.invoke(error)
            }
        }
    }

    /**
     * Apply the SAME FIT canvas and user transform as the Media3 export, directly
     * to the decoded TextureView content (not to AndroidView's outer composable).
     * Passing null restores native Media3 composition output without a second zoom.
     * Pixel positions intentionally derive from the ACTUAL view bounds.
     */
    fun applyPreviewTransform(
        transform: ClipTransform?,
        sourceAspectRatio: Float,
        canvasAspectRatio: Float,
    ) {
        previewTransform = transform
        previewSourceAspectRatio = sourceAspectRatio
        previewCanvasAspectRatio = canvasAspectRatio
        val matrix = Matrix()
        if (transform != null && width > 0 && height > 0) {
            val f = PreviewCanvasGeometry.forFrame(
                transform, width.toFloat(), height.toFloat(),
                sourceAspectRatio, canvasAspectRatio,
            )
            matrix.setScale(f.fitX, f.fitY, f.centerX, f.centerY)
            matrix.postScale(f.scaleX, f.scaleY, f.pivotX, f.pivotY)
            matrix.postRotate(f.rotationDegrees, f.pivotX, f.pivotY)
            matrix.postTranslate(f.translationX, f.translationY)
        }
        setTransform(matrix)
    }

    private fun unbind() {
        val old = attached
        attached = null
        attachedSize = null
        if (old != null) {
            runCatching { old.clearVideoSurface(output) }
                .onFailure { FabVidDiagnostics.logError("VIDEO_SURFACE_CLEAR", it) }
        }
    }

    fun dispose() {
        wanted = null
        unbind()
        output?.release()
        output = null
        onBindFailure = null
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        unbind()
        output?.release()
        output = Surface(texture)
        applyPreviewTransform(previewTransform, previewSourceAspectRatio, previewCanvasAspectRatio)
        bind(wanted)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        // Fit/zoom pixel geometry changes with the surface; do not reuse old px pivots.
        applyPreviewTransform(previewTransform, previewSourceAspectRatio, previewCanvasAspectRatio)
        bind(wanted)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        unbind()
        output?.release()
        output = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
