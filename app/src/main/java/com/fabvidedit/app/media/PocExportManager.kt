package com.fabvidedit.app.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.fabvidedit.app.model.PocExportSettings
import com.fabvidedit.app.model.PocRatioMode
import com.fabvidedit.app.model.PocStreamExportPlan
import com.fabvidedit.app.model.PocVideoCodec
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VideoFormatAnalysis
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PocExportManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onState: (ExportState) -> Unit,
) {
    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var temporaryFile: File? = null
    private var normalizedSourceFile: File? = null

    fun start(
        clip: VideoClip,
        trimStartMs: Long,
        trimEndMs: Long,
        settings: PocExportSettings,
        analysis: VideoFormatAnalysis?,
        streamPlan: PocStreamExportPlan? = null,
    ) {
        cancel(resetState = false)
        val registeredSelection = if (streamPlan == null) PocStreamSelectionRegistry.get(clip.uri) else null
        val effectivePlan = streamPlan ?: registeredSelection?.plan
        val effectiveAnalysis = registeredSelection?.selectedAnalysis ?: analysis

        if (effectivePlan != null && effectivePlan.inventory.videoStreams.size > 1) {
            if (effectivePlan.keptVideoIndexes.size > 1) {
                startMultiTrackRemux(clip, trimStartMs, trimEndMs, effectivePlan)
            } else {
                prepareSelectedTrackAndStartTransformer(
                    clip = clip,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    settings = settings,
                    analysis = effectiveAnalysis,
                    streamPlan = effectivePlan,
                )
            }
            return
        }
        startTransformer(
            clip = clip,
            sourceUri = Uri.parse(clip.uri),
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            settings = settings,
            analysis = effectiveAnalysis,
        )
    }

    private fun prepareSelectedTrackAndStartTransformer(
        clip: VideoClip,
        trimStartMs: Long,
        trimEndMs: Long,
        settings: PocExportSettings,
        analysis: VideoFormatAnalysis?,
        streamPlan: PocStreamExportPlan,
    ) {
        onState(ExportState.Running(0, "Préparation de la piste vidéo ${streamPlan.selectedVideoIndex}…"))
        scope.launch {
            val normalized = runCatching {
                withContext(Dispatchers.IO) { remuxSelectedTrack(clip, streamPlan) }
            }.getOrElse { error ->
                onState(ExportState.Error(error.localizedMessage ?: "Impossible de préparer la piste sélectionnée"))
                return@launch
            }
            normalizedSourceFile = normalized
            startTransformer(
                clip = clip,
                sourceUri = Uri.fromFile(normalized),
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                settings = settings,
                analysis = analysis,
            )
        }
    }

    private fun startTransformer(
        clip: VideoClip,
        sourceUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        settings: PocExportSettings,
        analysis: VideoFormatAnalysis?,
    ) {
        val sourceWidth = analysis?.sourceFormat?.width?.takeIf { it > 0 } ?: clip.width.coerceAtLeast(2)
        val sourceHeight = analysis?.sourceFormat?.height?.takeIf { it > 0 } ?: clip.height.coerceAtLeast(2)
        val (targetWidth, targetHeight) = if (settings.ratioMode == PocRatioMode.ORIGINAL) {
            even(sourceWidth) to even(sourceHeight)
        } else {
            settings.outputSize(analysis, clip.width, clip.height)
        }
        val start = trimStartMs.coerceIn(0L, clip.durationMs - 1L)
        val end = trimEndMs.coerceIn(start + 1L, clip.durationMs)
        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(start)
                    .setEndPositionMs(end)
                    .build(),
            )
            .build()

        val editedBuilder = EditedMediaItem.Builder(mediaItem)
            .setDurationUs((end - start) * 1_000L)
        settings.frameRate.fps?.let(editedBuilder::setFrameRate)
        val edited = editedBuilder.build()
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))

        val layout = when (settings.ratioMode) {
            PocRatioMode.CROP, PocRatioMode.FILL -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            PocRatioMode.PRESERVE, PocRatioMode.FIT, PocRatioMode.ORIGINAL -> Presentation.LAYOUT_SCALE_TO_FIT
        }
        val presentation = Presentation.createForWidthAndHeight(targetWidth, targetHeight, layout)
        val composition = Composition.Builder(sequence)
            .setEffects(Effects(emptyList(), listOf(presentation)))
            .build()

        val safeName = safeBaseName(clip)
        val fileName = "$safeName-POC-${System.currentTimeMillis()}.mp4"
        val outputDirectory = File(context.cacheDir, "FabVidEditPocExports").apply { mkdirs() }
        val output = File(outputDirectory, fileName)
        temporaryFile = output

        val videoBitrate = settings.requestedVideoBitrate(targetWidth, targetHeight)
        val encoderBuilder = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(videoBitrate)
                    .build(),
            )
        settings.audioBitrateKbps?.let { kbps ->
            encoderBuilder.setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(kbps.coerceIn(32, 512) * 1_000)
                    .build(),
            )
        }

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                progressJob?.cancel()
                onState(ExportState.Running(100, "Enregistrement dans Films/FabVidEdit"))
                scope.launch {
                    runCatching { publishToGallery(output, fileName, "video/mp4") }
                        .onSuccess { uri ->
                            output.delete()
                            normalizedSourceFile?.delete()
                            normalizedSourceFile = null
                            temporaryFile = null
                            transformer = null
                            onState(ExportState.Success(uri, fileName))
                        }
                        .onFailure { error ->
                            cleanupFiles()
                            onState(ExportState.Error(error.localizedMessage ?: "Impossible d’enregistrer la vidéo"))
                        }
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                progressJob?.cancel()
                cleanupFiles()
                transformer = null
                onState(ExportState.Error(exportException.localizedMessage ?: "Échec de l’export vidéo"))
            }
        }

        val transformerBuilder = Transformer.Builder(context)
            .setVideoMimeType(
                if (settings.codec == PocVideoCodec.HEVC) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264,
            )
            .setEncoderFactory(encoderBuilder.build())
            .setPortraitEncodingEnabled(true)
            .addListener(listener)
        if (settings.audioBitrateKbps != null) transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)

        val currentTransformer = transformerBuilder.build()
        transformer = currentTransformer
        onState(
            ExportState.Running(
                0,
                "${settings.codec.label} • ${targetWidth}×${targetHeight} • ${settings.frameRate.label}",
            ),
        )
        currentTransformer.start(composition, output.absolutePath)
        watchProgress(currentTransformer)
    }

    private fun startMultiTrackRemux(
        clip: VideoClip,
        trimStartMs: Long,
        trimEndMs: Long,
        streamPlan: PocStreamExportPlan,
    ) {
        onState(ExportState.Running(0, "Remux multi-pistes explicite…"))
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val outputDirectory = File(context.cacheDir, "FabVidEditPocExports").apply { mkdirs() }
                    val fileName = "${safeBaseName(clip)}-multi-${System.currentTimeMillis()}.mkv"
                    val output = File(outputDirectory, fileName)
                    temporaryFile = output
                    val input = FFmpegKitConfig.getSafParameterForRead(context, Uri.parse(clip.uri))
                    val start = trimStartMs.coerceIn(0L, clip.durationMs - 1L)
                    val end = trimEndMs.coerceIn(start + 1L, clip.durationMs)
                    val args = mutableListOf(
                        "-hide_banner", "-loglevel", "error",
                        "-ss", seconds(start),
                        "-i", input,
                        "-t", seconds(end - start),
                    )
                    streamPlan.keptVideoIndexes.sorted().forEach { index ->
                        args += listOf("-map", "0:$index")
                    }
                    streamPlan.selectedAudioIndex?.let { index -> args += listOf("-map", "0:$index") }
                    if (streamPlan.keepSubtitles) args += listOf("-map", "0:s?")
                    args += listOf(
                        "-map_metadata", "0",
                        "-c", "copy",
                        "-avoid_negative_ts", "make_zero",
                        "-y",
                        output.absolutePath,
                    )
                    val session = FFmpegKit.executeWithArguments(args.toTypedArray())
                    if (!ReturnCode.isSuccess(session.returnCode) || !output.exists()) {
                        output.delete()
                        error("Remux multi-pistes impossible : ${session.output.takeLast(700)}")
                    }
                    fileName to output
                }
            }
            result.onSuccess { (fileName, output) ->
                onState(ExportState.Running(100, "Enregistrement dans Films/FabVidEdit"))
                runCatching { publishToGallery(output, fileName, "video/x-matroska") }
                    .onSuccess { uri ->
                        output.delete()
                        temporaryFile = null
                        onState(ExportState.Success(uri, fileName))
                    }
                    .onFailure { error ->
                        cleanupFiles()
                        onState(ExportState.Error(error.localizedMessage ?: "Impossible d’enregistrer le remux"))
                    }
            }.onFailure { error ->
                cleanupFiles()
                onState(ExportState.Error(error.localizedMessage ?: "Échec du remux multi-pistes"))
            }
        }
    }

    private fun remuxSelectedTrack(clip: VideoClip, streamPlan: PocStreamExportPlan): File {
        val outputDirectory = File(context.cacheDir, "FabVidEditSelectedStreams").apply { mkdirs() }
        val output = File(outputDirectory, "selected-${System.currentTimeMillis()}-${streamPlan.selectedVideoIndex}.mkv")
        val input = FFmpegKitConfig.getSafParameterForRead(context, Uri.parse(clip.uri))
        val args = mutableListOf(
            "-hide_banner", "-loglevel", "error",
            "-i", input,
            "-map", "0:${streamPlan.selectedVideoIndex}",
        )
        streamPlan.selectedAudioIndex?.let { index -> args += listOf("-map", "0:$index") }
        args += listOf(
            "-map_metadata", "0",
            "-c", "copy",
            "-avoid_negative_ts", "make_zero",
            "-y",
            output.absolutePath,
        )
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        if (!ReturnCode.isSuccess(session.returnCode) || !output.exists()) {
            output.delete()
            error("Mapping FFmpeg impossible : ${session.output.takeLast(700)}")
        }
        return output
    }

    fun cancel(resetState: Boolean = true) {
        progressJob?.cancel()
        progressJob = null
        transformer?.cancel()
        transformer = null
        cleanupFiles()
        if (resetState) onState(ExportState.Idle)
    }

    private fun watchProgress(current: Transformer) {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main.immediate) {
            val holder = ProgressHolder()
            while (isActive && transformer === current) {
                if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onState(ExportState.Running(holder.progress.coerceIn(0, 99)))
                }
                delay(350)
            }
        }
    }

    private suspend fun publishToGallery(source: File, fileName: String, mimeType: String): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FabVidEdit")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("La galerie Android a refusé le fichier")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("Impossible d’ouvrir le fichier de destination")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null,
            )
            uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun cleanupFiles() {
        temporaryFile?.delete()
        temporaryFile = null
        normalizedSourceFile?.delete()
        normalizedSourceFile = null
    }

    private fun safeBaseName(clip: VideoClip): String = clip.name
        .substringBeforeLast('.')
        .replace(Regex("[^a-zA-Z0-9À-ÿ_-]+"), "-")
        .trim('-')
        .take(44)
        .ifBlank { "FabVidEdit-POC" }

    private fun seconds(timeMs: Long): String = String.format(Locale.US, "%.3f", timeMs / 1_000.0)

    private fun even(value: Int): Int = if (value % 2 == 0) value.coerceAtLeast(2) else (value - 1).coerceAtLeast(2)
}
