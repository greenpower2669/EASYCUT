package com.fabvidedit.app.media

import android.content.Context
import android.net.Uri
import com.fabvidedit.app.FabVidDiagnostics
import com.fabvidedit.app.model.MediaStreamInventory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Experimental no-split import. Both logical tracks reference the SAME durable A/V container.
 * No FFmpeg remux, no audio extraction, no optional frame scan; FFprobe inventory still precedes it.
 * For multi-stream sources the user can uncheck the option and use the unchanged split pipeline.
 */
internal object UnifiedSourceMaterializer {
    suspend fun materialize(
        context: Context,
        stableUri: Uri,
        displayName: String,
        inventory: MediaStreamInventory,
        onProgress: (String) -> Unit = {},
    ): ImportedMediaContainer = withContext(Dispatchers.IO) {
        UnifiedImportPolicy.requireSupported(inventory.videoStreams.size, inventory.audioStreams.size)
        val containerId = UUID.randomUUID().toString()
        val ownedFile = IncomingMediaResolver.resolveOwnedProjectFile(context, stableUri)
            ?: error("Import unifié : source locale durable introuvable")
        // An external media's disposable incoming/ clone must become a durable project source.
        // An already-owned source is reused, avoiding a second full-sized copy.
        val sourceFile = if (ownedFile.parentFile?.name == "incoming") {
            onProgress("Conservation du conteneur vidéo et audio…")
            val root = ProjectStorageManager.rootContaining(context, ownedFile)
                ?: error("Import unifié : stockage de la source introuvable")
            val directory = File(root.directory, "streams/$containerId")
            require(directory.isDirectory || directory.mkdirs()) {
                "Import unifié : impossible de préparer le dossier de la source"
            }
            val destination = File(directory, ownedFile.name)
            require(ownedFile.renameTo(destination)) {
                "Import unifié : impossible de conserver le conteneur sans nouvelle copie"
            }
            destination
        } else ownedFile

        val sourceUri = ProjectStorageManager.uriForPrivateFile(sourceFile).toString()
        val containerUri = "fabvid://container/$containerId"
        val video = inventory.videoStreams.single()
        val originMs = (inventory.videoStreams + inventory.audioStreams)
            .minOfOrNull { it.startTimeMs }?.coerceAtLeast(0L) ?: 0L
        val videoDuration = (video.durationMs ?: inventory.durationMs).coerceAtLeast(1L)
        val videoSource = ImportedVideoSource(
            id = UUID.randomUUID().toString(),
            containerId = containerId,
            containerUri = containerUri,
            uri = sourceUri,
            streamIndex = video.index,
            timelineOffsetMs = (video.startTimeMs - originMs).coerceAtLeast(0L),
            name = "$displayName • source vidéo liée",
            durationMs = videoDuration,
            width = video.width,
            height = video.height,
            rotationDegrees = video.rotationDegrees,
            codecName = video.codecName,
            frameRate = video.frameRate,
            sampleAspectRatio = video.sampleAspectRatio,
            displayAspectRatio = video.displayAspectRatio,
            // False means "no variation detected"; this path does not perform a frame scan.
            aspectVaries = false,
        )
        val audioSources = inventory.audioStreams.map { audio ->
            ImportedAudioSource(
                id = UUID.randomUUID().toString(),
                containerId = containerId,
                containerUri = containerUri,
                uri = sourceUri,
                streamIndex = audio.index,
                timelineOffsetMs = (audio.startTimeMs - originMs).coerceAtLeast(0L),
                name = "$displayName • son lié (sans copie)",
                durationMs = (audio.durationMs ?: inventory.durationMs).coerceAtLeast(1L),
                codecName = audio.codecName,
            )
        }
        FabVidDiagnostics.mark("IMPORT_UNIFIED_READY")
        ImportedMediaContainer(
            id = containerId,
            originalUri = containerUri,
            name = displayName,
            containerName = inventory.containerName,
            durationMs = inventory.durationMs.coerceAtLeast(1L),
            videoSources = listOf(videoSource),
            audioSources = audioSources,
        )
    }
}
