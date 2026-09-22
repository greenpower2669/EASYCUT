package com.fabvidedit.app.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.fabvidedit.app.model.VideoProject
import com.fabvidedit.app.model.VisualMediaKind
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportVideoCodec(val label: String, val mimeType: String) {
    H264("H.264 • compatibilité", MimeTypes.VIDEO_H264),
    H265("H.265 / HEVC • taille réduite", MimeTypes.VIDEO_H265),
}

enum class ExportFrameRate(val label: String, val fps: Int?) {
    SOURCE("Source", null),
    FPS_24("24 fps", 24),
    FPS_25("25 fps", 25),
    FPS_30("30 fps", 30),
    FPS_50("50 fps", 50),
    FPS_60("60 fps", 60),
}

enum class ExportResolution(val label: String, val shortSide: Int?) {
    SOURCE("Source", null),
    P720("720p", 720),
    P1080("1080p", 1080),
    P1440("1440p", 1440),
    P2160("2160p / 4K", 2160),
}

enum class ExportBitrate(val label: String, val bitsPerSecond: Int?) {
    AUTO("Auto", null),
    MBPS_2("2 Mb/s", 2_000_000),
    MBPS_4("4 Mb/s", 4_000_000),
    MBPS_8("8 Mb/s", 8_000_000),
    MBPS_12("12 Mb/s", 12_000_000),
    MBPS_20("20 Mb/s", 20_000_000),
    MBPS_35("35 Mb/s", 35_000_000),
    MBPS_50("50 Mb/s", 50_000_000),
}

data class ExportSettings(
    val codec: ExportVideoCodec = ExportVideoCodec.H264,
    val frameRate: ExportFrameRate = ExportFrameRate.SOURCE,
    val resolution: ExportResolution = ExportResolution.P1080,
    val bitrate: ExportBitrate = ExportBitrate.AUTO,
)

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Int, val message: String = "Export en cours") : ExportState
    data class Success(val uri: Uri, val fileName: String) : ExportState
    data class Error(val message: String) : ExportState
}

/**
 * Media3 export front-end.
 *
 * The validated import/recovery engine intentionally prefers MPEG-TS for AVC/HEVC because TS is
 * tolerant of odd timestamps and parameter-set changes. Transformer, however, uses its own
 * AssetLoader. v0.24 therefore creates disposable export proxies for TS assets without changing
 * the project or the repaired source files. The proxy is stream-copied (no generation loss), used
 * only by Transformer, and removed after success/failure/cancel.
 */
class ExportManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onState: (ExportState) -> Unit,
) {
    companion object {
        private const val TAG = "FabVidExport"
        private val TS_EXTENSIONS = setOf("ts", "mts", "m2ts")
    }

    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var preparationJob: Job? = null
    private var temporaryFile: File? = null
    private var preparedAssetDirectory: File? = null

    fun start(project: VideoProject, settings: ExportSettings) {
        cancel(resetState = false)
        if (settings.codec == ExportVideoCodec.H265 && !VideoEncoderCapabilities.hasHardwareHevcEncoder()) {
            onState(ExportState.Error("H.265 indisponible : aucun encodeur matériel HEVC compatible détecté"))
            return
        }
        onState(ExportState.Running(0, "Préparation des sources d’export"))

        preparationJob = scope.launch {
            val preparedProject = runCatching {
                withContext(Dispatchers.IO) { prepareProjectForTransformer(project) }
            }.getOrElse { error ->
                cleanupPreparedAssets()
                onState(
                    ExportState.Error(
                        diagnosticMessage(error, prefix = "Préparation export impossible"),
                    ),
                )
                return@launch
            }

            if (!isActive) {
                cleanupPreparedAssets()
                return@launch
            }
            preparationJob = null
            withContext(Dispatchers.Main.immediate) {
                startTransformer(preparedProject, settings)
            }
        }
    }

    /**
     * Builds a transient project used only by Transformer.
     *
     * 1) A/V sources produced by StreamSourceMaterializer have their audio explicitly detached:
     *    the video file was created with -an and the real audio already lives in SourceAudioTrack.
     * 2) App-owned TS video assets are remuxed to MP4 (MKV fallback) by stream-copy so the
     *    Transformer's AssetLoader receives a conventional seekable editing asset.
     */
    private fun prepareProjectForTransformer(project: VideoProject): VideoProject {
        val proxyBySourceUri = mutableMapOf<String, String>()
        var detachedAudioCount = 0
        var proxyCount = 0

        val preparedClips = project.clips.map { original ->
            var clip = original

            if (clip.mediaKind == VisualMediaKind.VIDEO && isSeparatedVideoSource(clip.containerUri, clip.sourceStreamIndex)) {
                // These files are deliberately video-only. Keeping volume/keyframe volume enabled
                // makes Media3 probe/process an audio track that cannot exist in this asset.
                clip = clip.copy(
                    volume = 0f,
                    keyframes = clip.keyframes.map { it.copy(volume = 0f) },
                )
                detachedAudioCount++
            }

            if (clip.mediaKind == VisualMediaKind.VIDEO && needsTsExportProxy(clip.uri)) {
                val proxyUri = proxyBySourceUri[clip.uri] ?: createTsExportProxy(clip.uri).also {
                    proxyBySourceUri[clip.uri] = it
                    proxyCount++
                }
                clip = clip.copy(uri = proxyUri)
            }
            clip
        }

        Log.i(
            TAG,
            "Export preflight clips=${project.clips.size} detachedVideoAudio=$detachedAudioCount tsProxies=$proxyCount sourceAudio=${project.sourceAudioTracks.size}",
        )
        return project.copy(clips = preparedClips)
    }

    private fun isSeparatedVideoSource(containerUri: String?, sourceStreamIndex: Int?): Boolean =
        sourceStreamIndex != null && containerUri?.startsWith("fabvid://container/") == true

    private fun needsTsExportProxy(rawUri: String): Boolean {
        val extension = runCatching {
            Uri.parse(rawUri).path?.substringAfterLast('.', "")?.lowercase().orEmpty()
        }.getOrDefault("")
        return extension in TS_EXTENSIONS
    }

    private fun createTsExportProxy(rawUri: String): String {
        val uri = Uri.parse(rawUri)
        val source = ProjectStorageManager.resolveAppOwnedFile(context, uri)
            ?: return rawUri
        if (!source.isFile || !source.canRead()) {
            error("Source vidéo introuvable ou illisible : ${source.name}")
        }

        val requiredBytes = source.length() + ProjectStorageManager.IMPORT_SAFETY_MARGIN_BYTES
        require(context.cacheDir.usableSpace > requiredBytes) {
            "Espace insuffisant pour le proxy d’export : ${ProjectStorageManager.formatBytes(requiredBytes)} nécessaires"
        }
        val directory = preparedAssetDirectory ?: File(
            context.cacheDir,
            "FabVidEditExportAssets/${System.currentTimeMillis()}-${System.nanoTime()}",
        ).apply {
            require(mkdirs() || isDirectory) { "Impossible de préparer le cache d’export" }
            preparedAssetDirectory = this
        }

        val base = "video-${source.nameWithoutExtension.hashCode().toUInt()}-${System.nanoTime()}"
        val failures = mutableListOf<String>()
        val candidates = listOf(
            // Disposable local proxy: faststart would add an unnecessary second full-file pass.
            ProxyCandidate("mp4", "MP4"),
            ProxyCandidate("mkv", "MKV"),
        )

        candidates.forEach { candidate ->
            val output = File(directory, "$base.${candidate.extension}")
            val args = mutableListOf(
                "-hide_banner", "-loglevel", "error",
                "-fflags", "+genpts",
                "-i", source.absolutePath,
                "-map", "0:v:0",
                "-an", "-sn", "-dn",
                "-map_metadata", "-1",
                "-c:v", "copy",
                "-avoid_negative_ts", "make_zero",
            )
            args += candidate.extraArgs
            args += listOf("-y", output.absolutePath)

            val session = FFmpegKit.executeWithArguments(args.toTypedArray())
            val detail = session.output.orEmpty().replace(Regex("\\s+"), " ").takeLast(600)
            val success = ReturnCode.isSuccess(session.returnCode) && output.isFile && output.length() > 0L
            FFmpegKitConfig.clearSessions()

            if (success) {
                Log.i(
                    TAG,
                    "TS export proxy ready mux=${candidate.label} sourceBytes=${source.length()} proxyBytes=${output.length()}",
                )
                return Uri.fromFile(output).toString()
            }
            output.delete()
            failures += "${candidate.label}: ${detail.ifBlank { "échec FFmpeg" }}"
        }

        error(
            "Remux TS pour Media3 impossible : ${failures.joinToString(" | ").takeLast(1_000)}",
        )
    }

    private fun startTransformer(project: VideoProject, settings: ExportSettings) {
        val safeProjectName = project.name
            .replace(Regex("[^a-zA-Z0-9À-ÿ_-]+"), "-")
            .trim('-')
            .take(48)
            .ifBlank { "FabVidEdit" }
        val fileName = "${safeProjectName}-${System.currentTimeMillis()}.mp4"
        val exportDirectory = File(context.cacheDir, "FabVidEditExports").apply { mkdirs() }
        val output = File(exportDirectory, fileName)
        temporaryFile = output

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                progressJob?.cancel()
                onState(ExportState.Running(100, "Enregistrement dans la galerie"))
                scope.launch {
                    runCatching { publishToGallery(output, fileName) }
                        .onSuccess { uri ->
                            cleanupAfterExport(output)
                            transformer = null
                            onState(ExportState.Success(uri, fileName))
                        }
                        .onFailure { error ->
                            cleanupAfterExport(output)
                            transformer = null
                            onState(
                                ExportState.Error(
                                    diagnosticMessage(error, prefix = "Export terminé mais enregistrement impossible"),
                                ),
                            )
                        }
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                progressJob?.cancel()
                val message = transformerDiagnostic(exportException)
                cleanupAfterExport(output)
                transformer = null
                onState(ExportState.Error(message))
            }
        }

        val encoderBuilder = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
        settings.bitrate.bitsPerSecond?.let { bitrate ->
            encoderBuilder.setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(bitrate)
                    .build(),
            )
        }

        val currentTransformer = Transformer.Builder(context)
            .setVideoMimeType(settings.codec.mimeType)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderBuilder.build())
            .addListener(listener)
            .build()
        transformer = currentTransformer
        onState(
            ExportState.Running(
                0,
                "${settings.codec.label} • ${settings.frameRate.label} • ${settings.resolution.label}",
            ),
        )

        runCatching {
            currentTransformer.start(
                CompositionFactory.create(
                    project = project,
                    resolutionShortSide = settings.resolution.shortSide,
                    frameRate = settings.frameRate.fps,
                ),
                output.absolutePath,
            )
        }.onFailure { error ->
            cleanupAfterExport(output)
            transformer = null
            onState(ExportState.Error(diagnosticMessage(error, prefix = "Démarrage export impossible")))
            return
        }
        watchProgress(currentTransformer)
    }

    fun cancel(resetState: Boolean = true) {
        preparationJob?.cancel()
        preparationJob = null
        progressJob?.cancel()
        progressJob = null
        transformer?.cancel()
        transformer = null
        temporaryFile?.delete()
        temporaryFile = null
        cleanupPreparedAssets()
        if (resetState) onState(ExportState.Idle)
    }

    private fun watchProgress(transformer: Transformer) {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main.immediate) {
            val holder = ProgressHolder()
            while (isActive && this@ExportManager.transformer === transformer) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onState(ExportState.Running(holder.progress.coerceIn(0, 99)))
                }
                delay(400)
            }
        }
    }

    private fun transformerDiagnostic(error: ExportException): String {
        val hadProxy = preparedAssetDirectory != null
        val chain = throwableChain(error)
        val proxyInfo = if (hadProxy) " • proxy TS→MP4/MKV actif" else ""
        return (
            "Media3 code ${error.errorCode}$proxyInfo\n" +
                chain.joinToString(" → ")
            ).take(1_200)
    }

    private fun diagnosticMessage(error: Throwable, prefix: String): String =
        "$prefix\n${throwableChain(error).joinToString(" → ")}".take(1_200)

    private fun throwableChain(error: Throwable): List<String> =
        generateSequence(error) { it.cause }
            .map { current ->
                val message = current.localizedMessage?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                if (message.isBlank()) current.javaClass.simpleName
                else "${current.javaClass.simpleName}: $message"
            }
            .distinct()
            .toList()

    private fun cleanupAfterExport(output: File) {
        output.delete()
        temporaryFile = null
        cleanupPreparedAssets()
    }

    private fun cleanupPreparedAssets() {
        preparedAssetDirectory?.let { directory ->
            val bytes = runCatching {
                directory.walkTopDown().filter(File::isFile).sumOf(File::length)
            }.getOrDefault(0L)
            directory.deleteRecursively()
            Log.i(TAG, "Export proxies released bytes=$bytes")
        }
        preparedAssetDirectory = null
    }

    private suspend fun publishToGallery(source: File, fileName: String): Uri =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/EASYCUT",
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: error("La galerie Android a refusé le fichier")
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

    private data class ProxyCandidate(
        val extension: String,
        val label: String,
        val extraArgs: List<String> = emptyList(),
    )
}
