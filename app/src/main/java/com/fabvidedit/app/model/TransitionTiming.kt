package com.fabvidedit.app.model

/**
 * One timeline-to-source transition clock for both fallback preview and Media3 export.
 * Keeps intentional animations identical for short clips and changed playback speeds.
 */
internal object TransitionTiming {
    fun progress(
        clip: VideoClip,
        sourceTimeMs: Long,
        transition: ClipTransition?,
        isIncoming: Boolean,
    ): Float? {
        transition ?: return null
        val requestedSourceMs = ((transition.durationMs / 2f) * clip.speed).toLong()
        val durationMs = requestedSourceMs
            .coerceAtMost((clip.sourceDurationMs / 3).coerceAtLeast(1L))
            .coerceAtLeast(1L)
        val raw = if (isIncoming) {
            if (sourceTimeMs > durationMs) return null
            sourceTimeMs.toFloat() / durationMs
        } else {
            val start = clip.sourceDurationMs - durationMs
            if (sourceTimeMs < start) return null
            (sourceTimeMs - start).toFloat() / durationMs
        }
        val t = raw.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
