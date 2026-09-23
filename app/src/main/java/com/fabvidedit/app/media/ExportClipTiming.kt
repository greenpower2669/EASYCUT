package com.fabvidedit.app.media

/** Media3's item callback can contain the project timestamp after a preceding lane gap.
 * Keyframes use trimmed-source-local time. Sequential preview keeps origin zero.
 */
internal object ExportClipTiming {
    fun sourceLocalMs(
        presentationTimeUs: Long, timelineStartMs: Long,
        speed: Float, sourceDurationMs: Long,
    ): Long {
        val outputLocalUs = (presentationTimeUs - timelineStartMs.coerceAtLeast(0L) * 1_000L)
            .coerceAtLeast(0L)
        return ((outputLocalUs / 1_000.0) * speed.coerceIn(0.25f, 4f))
            .toLong().coerceIn(0L, sourceDurationMs)
    }
}
