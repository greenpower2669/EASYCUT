package com.fabvidedit.app.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves media received from Android implicit intents without ever converting a content:// URI
 * into a guessed filesystem path. Temporary grants are materialized into app-owned storage so a
 * saved FabVidEdit project remains reopenable after the sending Activity or device is restarted.
 */
object IncomingMediaResolver {
    private const val TAG = "FabVidIncoming"
    private const val STORAGE_SAFETY_MARGIN_BYTES = 64L * 1024L * 1024L

    data class Resolution(
        val uris: List<Uri>,
        val failures: List<String> = emptyList(),
    )

    fun supports(intent: Intent?): Boolean = when (intent?.action) {
        Intent.ACTION_VIEW,
        Intent.ACTION_SEND,
        Intent.ACTION_SEND_MULTIPLE,
        -> true
        else -> false
    }

    suspend fun resolve(context: Context, intent: Intent): Resolution = withContext(Dispatchers.IO) {
        val incoming = extractUris(intent)
        Log.i(TAG, "Incoming action=${intent.action}")
        Log.i(TAG, "Incoming MIME=${intent.type ?: "unknown"}")
        Log.i(TAG, "URI permission flags=0x${intent.flags.toString(16)}")

        if (incoming.isEmpty()) {
            return@withContext Resolution(emptyList(), listOf("Aucun média vidéo n’a été fourni par Android"))
        }

        val resolved = mutableListOf<Uri>()
        val failures = mutableListOf<String>()
        incoming.forEach { uri ->
            Log.i(TAG, "Incoming URI=$uri")
            runCatching {
                validateUri(uri)
                val actualMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                Log.i(TAG, "Resolved MIME=${actualMime ?: "unknown"}")
                requireReadable(context, uri)

                if (!isOwnedProjectUri(context, uri)) {
                    tryPersistReadPermission(context, intent, uri)
                }
                Log.i(TAG, "Resolved media source=$uri (clone deferred to import pipeline)")
                uri
            }.onSuccess { stableUri ->
                resolved += stableUri
            }.onFailure { error ->
                Log.w(TAG, "Incoming media rejected: ${error.message}")
                failures += (error.localizedMessage ?: "Média entrant illisible")
            }
        }

        Resolution(
            uris = resolved.distinctBy(Uri::toString),
            failures = failures,
        )
    }

    /**
     * Import/edit path: snapshot every external source once into app-owned storage.
     * FFprobe and FFmpeg can then share that exact disk clone instead of each preparing another copy.
     */
    suspend fun ensureOwnedClone(context: Context, uri: Uri): Uri = withContext(Dispatchers.IO) {
        validateUri(uri)
        requireReadable(context, uri)
        // App-owned media already has a durable path; a second full-size copy wastes storage.
        // MediaImportPipeline only removes disposable sources inside the incoming/ directory.
        if (isOwnedProjectUri(context, uri)) uri else copyIntoProjectStorage(context, uri)
    }

    /**
     * Resolve a URI produced by our FileProvider back to its private file. The canonical-path guard
     * prevents traversal outside filesDir/project_sources. Returns null for any foreign URI.
     */
    fun resolveOwnedProjectFile(context: Context, uri: Uri): File? =
        ProjectStorageManager.resolveAppOwnedFile(context, uri)

    /**
     * Makes any URI safe for project persistence. SAF URIs with a retained read grant are kept as
     * they are; temporary grants and file:// sources are copied as a streaming operation.
     */
    suspend fun ensureDurable(context: Context, uri: Uri): Uri = withContext(Dispatchers.IO) {
        validateUri(uri)
        requireReadable(context, uri)
        when {
            isOwnedProjectUri(context, uri) -> uri
            hasPersistedReadPermission(context, uri) -> uri
            else -> copyIntoProjectStorage(context, uri)
        }
    }

    @Suppress("DEPRECATION")
    private fun extractUris(intent: Intent): List<Uri> = buildList {
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::add)
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
                intent.data?.let(::add)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
                intent.data?.let(::add)
            }
        }
        intent.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index).uri?.let(::add)
            }
        }
    }.distinctBy(Uri::toString)

    private fun validateUri(uri: Uri) {
        val scheme = uri.scheme?.lowercase()
        require(scheme == "content" || scheme == "file") {
            "Schéma URI non pris en charge : ${scheme ?: "absent"}"
        }
    }

    private fun requireReadable(context: Context, uri: Uri) {
        val readable = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.fileDescriptor.valid()
            } ?: false
        }.getOrDefault(false)
        require(readable) { "Android n’accorde pas l’accès en lecture à ce média" }
    }

    private fun isOwnedProjectUri(context: Context, uri: Uri): Boolean =
        ProjectStorageManager.resolveAppOwnedFile(context, uri) != null

    private fun hasPersistedReadPermission(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }

    private fun tryPersistReadPermission(context: Context, intent: Intent, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) return false
        if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return false

        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onSuccess {
            Log.i(TAG, "URI access=persistable")
        }.onFailure {
            Log.i(TAG, "URI access=temporary; project copy required")
        }.isSuccess
    }

    private fun copyIntoProjectStorage(context: Context, uri: Uri): Uri {
        val metadata = queryMetadata(context, uri)
        val required = ProjectStorageManager.requiredImportBytes(metadata.sizeBytes)
        val storage = ProjectStorageManager.chooseRoot(context, required)
        val targetDirectory = File(storage.directory, "incoming").apply {
            require(exists() || mkdirs()) { "Impossible de préparer le stockage temporaire du projet" }
        }

        val safeName = sanitizeFilename(metadata.displayName ?: "media")
        val target = File(targetDirectory, "${UUID.randomUUID()}-$safeName")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Impossible d’ouvrir le média reçu")
            input.use { source ->
                target.outputStream().buffered().use { destination ->
                    source.copyTo(destination, 256 * 1024)
                }
            }
            require(target.length() > 0L) { "Le média reçu est vide" }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }

        Log.i(
            TAG,
            "Import clone ready storage=${storage.label} removable=${storage.removable} bytes=${target.length()} path=${target.absolutePath}",
        )
        return ProjectStorageManager.uriForPrivateFile(target)
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
        if (sizeBytes == null || sizeBytes == 0L) {
            sizeBytes = when {
                uri.scheme.equals("file", ignoreCase = true) -> uri.path?.let(::File)?.takeIf(File::isFile)?.length()
                else -> runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                        descriptor.statSize.takeIf { it > 0L }
                    }
                }.getOrNull()
            }
        }
        return SourceMetadata(displayName, sizeBytes)
    }

    private fun sanitizeFilename(value: String): String {
        val sanitized = value
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.')
            .take(96)
        return sanitized.ifBlank { "media.bin" }
    }

    private data class SourceMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
    )
}
