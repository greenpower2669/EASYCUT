package com.fabvidedit.app.model

import kotlin.math.abs

enum class MediaStreamKind {
    VIDEO,
    AUDIO,
    SUBTITLE,
    DATA,
    OTHER,
}

enum class StreamSelectionConfidence(val label: String) {
    HIGH("Élevée"),
    MEDIUM("Moyenne"),
    LOW("Faible"),
}

data class MediaStreamInfo(
    val index: Int,
    val kind: MediaStreamKind,
    val codecName: String? = null,
    val codecLongName: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sampleAspectRatio: Float = 1f,
    val displayAspectRatio: Float = 0f,
    val frameRate: Float? = null,
    val durationMs: Long? = null,
    val startTimeMs: Long = 0L,
    val frameCount: Long? = null,
    val bitRate: Long? = null,
    val isDefault: Boolean = false,
    val isAttachedPicture: Boolean = false,
    val rotationDegrees: Int = 0,
    val tags: Map<String, String> = emptyMap(),
) {
    val effectiveAspectRatio: Float
        get() = when {
            displayAspectRatio > 0f -> displayAspectRatio
            width > 0 && height > 0 -> width.toFloat() * sampleAspectRatio / height.toFloat()
            else -> 0f
        }

    val ratioLabel: String
        get() {
            val ratio = effectiveAspectRatio
            if (ratio <= 0f) return "?"
            val presets = listOf(
                9f / 16f to "9:16",
                16f / 9f to "16:9",
                1f to "1:1",
                4f / 3f to "4:3",
                3f / 4f to "3:4",
            )
            return presets.minByOrNull { abs(it.first - ratio) }
                ?.takeIf { abs(it.first - ratio) < 0.035f }
                ?.second
                ?: "%.2f:1".format(ratio)
        }
}

data class VideoStreamScore(
    val streamIndex: Int,
    val score: Float,
    val durationCoverage: Float,
)

data class StreamSelectionRecommendation(
    val selectedVideoIndex: Int?,
    val selectedAudioIndex: Int?,
    val confidence: StreamSelectionConfidence,
    val simultaneousVideoConflict: Boolean,
    val automaticallyExcludedVideoIndexes: Set<Int>,
    val scores: List<VideoStreamScore>,
    val reason: String,
) {
    val requiresManualChoice: Boolean
        get() = confidence == StreamSelectionConfidence.LOW
}

data class MediaStreamInventory(
    val containerName: String? = null,
    val containerLongName: String? = null,
    val durationMs: Long = 0L,
    val streams: List<MediaStreamInfo>,
    val recommendation: StreamSelectionRecommendation,
) {
    val videoStreams: List<MediaStreamInfo>
        get() = streams.filter { it.kind == MediaStreamKind.VIDEO && !it.isAttachedPicture }

    val audioStreams: List<MediaStreamInfo>
        get() = streams.filter { it.kind == MediaStreamKind.AUDIO }

    val subtitleStreams: List<MediaStreamInfo>
        get() = streams.filter { it.kind == MediaStreamKind.SUBTITLE }

    val dataStreams: List<MediaStreamInfo>
        get() = streams.filter { it.kind == MediaStreamKind.DATA }

    val otherStreams: List<MediaStreamInfo>
        get() = streams.filter { it.kind == MediaStreamKind.OTHER || it.isAttachedPicture }
}

data class PocStreamExportPlan(
    val inventory: MediaStreamInventory,
    val selectedVideoIndex: Int,
    val keptVideoIndexes: Set<Int>,
    val selectedAudioIndex: Int? = null,
    val keepSubtitles: Boolean = false,
) {
    val isMultiVideoOutput: Boolean
        get() = keptVideoIndexes.size > 1
}
