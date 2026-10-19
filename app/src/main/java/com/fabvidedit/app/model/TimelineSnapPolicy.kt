package com.fabvidedit.app.model

import kotlin.math.abs

/** Snap the beginning OR the end of a dragged clip to an actual timeline boundary. */
object TimelineSnapPolicy {
    fun snapStart(startMs: Long, durationMs: Long, boundariesMs: List<Long>,
                  playheadMs: Long?, toleranceMs: Long = 180L): Long {
        val safeStart = startMs.coerceAtLeast(0L)
        val candidates = buildList {
            add(0L)
            boundariesMs.forEach { edge ->
                add(edge)
                if (edge >= durationMs) add(edge - durationMs)
            }
            if (playheadMs != null) {
                add(playheadMs)
                if (playheadMs >= durationMs) add(playheadMs - durationMs)
            }
        }
        val nearest = candidates.filter { it >= 0L }.minByOrNull { abs(it - safeStart) } ?: return safeStart
        return if (abs(nearest - safeStart) <= toleranceMs.coerceAtLeast(0L)) nearest else safeStart
    }
}
