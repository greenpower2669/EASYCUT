package com.fabvidedit.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object FfmpegTrackTools {
    private val thumbnailCache = object : LruCache<String, Bitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun thumbnail(
        context: Context,
        uri: Uri,
        streamIndex: Int,
        timeMs: Long,
        maxEdgePx: Int = 420,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${uri}#v$streamIndex@${timeMs / 250L}#$maxEdgePx"
        thumbnailCache.get(key)?.let { return@withContext it }

        val output = File(
            context.cacheDir,
            "fabvid-track-thumb-$streamIndex-${timeMs.coerceAtLeast(0L)}-${System.nanoTime()}.jpg",
        )
        val input = FFmpegKitConfig.getSafParameterForRead(context, uri)
        val seconds = String.format(Locale.US, "%.3f", timeMs.coerceAtLeast(0L) / 1_000.0)
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-hide_banner",
                "-loglevel", "error",
                "-ss", seconds,
                "-i", input,
                "-map", "0:$streamIndex",
                "-frames:v", "1",
                "-vf", "scale=$maxEdgePx:$maxEdgePx:force_original_aspect_ratio=decrease",
                "-q:v", "3",
                "-y",
                output.absolutePath,
            ),
        )
        if (!ReturnCode.isSuccess(session.returnCode) || !output.exists()) {
            output.delete()
            return@withContext null
        }
        val bitmap = BitmapFactory.decodeFile(output.absolutePath)
        output.delete()
        if (bitmap != null) thumbnailCache.put(key, bitmap)
        bitmap
    }

    suspend fun frameDimensions(
        context: Context,
        uri: Uri,
        streamIndex: Int,
        timeMs: Long,
    ): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val input = FFmpegKitConfig.getSafParameterForRead(context, uri)
        val seconds = String.format(Locale.US, "%.3f", timeMs.coerceAtLeast(0L) / 1_000.0)
        val session = FfprobeNativeGate.run {
            FFprobeKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-read_intervals", "$seconds%+0.50",
                    "-select_streams", streamIndex.toString(),
                    "-show_frames",
                    "-show_entries", "frame=width,height",
                    "-print_format", "json",
                    input,
                ),
            )
        }
        if (!ReturnCode.isSuccess(session.returnCode)) return@withContext null
        val frames = runCatching { JSONObject(session.output).optJSONArray("frames") }.getOrNull()
            ?: return@withContext null
        for (index in 0 until frames.length()) {
            val frame = frames.optJSONObject(index) ?: continue
            val width = frame.optInt("width", 0)
            val height = frame.optInt("height", 0)
            if (width > 0 && height > 0) return@withContext width to height
        }
        null
    }

    fun clear() {
        thumbnailCache.evictAll()
    }
}
