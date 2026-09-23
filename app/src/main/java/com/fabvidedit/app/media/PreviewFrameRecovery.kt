package com.fabvidedit.app.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Recovery is conditional; no proxy is generated for videos which already display normally. */
internal object PreviewRecoveryPolicy {
    fun captureEdge(lowRam: Boolean): Int = if (lowRam) 480 else 720
    fun recoveryEdges(lowRam: Boolean): List<Int> = if (lowRam) listOf(360, 480) else listOf(480, 720)
    fun shouldRecover(snapshotAvailable: Boolean): Boolean = !snapshotAvailable
    fun shouldRecoverInSimpleMode(presentedOnCurrentSurface: Boolean, bridgeActive: Boolean): Boolean =
        !presentedOnCurrentSurface && !bridgeActive
}

/** Small independently decoded source frames while a replacement Media3 surface warms up. */
internal object PreviewFrameRecovery {
    suspend fun load(context: Context, uri: Uri, sourceMs: Long, edge: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()?.coerceAtLeast(1) ?: edge
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()?.coerceAtLeast(1) ?: edge
                    val factor = edge.coerceIn(1, 720).toFloat() / max(w, h)
                    val outWidth = (w * factor).toInt().coerceAtLeast(1)
                    val outHeight = (h * factor).toInt().coerceAtLeast(1)
                    retriever.getScaledFrameAtTime(
                        sourceMs.coerceAtLeast(0L) * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        outWidth, outHeight,
                    )
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
}
