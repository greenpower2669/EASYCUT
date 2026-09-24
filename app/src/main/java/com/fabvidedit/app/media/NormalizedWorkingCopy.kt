package com.fabvidedit.app.media

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.fabvidedit.app.FabVidDiagnostics
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VisualMediaKind
import com.fabvidedit.app.model.displayAspectRatio
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** User-requested, opt-in source derivative. Never mutates/deletes original media. */
internal object NormalizedWorkingCopy {
    data class Created(val uri: Uri, val width: Int, val height: Int, val codec: String)

    internal fun dimensions(clip: VideoClip): Pair<Int, Int> {
        val ratio = clip.displayAspectRatio()?.takeIf { it.isFinite() && it in 0.2f..5f }
            ?: error("Ratio d'affichage source inconnu : normalisation impossible")
        val height = clip.height.coerceIn(240, 2160).let { it + it % 2 }
        val width = ((height * ratio).roundToInt().coerceIn(240, 3840) + 1) / 2 * 2
        return width to height
    }

    suspend fun create(context: Context, clip: VideoClip): Created = withContext(Dispatchers.IO) {
        require(clip.mediaKind == VisualMediaKind.VIDEO) { "Une vidéo est nécessaire" }
        require(clip.rotationDegrees % 360 == 0) {
            "Copie SAR avec rotation source : utilise d'abord l'export du projet pour éviter une double rotation."
        }
        val (width, height) = dimensions(clip)
        val uri = Uri.parse(clip.uri)
        LocalFfmpegInput.withPath(context, uri) { input ->
            val source = File(input)
            val bound = (source.length().coerceAtLeast(1L) * 3L)
                .coerceAtMost(12L * 1024L * 1024L * 1024L) + 64L * 1024L * 1024L
            val root = ProjectStorageManager.chooseRoot(
                context, bound + ProjectStorageManager.IMPORT_SAFETY_MARGIN_BYTES,
            )
            val dir = File(root.directory, "normalized").apply {
                require(isDirectory || mkdirs()) { "Dossier des copies de travail indisponible" }
            }
            val output = File(dir, "source-pixels-carres-${UUID.randomUUID()}.mp4")
            val attempts = listOf("h264_mediacodec", "mpeg4")
            val details = mutableListOf<String>()
            try {
                for (codec in attempts) {
                    output.delete()
                    val args = mutableListOf(
                        "-hide_banner", "-loglevel", "error", "-y",
                        "-i", input,
                        "-map", "0:v:0", "-map", "0:a?",
                        "-vf", "scale=${width}:${height}:flags=bicubic,setsar=1/1,format=yuv420p",
                        "-c:v", codec,
                        "-b:v", "4000000",
                        "-c:a", "aac", "-b:a", "128000",
                        "-movflags", "+faststart",
                        "-fs", bound.toString(),
                        output.absolutePath,
                    )
                    FabVidDiagnostics.mark("NORMALIZE_COPY_TRY codec=$codec size=${width}x$height")
                    val session = FfprobeNativeGate.run { FFmpegKit.executeWithArguments(args.toTypedArray()) }
                    val success = ReturnCode.isSuccess(session.returnCode) && output.isFile &&
                        output.length() > 0L && output.length() < (bound - 1024L)
                    if (success) {
                        FabVidDiagnostics.mark("NORMALIZE_COPY_OK codec=$codec bytes=${output.length()}")
                        return@withPath Created(ProjectStorageManager.uriForPrivateFile(output), width, height, codec)
                    }
                    details += "$codec: ${session.output.orEmpty().replace(Regex("\\s+"), " ").takeLast(220)}"
                }
                error("Copie normalisée non créée : ${details.joinToString(" | ").takeLast(450)}")
            } catch (error: Throwable) {
                output.delete()
                throw error
            }
        }
    }
}
