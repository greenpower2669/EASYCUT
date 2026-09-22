package com.fabvidedit.app.media

/**
 * FFprobe's native writer / FFmpegKit callbacks are shared within the process.
 * Serialize ONLY synchronous probe calls; no nested calls or coroutine suspension.
 * Keep FFmpeg encode/decode out of this gate, so long exports remain independent.
 */
internal object FfprobeNativeGate {
    private val lock = Any()

    fun <T> run(block: () -> T): T = synchronized(lock) { block() }
}
