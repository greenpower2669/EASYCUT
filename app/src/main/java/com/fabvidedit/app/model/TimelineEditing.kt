package com.fabvidedit.app.model

import kotlin.math.abs

private const val MOVE_SNAP_MS = 180L
private const val MAX_VIDEO_TRACK_INDEX = 63

fun VideoProject.rippleDeleteClip(clipId: String): VideoProject {
    val removed = clips.firstOrNull { it.id == clipId } ?: return this
    val removedStart = removed.timelineStartMs
    val removedEnd = removedStart + removed.outputDurationMs
    val removedDuration = removed.outputDurationMs
    val shifted = clips.filterNot { it.id == clipId }.map { clip ->
        if (timelineMode == TimelineMode.MULTITRACK && clip.timelineTrackIndex == removed.timelineTrackIndex && clip.timelineStartMs >= removedEnd - 2L) {
            clip.copy(timelineStartMs = (clip.timelineStartMs - removedDuration).coerceAtLeast(removedStart))
        } else clip
    }
    // v0.11: never compact V1/V2/V3 after a deletion. Empty lanes are real editor locations.
    return copy(clips = shifted, projectFormatVersion = 9).withValidTransitions()
}

fun VideoProject.moveClipOnTimeline(clipId: String, targetTrackIndex: Int, targetStartMs: Long): VideoProject {
    val moving = clips.firstOrNull { it.id == clipId } ?: return this
    // v0.11: the UI may expose empty V1..V6 (and later more). Do not clamp to the currently-used
    // tracks, otherwise dropping from V1 to an empty V5/V6 silently falls back to V2/V3.
    val targetTrack = targetTrackIndex.coerceIn(0, MAX_VIDEO_TRACK_INDEX)
    val duration = moving.outputDurationMs.coerceAtLeast(1L)
    val occupied = clips.filter { it.id != clipId && it.timelineTrackIndex == targetTrack }.sortedBy(VideoClip::timelineStartMs)
    var start = targetStartMs.coerceAtLeast(0L)
    val boundaries = occupied.flatMap { listOf(it.timelineStartMs, it.timelineStartMs + it.outputDurationMs) } + 0L
    boundaries.minByOrNull { abs(it - start) }?.takeIf { abs(it - start) <= MOVE_SNAP_MS }?.let { start = it }
    var guard = 0
    while (guard++ < occupied.size + 2) {
        val end = start + duration
        val overlap = occupied.firstOrNull { other ->
            val otherEnd = other.timelineStartMs + other.outputDurationMs
            start < otherEnd && end > other.timelineStartMs
        } ?: break
        val before = overlap.timelineStartMs - duration
        val after = overlap.timelineStartMs + overlap.outputDurationMs
        start = if (before >= 0L && abs(start - before) < abs(start - after)) before else after
    }
    val moved = clips.map { clip ->
        if (clip.id == clipId) clip.copy(timelineTrackIndex = targetTrack, timelineStartMs = start) else clip
    }
    // v0.11: preserve the requested target lane exactly; never renumber tracks behind the user.
    return copy(clips = moved, timelineMode = TimelineMode.MULTITRACK, projectFormatVersion = 9).withValidTransitions()
}

/** Main track is V1 (index zero). Appending moves the EXISTING clip; no copy or ripple. */
fun VideoProject.endOfVideoTrack(trackIndex: Int, excludedClipId: String? = null): Long =
    clips.asSequence()
        .filter { it.timelineTrackIndex == trackIndex && it.id != excludedClipId }
        .map { it.timelineStartMs + it.outputDurationMs }
        .maxOrNull() ?: 0L

fun VideoProject.appendClipToVideoTrack(clipId: String, trackIndex: Int): VideoProject {
    if (clips.none { it.id == clipId }) return this
    return moveClipOnTimeline(clipId, trackIndex, endOfVideoTrack(trackIndex, clipId))
}
