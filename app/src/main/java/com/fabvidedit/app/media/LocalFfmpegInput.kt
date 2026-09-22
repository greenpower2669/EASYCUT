package com.fabvidedit.app.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gives FFmpeg/FFprobe a real local filesystem path regardless of the Android URI provider.
 *
 * Android entry points remain URI-first (content://, FileProvider, SAF). Immediately before the
 * native FFmpeg layer, content is copied to a short-lived app-private working file. This avoids
 * provider/SAF protocol differences between ACTION_VIEW, sharesheet and the system picker.
 */
object LocalFfmpegInput {
    private const val TAG = "FabVidFfmpegInput"
    private const val STORAGE_SAFETY_MARGIN_BYTES = 64L * 1024L * 1024L

    suspend fun <T> withPath(
        context: Context,
        uri: Uri,
        block: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        IncomingMediaResolver.resolveOwnedProjectFile(context, uri)?.let { ownedFile ->
            Log.i(TAG, "FFmpeg input=owned-project-file path=${ownedFile.absolutePath}")
            return@withContext block(ownedFile.absolutePath)
        }

        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = requireNotNull(uri.path) { "Chemin local absent pour le média" }
            val file = File(path)
            require(file.isFile && file.canRead()) { "Le fichier local n'est pas lisible" }
            Log.i(TAG, "FFmpeg input=direct-file path=$path")
            return@withContext block(path)
        }

        require(uri.scheme.equals("content", ignoreCase = true)) {
            "Schéma URI non pris en charge par FFmpeg : ${uri.scheme ?: "absent"}"
        }

        val metadata = queryMetadata(context, uri)
        val workDir = File(context.cacheDir, "ffmpeg_inputs").apply {
            require(exists() || mkdirs()) { "Impossible de préparer le cache FFmpeg" }
        }
        metadata.sizeBytes?.takeIf { it > 0L }?.let { size ->
            require(size + STORAGE_SAFETY_MARGIN_BYTES < workDir.usableSpace) {
                "Espace insuffisant pour préparer ce média pour FFmpeg"
            }
        }

        val suffix = metadata.displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.length in 1..12 && it.all { c -> c.isLetterOrDigit() } }
            ?.let { ".$it" }
            ?: ".media"
        val workingFile = File(workDir, "${UUID.randomUUID()}$suffix")

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Impossible d'ouvrir le média pour FFmpeg")
            input.use { source ->
                workingFile.outputStream().buffered().use { destination ->
                    source.copyTo(destination, DEFAULT_BUFFER_SIZE)
                }
            }
            require(workingFile.length() > 0L) { "Le média préparé pour FFmpeg est vide" }
            Log.i(
                TAG,
                "FFmpeg input=local-copy uri=$uri bytes=${workingFile.length()} path=${workingFile.absolutePath}",
            )
            block(workingFile.absolutePath)
        } finally {
            if (workingFile.exists() && !workingFile.delete()) {
                Log.w(TAG, "Unable to delete temporary FFmpeg input ${workingFile.absolutePath}")
            }
        }
    }

    private fun queryMetadata(context: Context, uri: Uri): SourceMetadata {
        var displayName: String? = null
        var sizeBytes: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) displayName = cursor.getString(nameColumn)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) sizeBytes = cursor.getLong(sizeColumn)
            }
        }
        return SourceMetadata(displayName, sizeBytes)
    }

    private data class SourceMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
    )
}
