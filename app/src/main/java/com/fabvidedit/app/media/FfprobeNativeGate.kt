package com.fabvidedit.app.media

/**
 * FFprobe's native writer / FFmpegKit callbacks are shared within the process.
 * Serialize synchronous inventory/probe and import/thumbnail remux calls that
 * share FFmpegKit process state. Do not lock Media3 playback or long exports.
 * No nested calls or coroutine suspension.
 */
internal object FfprobeNativeGate {
    private val lock = Any()

    fun <T> run(block: () -> T): T = synchronized(lock) { block() }
}
