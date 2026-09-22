package com.fabvidedit.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TimelineWaveformCache {
    private const val TAG = "FabVidWaveform"
    private const val WIDTH = 1200
    private const val HEIGHT = 160
    private const val MAX_CACHE_BYTES = 64L * 1024L * 1024L

    suspend fun load(
        context: Context,
        rawUri: String,
        trimStartMs: Long = 0L,
        trimEndMs: Long = Long.MAX_VALUE,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val safeStart = trimStartMs.coerceAtLeast(0L)
        val safeEnd = trimEndMs.takeIf { it != Long.MAX_VALUE }?.coerceAtLeast(safeStart + 1L)
        val cacheDir = File(context.cacheDir, "timeline_waveforms").apply { mkdirs() }
        val cacheFile = File(cacheDir, "${key(rawUri, safeStart, safeEnd)}.png")
        decode(cacheFile)?.let { return@withContext it }
        cacheFile.delete()

        val uri = Uri.parse(rawUri)
        val ok = runCatching {
            LocalFfmpegInput.withPath(context, uri) { input ->
                val args = buildList {
                    addAll(listOf("-hide_banner", "-loglevel", "error"))
                    if (safeStart > 0L) addAll(listOf("-ss", seconds(safeStart)))
                    addAll(listOf("-i", input))
                    safeEnd?.let { end ->
                        addAll(listOf("-t", seconds((end - safeStart).coerceAtLeast(1L))))
                    }
                    addAll(
                        listOf(
                            "-filter_complex", "aformat=channel_layouts=mono,showwavespic=s=${WIDTH}x${HEIGHT}",
                            "-frames:v", "1",
                            "-y", cacheFile.absolutePath,
                        ),
                    )
                }.toTypedArray()
                val session = FFmpegKit.executeWithArguments(args)
                val success = ReturnCode.isSuccess(session.returnCode) && cacheFile.isFile && cacheFile.length() > 0L
                FFmpegKitConfig.clearSessions()
                success
            }
        }.onFailure {
            Log.d(TAG, "waveform failed uri=$rawUri detail=${it.message}")
        }.getOrDefault(false)

        if (!ok) cacheFile.delete()
        trim(cacheDir)
        if (ok) decode(cacheFile) else null
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

    private fun key(uri: String, start: Long, end: Long?): String {
        val raw = "$uri|$start|${end ?: -1L}|$WIDTH|$HEIGHT"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1_000.0)
}
