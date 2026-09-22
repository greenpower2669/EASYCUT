package com.fabvidedit.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.fabvidedit.app.model.VideoClip
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small, disk-backed representative frames for the multipiste timeline.
 *
 * One light JPEG is cached per clip/source position. The Compose timeline can repeat this single
 * image like a filmstrip without decoding the source video for every visible tile.
 */
object TimelineThumbnailCache {
    private const val TAG = "FabVidThumbs"
    private const val THUMB_WIDTH = 192
    private const val THUMB_HEIGHT = 108
    private const val JPEG_QUALITY = 68
    private const val MAX_CACHE_BYTES = 96L * 1024L * 1024L

    suspend fun load(context: Context, clip: VideoClip): Bitmap? = withContext(Dispatchers.IO) {
        val sourceMs = clip.trimStartMs + minOf(750L, (clip.sourceDurationMs / 3L).coerceAtLeast(0L))
        val cacheDir = File(context.cacheDir, "timeline_thumbnails").apply { mkdirs() }
        val cacheFile = File(cacheDir, "${key(clip.uri, sourceMs)}.jpg")

        decode(cacheFile)?.let { return@withContext it }
        cacheFile.delete()

        val bitmap = extractWithAndroid(context, clip.uri, sourceMs)
            ?: extractWithFfmpeg(context, clip.uri, sourceMs, cacheFile)

        if (bitmap != null && !cacheFile.exists()) {
            runCatching {
                cacheFile.outputStream().buffered().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            }.onFailure { cacheFile.delete() }
        }

        trim(cacheDir)
        bitmap ?: decode(cacheFile)
    }

    private fun extractWithAndroid(context: Context, rawUri: String, sourceMs: Long): Bitmap? = runCatching {
        val uri = Uri.parse(rawUri)
        val retriever = MediaMetadataRetriever()
        try {
            when {
                uri.scheme.equals("file", ignoreCase = true) -> {
                    val path = requireNotNull(uri.path)
                    retriever.setDataSource(path)
                }
                else -> retriever.setDataSource(context, uri)
            }
            retriever.getScaledFrameAtTime(
                sourceMs * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMB_WIDTH,
                THUMB_HEIGHT,
            )
        } finally {
            retriever.release()
        }
    }.onFailure {
        Log.d(TAG, "Android thumbnail fallback uri=$rawUri detail=${it.message}")
    }.getOrNull()

    private suspend fun extractWithFfmpeg(
        context: Context,
        rawUri: String,
        sourceMs: Long,
        cacheFile: File,
    ): Bitmap? {
        val uri = Uri.parse(rawUri)
        return runCatching {
            LocalFfmpegInput.withPath(context, uri) { input ->
                cacheFile.delete()
                val args = arrayOf(
                    "-hide_banner",
                    "-loglevel", "error",
                    "-ss", seconds(sourceMs),
                    "-i", input,
                    "-frames:v", "1",
                    "-vf", "scale=${THUMB_WIDTH}:${THUMB_HEIGHT}:force_original_aspect_ratio=decrease,pad=${THUMB_WIDTH}:${THUMB_HEIGHT}:(ow-iw)/2:(oh-ih)/2",
                    "-q:v", "6",
                    "-y", cacheFile.absolutePath,
                )
                val session = FFmpegKit.executeWithArguments(args)
                val ok = ReturnCode.isSuccess(session.returnCode) && cacheFile.isFile && cacheFile.length() > 0L
                FFmpegKitConfig.clearSessions()
                if (!ok) cacheFile.delete()
                ok
            }
        }.onFailure {
            Log.w(TAG, "FFmpeg thumbnail failed uri=$rawUri detail=${it.message}")
            cacheFile.delete()
        }.getOrDefault(false).let { ok -> if (ok) decode(cacheFile) else null }
    }

    private fun decode(file: File): Bitmap? =
        file.takeIf { it.isFile && it.length() > 0L }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    private fun trim(cacheDir: File) {
        val files = cacheDir.listFiles()?.filter(File::isFile)?.sortedBy(File::lastModified).orEmpty()
        var total = files.sumOf(File::length)
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) break
            val bytes = file.length()
            if (file.delete()) total -= bytes
        }
    }

    private fun key(uri: String, sourceMs: Long): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$uri|$sourceMs|$THUMB_WIDTH|$THUMB_HEIGHT".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1_000.0)
}
