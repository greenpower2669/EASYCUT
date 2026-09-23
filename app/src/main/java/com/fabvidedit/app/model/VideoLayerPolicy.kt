package com.fabvidedit.app.model

/** Shared timeline/layer convention for preview, composition and editor controls.
 * V3 sits above V2; a gap must never become an opaque video frame.
 */
object VideoLayerPolicy {
    fun needsVideoCompositor(project: VideoProject): Boolean =
        project.timelineMode == TimelineMode.MULTITRACK &&
            project.clips.map(VideoClip::timelineTrackIndex).distinct().size > 1

    fun frontToBack(project: VideoProject): List<Int> =
        project.clips.map(VideoClip::timelineTrackIndex).distinct().sortedDescending()

    fun activeClip(clips: List<VideoClip>, timeMs: Long): VideoClip? =
        clips.asSequence()
            .filter { it.opacity > 0f && it.timelineStartMs <= timeMs &&
                timeMs < it.timelineStartMs + it.outputDurationMs }
            .maxWithOrNull(compareBy<VideoClip> { it.timelineTrackIndex }.thenBy { it.id })

    fun opacityAt(clips: List<VideoClip>, trackIndex: Int, timeMs: Long): Float =
        clips.firstOrNull {
            it.timelineTrackIndex == trackIndex &&
                it.timelineStartMs <= timeMs &&
                timeMs < it.timelineStartMs + it.outputDurationMs
        }?.opacity?.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f

    /** Exchange the WHOLE lanes so only visual depth changes; no clip time or A/V link moves. */
    fun moveLayer(project: VideoProject, selectedClipId: String, direction: Int): VideoProject {
        if (direction !in listOf(-1, 1)) return project
        val lane = project.clips.firstOrNull { it.id == selectedClipId }?.timelineTrackIndex
            ?: return project
        val ordered = frontToBack(project)
        val index = ordered.indexOf(lane)
        val other = ordered.getOrNull(index - direction) ?: return project
        return project.copy(clips = project.clips.map { clip ->
            when (clip.timelineTrackIndex) {
                lane -> clip.copy(timelineTrackIndex = other)
                other -> clip.copy(timelineTrackIndex = lane)
                else -> clip
            }
        }).withValidTransitions()
    }
}
