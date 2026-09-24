package com.fabvidedit.app.model

import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

enum class ClipFilter(val label: String) {
    NONE("Original"),
    MONO("Mono"),
    INVERT("Négatif"),
    WARM("Chaud"),
    COOL("Froid"),
}

enum class AspectRatioPreset(val label: String, val ratio: Float?) {
    SOURCE("Source", null),
    PORTRAIT("9:16", 9f / 16f),
    LANDSCAPE("16:9", 16f / 9f),
    SQUARE("1:1", 1f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    PORTRAIT_3_4("3:4", 3f / 4f),
    CLASSIC("4:3", 4f / 3f),
    CINEMA("21:9", 21f / 9f),
}

enum class TextPosition(val label: String, val anchorY: Float) {
    TOP("Haut", 0.72f),
    CENTER("Centre", 0f),
    BOTTOM("Bas", -0.72f),
}

enum class TimelineMode {
    SEQUENTIAL,
    MULTITRACK,
}

enum class VisualMediaKind {
    VIDEO,
    IMAGE,
}

enum class MotionEasing(val label: String) {
    LINEAR("Linéaire"),
    EASE_IN("Accélération"),
    EASE_OUT("Décélération"),
    EASE_IN_OUT("Douce"),
    ;

    fun apply(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> t
            EASE_IN -> t * t
            EASE_OUT -> 1f - (1f - t).pow(2)
            EASE_IN_OUT -> t * t * (3f - 2f * t)
        }
    }
}

enum class TransitionType(val label: String) {
    FADE("Fondu"),
    SLIDE_LEFT("Glissé gauche"),
    SLIDE_RIGHT("Glissé droite"),
    ZOOM("Zoom"),
    ROTATE("Rotation"),
}

data class ClipTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val pivotX: Float = 0f,
    val pivotY: Float = 0f,
) {
    fun normalized(): ClipTransform = copy(
        scaleX = scaleX.coerceIn(0.25f, 4f),
        scaleY = scaleY.coerceIn(0.25f, 4f),
        positionX = positionX.coerceIn(-2f, 2f),
        positionY = positionY.coerceIn(-2f, 2f),
        rotationDegrees = rotationDegrees.coerceIn(-720f, 720f),
        pivotX = pivotX.coerceIn(-1f, 1f),
        pivotY = pivotY.coerceIn(-1f, 1f),
    )

    fun interpolateTo(target: ClipTransform, progress: Float): ClipTransform {
        val t = progress.coerceIn(0f, 1f)
        fun lerp(start: Float, end: Float): Float = start + (end - start) * t
        val rawRotationDelta = target.rotationDegrees - rotationDegrees
        val rotationDelta = ((rawRotationDelta % 360f) + 540f) % 360f - 180f
        return ClipTransform(
            scaleX = lerp(scaleX, target.scaleX),
            scaleY = lerp(scaleY, target.scaleY),
            positionX = lerp(positionX, target.positionX),
            positionY = lerp(positionY, target.positionY),
            rotationDegrees = rotationDegrees + rotationDelta * t,
            pivotX = lerp(pivotX, target.pivotX),
            pivotY = lerp(pivotY, target.pivotY),
        ).normalized()
    }
}

/** One orientation-aware ratio for both the preview viewport and Media3 export. */
fun VideoClip.displayAspectRatio(): Float? {
    if (width <= 0 || height <= 0) return null
    val sideways = abs(rotationDegrees) % 180 == 90
    val displayedWidth = if (sideways) height else width
    val displayedHeight = if (sideways) width else height
    val sample = sampleAspectRatio.takeIf { it.isFinite() && it in 0.1f..10f } ?: 1f
    val declared = displayAspectRatio.takeIf { it.isFinite() && it in 0.1f..10f }
    val unswapped = declared ?: width.toFloat() * sample / height.toFloat()
    return if (sideways) 1f / unswapped else unswapped
}

data class TransformKeyframe(
    val id: String = UUID.randomUUID().toString(),
    /** Time relative to the trimmed source clip, before playback speed is applied. */
    val timeMs: Long,
    val transform: ClipTransform,
    /** Visual and audio values captured by the same diamond. */
    val brightness: Float = 1f,
    val volume: Float = 1f,
    val easing: MotionEasing = MotionEasing.EASE_IN_OUT,
    /** Manual SAR / DAR tests. null means Auto from this clip's source metadata. */
    val sarOverride: Float? = null,
    val darOverride: Float? = null,
)

