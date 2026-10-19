package com.fabvidedit.app.model

import kotlin.math.abs
import kotlin.math.roundToLong

/** Pure, non-destructive snap calculations for the touch timeline. */
object TimelineTouchMath {
    fun toleranceMs(widthPx: Float, durationMs: Long, radiusPx: Float): Long {
        if (widthPx <= 0f || !widthPx.isFinite() || durationMs <= 0L || radiusPx < 0f || !radiusPx.isFinite()) return 0L
        return (durationMs.toDouble() * radiusPx / widthPx).roundToLong().coerceAtLeast(1L)
    }
    fun snapStart(startMs: Long, durationMs: Long, markers: List<Long>, thresholdMs: Long): Long {
        val start = startMs.coerceAtLeast(0L)
        if (thresholdMs <= 0L) return start
        val nearest = markers.flatMap { listOf(it, it - durationMs) }
            .filter { it >= 0L }.minByOrNull { abs(it - start) } ?: return start
        return if (abs(nearest - start) <= thresholdMs) nearest else start
    }
    fun markers(project: VideoProject, playheadMs: Long): List<Long> = buildList {
        add(0L); add(playheadMs.coerceAtLeast(0L))
        project.clips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.outputDurationMs) }
        project.sourceAudioTracks.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.outputDurationMs) }
        project.textLayers.forEach { add(it.startMs); add(it.endMs) }
    }.distinct()
}
