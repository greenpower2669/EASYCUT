package com.fabvidedit.app.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.fabvidedit.app.model.MediaStreamInfo
import com.fabvidedit.app.model.MediaStreamInventory
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A container after FFprobe inventory, with every A/V stream exposed as an independent source. */
data class ImportedMediaContainer(
    val id: String,
    val originalUri: String,
    val name: String,
    val containerName: String?,
    val durationMs: Long,
    val videoSources: List<ImportedVideoSource>,
    val audioSources: List<ImportedAudioSource>,
)

data class ImportedVideoSource(
    val id: String,
    val containerId: String,
    val containerUri: String,
    val uri: String,
    val streamIndex: Int,
    val resolutionSegmentIndex: Int = 0,
    val timelineOffsetMs: Long = 0L,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val codecName: String?,
    val frameRate: Float?,
)

data class ImportedAudioSource(
    val id: String,
    val containerId: String,
    val containerUri: String,
    val uri: String,
    val streamIndex: Int,
    val timelineOffsetMs: Long = 0L,
    val name: String,
    val durationMs: Long,
    val codecName: String?,
)

/**
 * v0.12 import strategy:
 * 1) keep one app-owned clone of the original container;
 * 2) scan the original frames and cut whenever width x height changes;
 * 3) materialize each constant-resolution region directly, instead of remuxing the complete stream;
 * 4) keep -c copy and automatically try another real container if one muxer rejects the packets.
 *
 * Resolution alone decides logical video track boundaries. PTS only preserves timeline/audio alignment.
 */
object StreamSourceMaterializer {
    private const val TAG = "FabVidStreams"

