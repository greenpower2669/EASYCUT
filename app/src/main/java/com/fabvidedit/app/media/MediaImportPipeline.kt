package com.fabvidedit.app.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.fabvidedit.app.FabVidDiagnostics
import com.fabvidedit.app.model.MediaAssetInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single entry point for every import source. The actual container is first inventoried by FFprobe;
 * video-project imports then materialize one stable source per A/V stream.
 */
object MediaImportPipeline {
    private const val TAG = "FabVidImport"

    suspend fun importContainer(
        context: Context,
        uri: Uri,
        onProgress: (String) -> Unit = {},
    ): ImportedMediaContainer =
        withContext(Dispatchers.IO) {
            onProgress("Copie locale en flux si nécessaire…")
            val alreadyOwned = IncomingMediaResolver.resolveOwnedProjectFile(context, uri) != null
            val stableUri = importStage("PRÉPARATION URI") {
                IncomingMediaResolver.ensureOwnedClone(context, uri)
            }
            val cloneFile = if (alreadyOwned) null else IncomingMediaResolver.resolveOwnedProjectFile(context, stableUri)
            try {
                val name = displayName(context, uri)
                Log.i(TAG, "Resolved disposable import clone=$stableUri")

                onProgress("Inventaire des pistes (métadonnées courtes)…")
                val inventory = importStage("FFPROBE") {
                    FfprobeMediaInspector.inspect(context, stableUri)
                }
                importStage("VALIDATION STREAMS") {
                    require(inventory.videoStreams.isNotEmpty()) {
                        "Le container ne contient aucun stream vidéo exploitable"
                    }
                }
                logInventory(inventory)

                val imported = importStage("SÉPARATION STREAMS") {
                    StreamSourceMaterializer.split(
                        context = context,
                        stableUri = stableUri,
                        displayName = name,
                        inventory = inventory,
                        onProgress = onProgress,
                    )
                }
                Log.i(
                    TAG,
                    "Timeline sources created=${imported.videoSources.size} video + ${imported.audioSources.size} audio",
                )
                imported
            } finally {
                cloneFile?.takeIf { it.parentFile?.name == "incoming" }?.let { clone ->
                    val bytes = clone.length()
                    if (clone.delete()) Log.i(TAG, "Import clone released bytes=$bytes path=${clone.absolutePath}")
                }
            }
        }

    /** Generic metadata inspection, also valid for an audio-only file selected as background audio. */
    suspend fun inspect(context: Context, uri: Uri): MediaAssetInfo = withContext(Dispatchers.IO) {
        val stableUri = IncomingMediaResolver.ensureDurable(context, uri)
        val inventory = FfprobeMediaInspector.inspect(context, stableUri)
        logInventory(inventory)
        val representative = inventory.videoStreams.firstOrNull()
        MediaAssetInfo(
            uri = stableUri.toString(),
            name = displayName(context, stableUri),
            durationMs = inventory.durationMs.coerceAtLeast(1L),
            width = representative?.width ?: 0,
            height = representative?.height ?: 0,
        )
    }

    private suspend fun <T> importStage(label: String, block: suspend () -> T): T = try {
        block()
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        FabVidDiagnostics.logError("IMPORT_STAGE " + label, error)
        val detail = diagnosticMessage(error)
        Log.e(TAG, "Import stage failed stage=$label detail=$detail", error)
        throw IllegalStateException("IMPORT / $label : $detail", error)
    }

    private fun diagnosticMessage(error: Throwable): String {
        val messages = generateSequence(error) { it.cause }
            .mapNotNull { it.localizedMessage?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .toList()
        return (messages.lastOrNull() ?: error.javaClass.simpleName ?: "erreur inconnue")
            .replace(Regex("\\s+"), " ")
            .takeLast(700)
    }

    private fun logInventory(inventory: com.fabvidedit.app.model.MediaStreamInventory) {
        Log.i(TAG, "Container=${inventory.containerName ?: "unknown"}")
        Log.i(TAG, "FFprobe result=streams:${inventory.streams.size} durationMs:${inventory.durationMs}")
        Log.i(TAG, "Video stream indexes=${inventory.videoStreams.joinToString { it.index.toString() }}")
        Log.i(TAG, "Audio stream indexes=${inventory.audioStreams.joinToString { it.index.toString() }}")
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fromCursor = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
        return fromCursor ?: uri.lastPathSegment ?: "Média"
    }
}
