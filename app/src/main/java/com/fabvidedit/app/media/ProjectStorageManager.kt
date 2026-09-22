package com.fabvidedit.app.media

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.fabvidedit.app.model.VideoProject
import java.io.File
import java.util.Locale

/**
 * Centralized storage policy for large FabVidEdit media.
 *
 * - Prefer a mounted removable app-specific volume when it has enough space.
 * - Otherwise choose the app-specific volume with the most free space.
 * - Keep legacy internal FileProvider URIs readable.
 * - Reclaim unreferenced project_sources files left by failed imports.
 */
object ProjectStorageManager {
    private const val TAG = "FabVidStorage"
    const val IMPORT_SAFETY_MARGIN_BYTES = 512L * 1024L * 1024L
    private const val STARTUP_ORPHAN_MIN_AGE_MS = 5L * 60L * 1000L

    data class StorageRoot(
        val directory: File,
        val removable: Boolean,
        val label: String,
    )

    data class CleanupReport(
        val reclaimedBytes: Long,
        val deletedFiles: Int,
    )

    fun chooseRoot(context: Context, requiredBytes: Long): StorageRoot {
        val roots = availableRoots(context)
        require(roots.isNotEmpty()) { "Aucun stockage FabVidEdit disponible" }

        val eligible = roots.filter { root ->
            ensureDirectory(root.directory)
            root.directory.usableSpace > requiredBytes
        }
        if (eligible.isEmpty()) {
            val best = roots.maxByOrNull { it.directory.usableSpace }
            val free = best?.directory?.usableSpace ?: 0L
            throw IllegalStateException(
                "Espace insuffisant : ${formatBytes(requiredBytes)} nécessaires, ${formatBytes(free)} disponibles au mieux",
            )
        }

        val chosen = eligible
            .filter(StorageRoot::removable)
            .maxByOrNull { it.directory.usableSpace }
            ?: eligible.maxByOrNull { it.directory.usableSpace }
            ?: eligible.first()

        Log.i(
            TAG,
            "Storage selected=${chosen.label} removable=${chosen.removable} free=${formatBytes(chosen.directory.usableSpace)} path=${chosen.directory.absolutePath}",
        )
        return chosen
    }

    fun availableRoots(context: Context): List<StorageRoot> {
        val result = mutableListOf<StorageRoot>()
        result += StorageRoot(
            directory = File(context.filesDir, "project_sources"),
            removable = false,
            label = "interne privé",
        )

        context.getExternalFilesDirs(null)
            .filterNotNull()
            .forEachIndexed { index, base ->
                val mounted = runCatching { Environment.getExternalStorageState(base) == Environment.MEDIA_MOUNTED }
                    .getOrDefault(false)
                if (!mounted) return@forEachIndexed
                val removable = runCatching { Environment.isExternalStorageRemovable(base) }.getOrDefault(false)
                result += StorageRoot(
                    directory = File(base, "project_sources"),
                    removable = removable,
                    label = if (removable) "carte SD" else if (index == 0) "stockage externe principal" else "stockage externe ${index + 1}",
                )
            }

        return result.distinctBy { runCatching { it.directory.canonicalPath }.getOrDefault(it.directory.absolutePath) }
    }

    fun rootContaining(context: Context, file: File): StorageRoot? {
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return null
        return availableRoots(context).firstOrNull { root ->
            val canonicalRoot = runCatching { root.directory.canonicalFile }.getOrNull() ?: return@firstOrNull false
            candidate.path == canonicalRoot.path || candidate.path.startsWith(canonicalRoot.path + File.separator)
        }
    }

