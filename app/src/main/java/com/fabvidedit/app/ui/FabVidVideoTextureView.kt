package com.fabvidedit.app.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.Bitmap
import kotlin.math.max
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
    // Applying an unchanged matrix invalidates the video view for no visual benefit.
    // Reapply after surface creation/resize, even when the parameters match.
    private data class MatrixKey(
        val transform: ClipTransform?,
        val sourceAspectRatio: Float,
        val canvasAspectRatio: Float,
        val width: Int,
        val height: Int,
    )
    private var appliedMatrixKey: MatrixKey? = null
    var renderedFrameCount: Long = 0L
        private set
    var appliedPreviewMatrixCount: Long = 0L
        private set
    var surfaceBindCount: Long = 0L
        private set
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
                surfaceBindCount++
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
        val key = MatrixKey(transform, sourceAspectRatio, canvasAspectRatio, width, height)
        if (key == appliedMatrixKey) return
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
        appliedPreviewMatrixCount++
        appliedMatrixKey = key
    }

    /** One bounded copy on decoder hand-off, never per finger movement. */
    fun captureFrame(maxEdge: Int): Bitmap? {
        if (!isAvailable || renderedFrameCount <= 0L || width <= 0 || height <= 0) return null
        val factor = (maxEdge.coerceAtLeast(1).toFloat() / max(width, height)).coerceAtMost(1f)
        return runCatching {
            getBitmap((width * factor).toInt().coerceAtLeast(1),
                (height * factor).toInt().coerceAtLeast(1))
        }.getOrNull()
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
        appliedMatrixKey = null
        onBindFailure = null
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        unbind()
        output?.release()
        output = Surface(texture)
        appliedMatrixKey = null
        applyPreviewTransform(previewTransform, previewSourceAspectRatio, previewCanvasAspectRatio)
        bind(wanted)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        // The surface backing changed; preserve FIT, zoom and finger pivot.
        appliedMatrixKey = null
        applyPreviewTransform(previewTransform, previewSourceAspectRatio, previewCanvasAspectRatio)
        bind(wanted)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        unbind()
        output?.release()
        output = null
        appliedMatrixKey = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        renderedFrameCount++
    }
}
