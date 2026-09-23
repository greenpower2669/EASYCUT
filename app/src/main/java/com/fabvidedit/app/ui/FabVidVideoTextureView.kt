package com.fabvidedit.app.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.transformer.CompositionPlayer
import com.fabvidedit.app.FabVidDiagnostics

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
        bind(wanted)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        // CompositionPlayer output size has to track the actual TextureView size.
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
