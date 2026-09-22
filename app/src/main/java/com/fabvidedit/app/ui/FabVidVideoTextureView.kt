package com.fabvidedit.app.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.Player
import com.fabvidedit.app.FabVidDiagnostics

/**
 * Media3 CompositionPlayer does NOT accept PlayerView.setPlayer(): that call forwards
 * a TextureView via setVideoTextureView and throws UnsupportedOperationException.
 * Bind a real Surface explicitly, and unbind BEFORE releasing it. ExoPlayer fallback
 * uses the same output when CompositionPlayer lacks setVideoSurface support.
 */
class FabVidVideoTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var output: Surface? = null
    private var wanted: Player? = null
    private var attached: Player? = null
    var onBindFailure: ((Throwable) -> Unit)? = null

    init {
        surfaceTextureListener = this
    }

    fun bind(player: Player?) {
        wanted = player
        if (attached === player && output != null) return
        unbind()
        val ready = output ?: surfaceTexture?.let { texture ->
            Surface(texture).also { output = it }
        }
        if (ready != null && player != null) {
            try {
                player.setVideoSurface(ready)
                attached = player
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

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        unbind()
        output?.release()
        output = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
