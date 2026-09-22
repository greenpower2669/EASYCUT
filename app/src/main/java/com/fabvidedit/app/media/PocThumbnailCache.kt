package com.fabvidedit.app.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object PocThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun get(
        context: Context,
        uri: String,
        timeMs: Long,
        maxEdgePx: Int = 320,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val bucket = timeMs / 120L
        val key = "$uri@$bucket#$maxEdgePx"
        cache.get(key)?.let { return@withContext it }

        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.getFrameAtTime(
                timeMs.coerceAtLeast(0L) * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            )
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        } ?: return@withContext null

        val longest = max(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scaled = if (longest <= maxEdgePx) {
            bitmap
        } else {
            val scale = maxEdgePx.toFloat() / longest.toFloat()
            val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, width, height, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }
        cache.put(key, scaled)
        scaled
    }

    fun clear() {
        cache.evictAll()
    }
}
