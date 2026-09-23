package com.fabvidedit.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local, bounded error journal for silent process deaths. No analytics, internet access,
 * media URI, original video bytes or automatic sharing. The user chooses whether to share it.
 */
object FabVidDiagnostics {
    private const val TAG = "FabVidError"
    private const val LIMIT_BYTES = 192 * 1024L
    private const val RETAIN_BYTES = 96 * 1024
    private val installed = AtomicBoolean(false)
    @Volatile private var currentStage = "démarrage"
    @Volatile private var lastError = "Aucune erreur capturée"
    private lateinit var journal: File

    fun install(context: Context) {
        val app = context.applicationContext
        journal = File(app.filesDir, "fabvid-errors.log")
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val handler = Thread.UncaughtExceptionHandler { thread, error ->
            try {
                logError("UNCAUGHT thread=" + thread.name.take(40), error)
            } catch (_: Throwable) {
                // Never replace Android's normal crash processing with another crash.
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else Process.killProcess(Process.myPid())
            }
        }
        Thread.setDefaultUncaughtExceptionHandler(handler)
        mark("START version=" + BuildConfig.VERSION_NAME)
    }

    fun mark(stage: String) {
        currentStage = stage.take(120)
        append("STAGE " + currentStage)
    }

    fun logError(stage: String, error: Throwable) {
        val kind = error.javaClass.name
        val message = error.message.orEmpty().replace('\n', ' ').take(400)
        lastError = "$stage | $kind | $message"
        Log.e(TAG, "stage=" + currentStage + " / " + lastError, error)
        // Stack trace is kept only in the private journal; the report is user-shared manually.
        val trace = runCatching { error.stackTraceToString().take(10_000) }.getOrDefault(kind)
        append("ERROR stage=" + currentStage + " context=" + stage.take(100) +
            " type=" + kind + " message=" + message + "\n" + trace)
    }

    /** Low-rate, device-local performance counters; do not change the fatal error stage. */
    fun metric(message: String) {
        append("PERF " + message.take(260))
    }

    fun getLastStage(): String = currentStage
    fun getLastError(): String = lastError

    fun getLastExitReason(context: Context): String =
        context.getSharedPreferences("fabvid_diagnostics", Context.MODE_PRIVATE)
            .getString("exit_reason", "Aucun arrêt Android identifié") ?: "Aucun arrêt Android identifié"

    /** Android 11+: distinguish Java crashes, native FFmpeg crashes and low-memory kills. */
    fun recordPreviousExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val prefs = context.getSharedPreferences("fabvid_diagnostics", Context.MODE_PRIVATE)
            val seen = prefs.getLong("exit_timestamp", 0L)
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            val newest = exits.maxByOrNull(ApplicationExitInfo::getTimestamp) ?: return@runCatching
            if (newest.timestamp <= seen) return@runCatching
            val reason = when (newest.reason) {
                ApplicationExitInfo.REASON_CRASH -> "Crash Java/Kotlin"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Crash natif (codec/FFmpeg possible)"
                ApplicationExitInfo.REASON_ANR -> "Application non réactive"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "Arrêt par manque de mémoire"
                ApplicationExitInfo.REASON_SIGNALED -> "Processus arrêté par signal"
                else -> "Arrêt Android code " + newest.reason
            }
            val details = "date=" + newest.timestamp +
                " reason=" + reason +
                " importance=" + newest.importance +
                " pssKB=" + newest.pss +
                " rssKB=" + newest.rss
            prefs.edit().putLong("exit_timestamp", newest.timestamp)
                .putString("exit_reason", details).commit()
            append("PREVIOUS_EXIT " + details + " stage_after_restart=" + currentStage)
            if (newest.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                newest.reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
                newest.reason == ApplicationExitInfo.REASON_ANR
            ) Log.e(TAG, "Previous process exit: " + details)
        }.onFailure { Log.w(TAG, "Unable to read Android process exit reason", it) }
    }

    @Synchronized
    fun clearJournal(context: Context): Boolean {
        if (!::journal.isInitialized) return false
        return runCatching {
            val parent = journal.parentFile ?: return@runCatching false
            if (!parent.exists() && !parent.mkdirs()) return@runCatching false
            FileOutputStream(journal, false).use { it.flush() }
            context.getSharedPreferences("fabvid_diagnostics", Context.MODE_PRIVATE)
                .edit().putLong("journal_cleared_at", System.currentTimeMillis()).apply()
            true
        }.getOrElse {
            Log.w(TAG, "Unable to clear local journal", it)
            false
        }
    }

    @Synchronized
    fun getReport(context: Context): String {
        val lines = runCatching {
            if (::journal.isInitialized && journal.isFile) {
                journal.readText().takeLast(25_000)
            } else "Journal non encore initialisé"
        }.getOrDefault("Journal indisponible")
        val clearedAt = context.getSharedPreferences("fabvid_diagnostics", Context.MODE_PRIVATE)
            .getLong("journal_cleared_at", 0L)
        val clearNotice = if (clearedAt > 0L) {
            "Anciennes lignes du journal vidées sur demande (date ms=$clearedAt). " +
                "Le dernier arrêt et la dernière erreur restent conservés.\n"
        } else ""
        return "EASYCUT " + BuildConfig.VERSION_NAME + "\n" +
            clearNotice +
            "Motif du dernier arrêt Android : " + getLastExitReason(context) + "\n" +
            "Dernière étape : " + getLastStage() + "\n" +
            "Dernière erreur : " + getLastError() + "\n\n" + lines
    }

    @Synchronized
    private fun append(message: String) {
        if (!::journal.isInitialized) return
        runCatching {
            val line = ("[" + System.currentTimeMillis() + "] " +
                message.take(12_000) + "\n").toByteArray(Charsets.UTF_8)
            val parent = journal.parentFile ?: return@runCatching
            if (!parent.exists() && !parent.mkdirs()) return@runCatching
            if (journal.length() > LIMIT_BYTES) {
                // Keep only the tail; never read the whole source video or an unbounded log.
                val tail = ByteArray(RETAIN_BYTES)
                val size = java.io.RandomAccessFile(journal, "r").use { file ->
                    file.seek((file.length() - RETAIN_BYTES).coerceAtLeast(0))
                    file.read(tail)
                }
                FileOutputStream(journal, false).use { out ->
                    if (size > 0) out.write(tail, 0, size)
                }
            }
            FileOutputStream(journal, true).use { out -> out.write(line) }
        }.onFailure { Log.w(TAG, "Local diagnostic write failed", it) }
    }
}