    suspend fun split(
        context: Context,
        stableUri: Uri,
        displayName: String,
        inventory: MediaStreamInventory,
        onProgress: (String) -> Unit = {},
    ): ImportedMediaContainer = withContext(Dispatchers.IO) {
        require(inventory.videoStreams.isNotEmpty()) {
            "Le container ne contient aucun stream vidéo exploitable"
        }
        val containerId = UUID.randomUUID().toString()
        val cloneFile = IncomingMediaResolver.resolveOwnedProjectFile(context, stableUri)
        val sourceBytes = cloneFile?.length()?.takeIf { it > 0L }
        val storage = cloneFile?.let { ProjectStorageManager.rootContaining(context, it) }
            ?: ProjectStorageManager.chooseRoot(context, ProjectStorageManager.IMPORT_SAFETY_MARGIN_BYTES)
        val remainingNeeded = (sourceBytes ?: 0L) + ProjectStorageManager.IMPORT_SAFETY_MARGIN_BYTES
        val maxWorkspaceBytes = sourceBytes
            ?.let { (it * 3L + 256L * 1024L * 1024L).coerceAtLeast(512L * 1024L * 1024L) }
            ?: 2L * 1024L * 1024L * 1024L
        val maxSingleOutputBytes = sourceBytes
            ?.let { (it * 2L + 64L * 1024L * 1024L).coerceAtLeast(128L * 1024L * 1024L) }
            ?: 1L * 1024L * 1024L * 1024L
        var producedBytes = 0L
        require(storage.directory.usableSpace > remainingNeeded) {
            "Espace insuffisant pour extraire les pistes : ${ProjectStorageManager.formatBytes(remainingNeeded)} nécessaires, " +
                "${ProjectStorageManager.formatBytes(storage.directory.usableSpace)} disponibles sur ${storage.label}"
        }
        val directory = File(storage.directory, "streams/$containerId").apply {
            require(exists() || mkdirs()) { "Impossible de créer le stockage des sources vidéo" }
        }
        Log.i(TAG, "Stream workspace storage=${storage.label} removable=${storage.removable} path=${directory.absolutePath}")

        val allTimedStreams = inventory.videoStreams + inventory.audioStreams
        val originMs = allTimedStreams.minOfOrNull { it.startTimeMs }?.coerceAtLeast(0L) ?: 0L

        try {
            LocalFfmpegInput.withPath(context, stableUri) { input ->
            // Crucial order: inspect the untouched clone BEFORE asking any muxer to rewrite it.
            onProgress("Vérification des résolutions en flux (images clés)…")
            val resolutionSegments = ResolutionFrameScanner.scanInput(input, inventory, directory, sourceBytes)
            onProgress("Préparation des pistes sans réencodage…")

            val videoSources = inventory.videoStreams.flatMap { stream ->
                onProgress("Préparation vidéo " + (inventory.videoStreams.indexOf(stream) + 1) + "/" + inventory.videoStreams.size + "…")
                val segments = resolutionSegments[stream.index].orEmpty().ifEmpty {
                    listOf(
                        ResolutionSegment(
                            streamIndex = stream.index,
                            segmentIndex = 0,
                            startMs = stream.startTimeMs.coerceAtLeast(0L),
                            endMs = stream.startTimeMs.coerceAtLeast(0L) +
                                (stream.durationMs ?: inventory.durationMs).coerceAtLeast(1L),
                            width = stream.width.coerceAtLeast(1),
                            height = stream.height.coerceAtLeast(1),
                        ),
                    )
                }

                segments.map { segment ->
                    val baseName = "video-${stream.index}-r${segment.segmentIndex}-${segment.width}x${segment.height}"
                    val materialized = materializeVideoSegment(
                        input = input,
                        stream = stream,
                        directory = directory,
                        baseName = baseName,
                        startMs = segment.startMs,
                        durationMs = segment.durationMs,
                        maxOutputBytes = maxSingleOutputBytes,
                    )
                    producedBytes += materialized.file.length()
                    require(producedBytes <= maxWorkspaceBytes) {
                        "Protection stockage FabVidEdit : extraction interrompue à " +
                            ProjectStorageManager.formatBytes(producedBytes) +
                            " pour une source de " + ProjectStorageManager.formatBytes(sourceBytes ?: 0L)
                    }
                    val id = UUID.randomUUID().toString()
                    val source = ImportedVideoSource(
                        id = id,
                        containerId = containerId,
                        containerUri = "fabvid://container/$containerId",
                        uri = ownedUri(context, materialized.file).toString(),
                        streamIndex = stream.index,
                        resolutionSegmentIndex = segment.segmentIndex,
                        timelineOffsetMs = (segment.startMs - originMs).coerceAtLeast(0L),
                        name = "$displayName • ${segment.width}x${segment.height} • ${materialized.label} • V${stream.index}.R${segment.segmentIndex}",
                        durationMs = segment.durationMs,
                        width = segment.width,
                        height = segment.height,
                        rotationDegrees = stream.rotationDegrees,
                        codecName = stream.codecName,
                        frameRate = stream.frameRate,
                    )
                    Log.i(
                        TAG,
                        "LOGICAL VIDEO source=$id stream=${stream.index} segment=${segment.segmentIndex} " +
                            "${source.width}x${source.height} container=${materialized.label} " +
                            "timelineOffset=${source.timelineOffsetMs} durationMs=${source.durationMs} uri=${source.uri}",
                    )
                    source
                }
            }

            val audioSources = inventory.audioStreams.map { stream ->
                onProgress("Préparation audio " + (inventory.audioStreams.indexOf(stream) + 1) + "/" + inventory.audioStreams.size + "…")
                val materialized = materializeAudioStream(
                    input = input,
                    stream = stream,
                    directory = directory,
                    baseName = "audio-${stream.index}",
                    maxOutputBytes = maxSingleOutputBytes,
                )
                producedBytes += materialized.file.length()
                require(producedBytes <= maxWorkspaceBytes) {
                    "Protection stockage FabVidEdit : extraction interrompue à " +
                        ProjectStorageManager.formatBytes(producedBytes) +
                        " pour une source de " + ProjectStorageManager.formatBytes(sourceBytes ?: 0L)
                }
                val id = UUID.randomUUID().toString()
                val source = ImportedAudioSource(
                    id = id,
                    containerId = containerId,
                    containerUri = "fabvid://container/$containerId",
                    uri = ownedUri(context, materialized.file).toString(),
                    streamIndex = stream.index,
                    timelineOffsetMs = (stream.startTimeMs - originMs).coerceAtLeast(0L),
                    name = "$displayName • AUDIO ${stream.index} • ${materialized.label}",
                    durationMs = (stream.durationMs ?: inventory.durationMs).coerceAtLeast(1L),
                    codecName = stream.codecName,
                )
                Log.i(
                    TAG,
                    "Project source ID=$id AUDIO stream=${stream.index} container=${materialized.label} " +
                        "uri=${source.uri} offset=${source.timelineOffsetMs} durationMs=${source.durationMs}",
                )
                source
            }

                ImportedMediaContainer(
                    id = containerId,
                    originalUri = "fabvid://container/$containerId",
                    name = displayName,
                    containerName = inventory.containerName,
                    durationMs = inventory.durationMs.coerceAtLeast(1L),
                    videoSources = videoSources,
                    audioSources = audioSources,
                )
            }
        } catch (error: Throwable) {
            val bytes = directory.walkTopDown().filter(File::isFile).sumOf(File::length)
            directory.deleteRecursively()
            Log.w(TAG, "Failed import workspace removed bytes=$bytes path=${directory.absolutePath}", error)
            throw error
        }
    }