data class ClipTransition(
    val id: String = UUID.randomUUID().toString(),
    val fromClipId: String,
    val toClipId: String,
    val type: TransitionType = TransitionType.FADE,
    val durationMs: Long = 600,
)

data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    /** Stable identity of the media source. Duplicated clips may intentionally share it. */
    val sourceId: String = id,
    /** Original container URI, retained only as provenance/reconnection metadata. */
    val containerUri: String? = null,
    /** Original FFprobe stream index inside containerUri. */
    val sourceStreamIndex: Int? = null,
    val mediaKind: VisualMediaKind = VisualMediaKind.VIDEO,
    val syncGroupId: String? = null,
    val syncLocked: Boolean = false,
    /** Visual track/layer index. Imported independent streams start at time zero on distinct tracks. */
    val timelineTrackIndex: Int = 0,
    val timelineStartMs: Long = 0L,
    val uri: String,
    val name: String,
    val durationMs: Long,
    val width: Int = 0,
    val height: Int = 0,
    /** FFprobe SAR/DAR: metadata only; no correction is applied to the untransformed player. */
    val sampleAspectRatio: Float = 1f,
    val displayAspectRatio: Float = 0f,
    /** Set only when bounded sampled keyframes actually show SAR/DAR variation. */
    val aspectVaries: Boolean = false,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = durationMs,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val brightness: Float = 1f,
    /** Per-clip opacity for the video compositor, independent from audio gain. */
    val opacity: Float = 1f,
    val rotationDegrees: Int = 0,
    val filter: ClipFilter = ClipFilter.NONE,
    val transform: ClipTransform = ClipTransform(),
    val keyframes: List<TransformKeyframe> = emptyList(),
) {
    val sourceDurationMs: Long
        get() = max(1, trimEndMs - trimStartMs)

    val outputDurationMs: Long
        get() = max(1, (sourceDurationMs / speed.coerceAtLeast(0.1f)).toLong())

    /** Explicit overrides are held until the next diamond, not interpolated frame by frame. */
    fun aspectAtSourceTime(timeMs: Long): Pair<Float, Float> {
        val selected = keyframes.filter { it.timeMs <= timeMs }.maxByOrNull { it.timeMs }
        val sar = selected?.sarOverride ?: sampleAspectRatio
        val dar = selected?.darOverride ?: displayAspectRatio
        return sar to dar
    }

    fun transformAtSourceTime(timeMs: Long): ClipTransform {
        val ordered = keyframes.sortedBy(TransformKeyframe::timeMs)
        if (ordered.isEmpty()) return transform.normalized()
        val time = timeMs.coerceIn(0, sourceDurationMs)
        val rightIndex = ordered.indexOfFirst { it.timeMs >= time }
        if (rightIndex < 0) return ordered.last().transform.normalized()
        val right = ordered[rightIndex]
        val leftTime = if (rightIndex == 0) 0L else ordered[rightIndex - 1].timeMs
        val leftTransform = if (rightIndex == 0) transform else ordered[rightIndex - 1].transform
        if (right.timeMs <= leftTime || time >= right.timeMs) return right.transform.normalized()
        val rawProgress = (time - leftTime).toFloat() / (right.timeMs - leftTime).toFloat()
        return leftTransform.interpolateTo(right.transform, right.easing.apply(rawProgress))
    }

    fun brightnessAtSourceTime(timeMs: Long): Float = scalarAtSourceTime(
        timeMs = timeMs,
        baseValue = brightness,
        valueAt = TransformKeyframe::brightness,
    ).coerceIn(0f, 2f)

    fun volumeAtSourceTime(timeMs: Long): Float = scalarAtSourceTime(
        timeMs = timeMs,
        baseValue = volume,
        valueAt = TransformKeyframe::volume,
    ).coerceIn(0f, 1f)

    private fun scalarAtSourceTime(
        timeMs: Long,
        baseValue: Float,
        valueAt: (TransformKeyframe) -> Float,
    ): Float {
        val ordered = keyframes.sortedBy(TransformKeyframe::timeMs)
        if (ordered.isEmpty()) return baseValue
        val time = timeMs.coerceIn(0, sourceDurationMs)
        val rightIndex = ordered.indexOfFirst { it.timeMs >= time }
        if (rightIndex < 0) return valueAt(ordered.last())
        val right = ordered[rightIndex]
        val leftTime = if (rightIndex == 0) 0L else ordered[rightIndex - 1].timeMs
        val leftValue = if (rightIndex == 0) baseValue else valueAt(ordered[rightIndex - 1])
        if (right.timeMs <= leftTime || time >= right.timeMs) return valueAt(right)
        val progress = (time - leftTime).toFloat() / (right.timeMs - leftTime).toFloat()
        val eased = right.easing.apply(progress)
        return leftValue + (valueAt(right) - leftValue) * eased
    }
}