    fun resolveAppOwnedFile(context: Context, uri: Uri): File? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return null
            val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
            return file.takeIf { it.isFile && rootContaining(context, it) != null }
        }

        if (uri.scheme != "content" || uri.authority != "${context.packageName}.fileprovider") return null
        val segments = uri.pathSegments
        if (segments.size < 2) return null

        val root = when (segments.first()) {
            "project_sources" -> File(context.filesDir, "project_sources")
            "external_project_sources" -> context.getExternalFilesDir(null)?.let { File(it, "project_sources") }
            else -> null
        } ?: return null

        val candidate = runCatching {
            File(root, segments.drop(1).joinToString(File.separator)).canonicalFile
        }.getOrNull() ?: return null
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val prefix = canonicalRoot.path + File.separator
        return candidate.takeIf {
            (it.path == canonicalRoot.path || it.path.startsWith(prefix)) && it.isFile && it.canRead()
        }
    }

    fun uriForPrivateFile(file: File): Uri = Uri.fromFile(file)

    fun cleanupOrphans(
        context: Context,
        projects: List<VideoProject>,
        minAgeMs: Long = 0L,
    ): CleanupReport {
        val referenced = buildSet {
            projects.forEach { project ->
                project.clips.forEach { clip ->
                    addUriPath(context, clip.uri)
                    clip.containerUri?.let { addUriPath(context, it) }
                }
                project.sourceAudioTracks.forEach { track ->
                    addUriPath(context, track.uri)
                    track.containerUri?.let { addUriPath(context, it) }
                }
                project.audioTrack?.let { addUriPath(context, it.uri) }
            }
        }

        var reclaimed = 0L
        var deleted = 0
        val now = System.currentTimeMillis()
        availableRoots(context).forEach { root ->
            if (!root.directory.exists()) return@forEach
            root.directory.walkBottomUp().forEach { file ->
                if (file == root.directory) return@forEach
                if (file.isDirectory) {
                    if (file.list()?.isEmpty() == true) file.delete()
                    return@forEach
                }
                val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                if (canonical in referenced) return@forEach
                if (minAgeMs > 0L && now - file.lastModified() < minAgeMs) return@forEach
                val size = file.length()
                if (file.delete()) {
                    reclaimed += size
                    deleted++
                }
            }
        }

        // FFmpeg cache inputs are always temporary and never project references.
        File(context.cacheDir, "ffmpeg_inputs").takeIf(File::exists)?.deleteRecursively()

        if (deleted > 0) {
            Log.i(TAG, "Storage cleanup deleted=$deleted reclaimed=${formatBytes(reclaimed)}")
        }
        return CleanupReport(reclaimed, deleted)
    }

    fun cleanupStartupOrphans(context: Context, projects: List<VideoProject>): CleanupReport =
        cleanupOrphans(context, projects, STARTUP_ORPHAN_MIN_AGE_MS)

    fun requiredImportBytes(sourceBytes: Long?): Long {
        val source = sourceBytes?.takeIf { it > 0L }
        return if (source != null) {
            // One clone + roughly one full set of extracted streams, plus headroom.
            source * 2L + IMPORT_SAFETY_MARGIN_BYTES
        } else {
            2L * 1024L * 1024L * 1024L
        }
    }

    fun formatBytes(bytes: Long): String {
        val value = bytes.coerceAtLeast(0L).toDouble()
        val gib = 1024.0 * 1024.0 * 1024.0
        val mib = 1024.0 * 1024.0
        return when {
            value >= gib -> String.format(Locale.FRANCE, "%.1f Go", value / gib)
            value >= mib -> String.format(Locale.FRANCE, "%.0f Mo", value / mib)
            else -> String.format(Locale.FRANCE, "%.0f Ko", value / 1024.0)
        }
    }

    private fun MutableSet<String>.addUriPath(context: Context, raw: String) {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return
        val file = resolveAppOwnedFile(context, uri) ?: return
        add(runCatching { file.canonicalPath }.getOrDefault(file.absolutePath))
    }

    private fun ensureDirectory(directory: File) {
        require(directory.exists() || directory.mkdirs()) {
            "Impossible de préparer ${directory.absolutePath}"
        }
    }
}