    private fun materializeVideoSegment(
        input: String,
        stream: MediaStreamInfo,
        directory: File,
        baseName: String,
        startMs: Long,
        durationMs: Long,
        maxOutputBytes: Long,
    ): MaterializedFile {
        val failures = mutableListOf<String>()
        for (candidate in videoCandidates(stream.codecName)) {
            val output = File(directory, "$baseName.${candidate.extension}")
            val result = streamCopy(
                input = input,
                stream = stream,
                output = output,
                video = true,
                startMs = startMs,
                durationMs = durationMs,
                candidate = candidate,
                maxOutputBytes = maxOutputBytes,
            )
            if (result.success) {
                Log.i(
                    TAG,
                    "AUTOCUT READY stream=${stream.index} ${seconds(startMs)}+${seconds(durationMs)} " +
                        "codec=${stream.codecName ?: "?"} mux=${candidate.label} bytes=${output.length()}",
                )
                return MaterializedFile(output, candidate.label)
            }
            failures += "${candidate.label}: ${result.diagnostic}"
        }
        error(
            "Impossible d'autocouper le stream ${stream.index} sans réencodage après plusieurs conteneurs : " +
                failures.joinToString(" | ").takeLast(1_200),
        )
    }

    private fun materializeAudioStream(
        input: String,
        stream: MediaStreamInfo,
        directory: File,
        baseName: String,
        maxOutputBytes: Long,
    ): MaterializedFile {
        val failures = mutableListOf<String>()
        for (candidate in audioCandidates(stream.codecName)) {
            val output = File(directory, "$baseName.${candidate.extension}")
            val result = streamCopy(
                input = input,
                stream = stream,
                output = output,
                video = false,
                startMs = null,
                durationMs = null,
                candidate = candidate,
                maxOutputBytes = maxOutputBytes,
            )
            if (result.success) return MaterializedFile(output, candidate.label)
            failures += "${candidate.label}: ${result.diagnostic}"
        }
        error(
            "Impossible de séparer l'audio ${stream.index} sans réencodage : " +
                failures.joinToString(" | ").takeLast(900),
        )
    }