data class AudioKeyframe(
    val id: String = UUID.randomUUID().toString(),
    /** Time on the complete project timeline. */
    val timeMs: Long,
    val volume: Float,
    val easing: MotionEasing = MotionEasing.EASE_IN_OUT,
)

data class AudioTrack(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val volume: Float = 0.5f,
    val keyframes: List<AudioKeyframe> = emptyList(),
) {
    fun volumeAtProjectTime(timeMs: Long): Float {
        val ordered = keyframes.sortedBy(AudioKeyframe::timeMs)
        if (ordered.isEmpty()) return volume.coerceIn(0f, 1f)
        val time = timeMs.coerceAtLeast(0)
        val rightIndex = ordered.indexOfFirst { it.timeMs >= time }
        if (rightIndex < 0) return ordered.last().volume.coerceIn(0f, 1f)
        val right = ordered[rightIndex]
        val leftTime = if (rightIndex == 0) 0L else ordered[rightIndex - 1].timeMs
        val leftVolume = if (rightIndex == 0) volume else ordered[rightIndex - 1].volume
        if (right.timeMs <= leftTime || time >= right.timeMs) return right.volume.coerceIn(0f, 1f)
        val progress = (time - leftTime).toFloat() / (right.timeMs - leftTime).toFloat()
        val eased = right.easing.apply(progress)
        return (leftVolume + (right.volume - leftVolume) * eased).coerceIn(0f, 1f)
    }
}

data class SourceAudioKeyframe(
    val id: String = UUID.randomUUID().toString(),
    /** Absolute time inside the source audio file. */
    val timeMs: Long,
    val volume: Float,
    val easing: MotionEasing = MotionEasing.EASE_IN_OUT,
)

/** Audio stream automatically separated from an imported media container. */
data class SourceAudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val sourceId: String,
    val containerUri: String? = null,
    val sourceStreamIndex: Int? = null,
    val syncGroupId: String? = null,
    val syncLocked: Boolean = false,
    val timelineTrackIndex: Int,
    val timelineStartMs: Long = 0L,
    val uri: String,
    val name: String,
    /** Complete source duration. Trimming is non-destructive. */
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs,
    val volume: Float = 1f,
    val keyframes: List<SourceAudioKeyframe> = emptyList(),
) {
    val sourceDurationMs: Long
        get() = max(1L, trimEndMs - trimStartMs)
    val outputDurationMs: Long
        get() = sourceDurationMs

    fun volumeAtSourceTime(sourceTimeMs: Long): Float {
        val ordered = keyframes.sortedBy(SourceAudioKeyframe::timeMs)
        if (ordered.isEmpty()) return volume.coerceIn(0f, 1f)
        val time = sourceTimeMs.coerceIn(trimStartMs, trimEndMs)
        val rightIndex = ordered.indexOfFirst { it.timeMs >= time }
        if (rightIndex < 0) return ordered.last().volume.coerceIn(0f, 1f)
        val right = ordered[rightIndex]
        val leftTime = if (rightIndex == 0) trimStartMs else ordered[rightIndex - 1].timeMs
        val leftVolume = if (rightIndex == 0) volume else ordered[rightIndex - 1].volume
        if (right.timeMs <= leftTime || time >= right.timeMs) return right.volume.coerceIn(0f, 1f)
        val progress = (time - leftTime).toFloat() / (right.timeMs - leftTime).toFloat()
        val eased = right.easing.apply(progress)
        return (leftVolume + (right.volume - leftVolume) * eased).coerceIn(0f, 1f)
    }
}

