package com.fabvidedit.app.model

/** Rebuild playback only when a media input or its timeline position actually changes.
 * User zoom/keyframes/brightness must not seek the video, restart the clock or reprepare audio.
 * Opacity IS part of this key, because it can change which visual lane is visible.
 */
internal data class PreviewMediaIdentity(
    val videos: List<Video>,
    val audio: List<Audio>,
    val musicUri: String?,
    val musicDurationMs: Long?,
    val durationMs: Long,
) {
    data class Video(
        val id: String, val uri: String, val kind: VisualMediaKind,
        val durationMs: Long, val trimStartMs: Long, val trimEndMs: Long,
        val speed: Float, val startMs: Long, val lane: Int, val opacity: Float,
    )
    data class Audio(
        val id: String, val uri: String, val durationMs: Long,
        val trimStartMs: Long, val trimEndMs: Long, val startMs: Long,
    )
}

internal fun VideoProject.previewMediaIdentity(): PreviewMediaIdentity =
    PreviewMediaIdentity(
        videos = clips.map {
            PreviewMediaIdentity.Video(
                it.id, it.uri, it.mediaKind, it.durationMs, it.trimStartMs,
                it.trimEndMs, it.speed, it.timelineStartMs, it.timelineTrackIndex,
                it.opacity,
            )
        },
        audio = sourceAudioTracks.map {
            PreviewMediaIdentity.Audio(
                it.id, it.uri, it.durationMs, it.trimStartMs, it.trimEndMs,
                it.timelineStartMs,
            )
        },
        musicUri = audioTrack?.uri,
        musicDurationMs = audioTrack?.durationMs,
        durationMs = durationMs,
    )