    private fun streamCopy(
        input: String,
        stream: MediaStreamInfo,
        output: File,
        video: Boolean,
        startMs: Long?,
        durationMs: Long?,
        candidate: MuxCandidate,
        maxOutputBytes: Long,
    ): CopyResult {
        output.delete()
        val args = mutableListOf(
            "-hide_banner",
            "-loglevel", "error",
        )

        // Fast input seek is ideal for stream-copy and usually lands on the parameter/key frame
        // that introduces the new resolution.
        startMs?.takeIf { it > 0L }?.let {
            args += listOf("-ss", seconds(it))
        }
        args += listOf("-i", input)
        durationMs?.takeIf { it > 0L }?.let {
            args += listOf("-t", seconds(it))
        }
        args += listOf("-map", "0:${stream.index}")
        if (video) {
            args += listOf("-an", "-sn", "-dn")
        } else {
            args += listOf("-vn", "-sn", "-dn")
        }
        args += listOf(
            "-map_metadata", "-1",
            "-c", "copy",
            "-avoid_negative_ts", "make_zero",
            "-fs", maxOutputBytes.toString(),
        )
        args += candidate.extraArgs
        args += listOf("-f", candidate.muxer, "-y", output.absolutePath)

        Log.i(
            TAG,
            "COPY TRY stream=${stream.index} codec=${stream.codecName ?: "?"} mux=${candidate.label} " +
                "start=${startMs ?: 0L} duration=${durationMs ?: -1L}",
        )
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        val diagnostic = session.output.orEmpty().replace(Regex("\\s+"), " ").takeLast(700)
        val hitSizeGuard = output.exists() && output.length() >= (maxOutputBytes - 1024L).coerceAtLeast(1L)
        val success = ReturnCode.isSuccess(session.returnCode) && output.exists() && output.length() > 0L && !hitSizeGuard
        FFmpegKitConfig.clearSessions()
        if (!success) {
            Log.w(
                TAG,
                "COPY REJECTED stream=${stream.index} mux=${candidate.label} sizeGuard=$hitSizeGuard detail=$diagnostic",
            )
            output.delete()
        }
        return CopyResult(success, diagnostic)
    }

    private fun videoCandidates(codecName: String?): List<MuxCandidate> = when (codecName?.lowercase(Locale.US)) {
        // TS is intentionally first for AVC/HEVC: it tolerates changing SPS/PPS/VPS and odd timestamps
        // much better than forcing the complete source through Matroska.
        "h264", "hevc", "h265", "mpeg2video", "mpeg1video" -> listOf(
            MuxCandidate("ts", "mpegts", "MPEG-TS"),
            MuxCandidate("mp4", "mp4", "MP4"),
            MuxCandidate("mkv", "matroska", "MKV"),
        )
        "vp8", "vp9" -> listOf(
            MuxCandidate("webm", "webm", "WEBM"),
            MuxCandidate("mkv", "matroska", "MKV"),
        )
        "av1" -> listOf(
            MuxCandidate("mp4", "mp4", "MP4"),
            MuxCandidate("webm", "webm", "WEBM"),
            MuxCandidate("mkv", "matroska", "MKV"),
        )
        "mpeg4", "h263", "mjpeg", "prores" -> listOf(
            MuxCandidate("mp4", "mp4", "MP4"),
            MuxCandidate("mkv", "matroska", "MKV"),
        )
        else -> listOf(
            MuxCandidate("mkv", "matroska", "MKV"),
            MuxCandidate("mp4", "mp4", "MP4"),
            MuxCandidate("ts", "mpegts", "MPEG-TS"),
        )
    }

    private fun audioCandidates(codecName: String?): List<MuxCandidate> = when (codecName?.lowercase(Locale.US)) {
        "aac", "alac" -> listOf(
            MuxCandidate("m4a", "ipod", "M4A"),
            MuxCandidate("mka", "matroska", "MKA"),
        )
        "mp3" -> listOf(
            MuxCandidate("mp3", "mp3", "MP3"),
            MuxCandidate("mka", "matroska", "MKA"),
        )
        "opus", "vorbis" -> listOf(
            MuxCandidate("ogg", "ogg", "OGG"),
            MuxCandidate("mka", "matroska", "MKA"),
        )
        else -> listOf(
            MuxCandidate("mka", "matroska", "MKA"),
            MuxCandidate("m4a", "ipod", "M4A"),
        )
    }

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.6f", ms / 1_000.0)

    private fun ownedUri(context: Context, file: File): Uri =
        ProjectStorageManager.uriForPrivateFile(file)

    private data class MuxCandidate(
        val extension: String,
        val muxer: String,
        val label: String,
        val extraArgs: List<String> = emptyList(),
    )

    private data class CopyResult(
        val success: Boolean,
        val diagnostic: String,
    )

    private data class MaterializedFile(
        val file: File,
        val label: String,
    )
}