data class TextLayer(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundArgb: Int = 0x66000000,
    val position: TextPosition = TextPosition.BOTTOM,
    val sizePx: Int = 72,
)

data class VideoProject(
    val id: String = UUID.randomUUID().toString(),
    val projectFormatVersion: Int = 9,
    val name: String,
    val clips: List<VideoClip>,
    val sourceAudioTracks: List<SourceAudioTrack> = emptyList(),
    val timelineMode: TimelineMode = TimelineMode.SEQUENTIAL,
    val audioTrack: AudioTrack? = null,
    val textLayers: List<TextLayer> = emptyList(),
    val transitions: List<ClipTransition> = emptyList(),
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.SOURCE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val durationMs: Long
        get() = if (timelineMode == TimelineMode.MULTITRACK) {
            max(
                clips.maxOfOrNull { it.timelineStartMs + it.outputDurationMs } ?: 0L,
                sourceAudioTracks.maxOfOrNull { it.timelineStartMs + it.outputDurationMs } ?: 0L,
            )
        } else {
            clips.sumOf(VideoClip::outputDurationMs)
        }

    fun clipStartMs(index: Int): Long {
        val clip = clips.getOrNull(index) ?: return 0L
        return if (timelineMode == TimelineMode.MULTITRACK) {
            clip.timelineStartMs
        } else {
            clips.take(index.coerceIn(0, clips.size)).sumOf(VideoClip::outputDurationMs)
        }
    }

    fun sourceTimeForProjectPosition(clipIndex: Int, projectPositionMs: Long): Long {
        val clip = clips.getOrNull(clipIndex) ?: return 0
        val localOutputMs = (projectPositionMs - clipStartMs(clipIndex))
            .coerceIn(0, clip.outputDurationMs)
        return (localOutputMs * clip.speed).toLong().coerceIn(0, clip.sourceDurationMs)
    }

    fun projectPositionForSourceTime(clipIndex: Int, sourceTimeMs: Long): Long {
        val clip = clips.getOrNull(clipIndex) ?: return 0
        return clipStartMs(clipIndex) +
            (sourceTimeMs.coerceIn(0, clip.sourceDurationMs) / clip.speed).toLong()
    }

    fun nextClipOnTrack(clipIndex: Int): VideoClip? {
        val from = clips.getOrNull(clipIndex) ?: return null
        return if (timelineMode == TimelineMode.SEQUENTIAL) {
            clips.getOrNull(clipIndex + 1)
        } else {
            clips.asSequence()
                .filter { it.timelineTrackIndex == from.timelineTrackIndex }
                .filter { it.id != from.id && it.timelineStartMs >= from.timelineStartMs }
                .sortedWith(compareBy<VideoClip> { it.timelineStartMs }.thenBy { it.id })
                .firstOrNull { it.timelineStartMs >= from.timelineStartMs + from.outputDurationMs - 2L }
        }
    }

    fun transitionAfter(clipIndex: Int): ClipTransition? {
        val from = clips.getOrNull(clipIndex) ?: return null
        val to = nextClipOnTrack(clipIndex) ?: return null
        return transitions.firstOrNull { it.fromClipId == from.id && it.toClipId == to.id }
    }

    fun withValidTransitions(): VideoProject {
        val validPairs = if (timelineMode == TimelineMode.SEQUENTIAL) {
            clips.zipWithNext().map { (from, to) -> from.id to to.id }.toSet()
        } else {
            clips.groupBy(VideoClip::timelineTrackIndex).values.flatMap { track ->
                track.sortedBy(VideoClip::timelineStartMs)
                    .zipWithNext()
                    .map { (from, to) -> from.id to to.id }
            }.toSet()
        }
        return copy(
            transitions = transitions
                .filter { it.fromClipId to it.toClipId in validPairs }
                .distinctBy { it.fromClipId to it.toClipId },
        )
    }
}

data class MediaAssetInfo(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val width: Int = 0,
    val height: Int = 0,
)
