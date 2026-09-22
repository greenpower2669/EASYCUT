package com.fabvidedit.app.model

import kotlin.math.abs
import kotlin.math.roundToInt

enum class VideoOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Paysage"),
    SQUARE("Carré"),
    UNKNOWN("Inconnue"),
}

data class VideoFrameFormat(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val codecMime: String? = null,
    val frameRate: Float? = null,
    val pixelAspectRatio: Float = 1f,
) {
    val displayAspectRatio: Float
        get() = if (height > 0) width.toFloat() * pixelAspectRatio / height.toFloat() else 0f

    val orientation: VideoOrientation
        get() = when {
            width <= 0 || height <= 0 -> VideoOrientation.UNKNOWN
            abs(displayAspectRatio - 1f) < 0.04f -> VideoOrientation.SQUARE
            displayAspectRatio < 1f -> VideoOrientation.PORTRAIT
            else -> VideoOrientation.LANDSCAPE
        }

    val ratioLabel: String
        get() {
            val ratio = displayAspectRatio
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
                ?: "${(ratio * 100).roundToInt() / 100f}:1"
        }

    val key: String
        get() = "${width}x${height}"
}

data class VideoFormatSample(
    val positionPercent: Float,
    val timeMs: Long,
    val format: VideoFrameFormat,
)

data class VideoFormatCount(
    val format: VideoFrameFormat,
    val samples: Int,
    val share: Float,
)

data class FormatRupture(
    val timeMs: Long,
    val from: VideoFrameFormat,
    val to: VideoFrameFormat,
)

data class VideoFormatAnalysis(
    val durationMs: Long,
    val sourceFormat: VideoFrameFormat,
    val samples: List<VideoFormatSample>,
    val majority: VideoFormatCount,
    val formats: List<VideoFormatCount>,
    val ruptures: List<FormatRupture>,
    val possibleIntroOrAd: Boolean,
) {
    val secondaryFormats: List<VideoFormatCount>
        get() = formats.filterNot { it.format.key == majority.format.key }
}

enum class PocVideoCodec(val label: String) {
    HEVC("H.265 / HEVC"),
    AVC("H.264 / AVC"),
}

enum class PocFrameRate(val label: String, val fps: Int?) {
    ORIGINAL("Original", null),
    FPS_24("24 fps", 24),
    FPS_25("25 fps", 25),
    FPS_30("30 fps", 30),
    FPS_50("50 fps", 50),
    FPS_60("60 fps", 60),
}

enum class PocCompression(val label: String, val hevcBitsPerPixel: Float) {
    HIGH("Qualité élevée", 0.14f),
    BALANCED("Équilibré", 0.09f),
    STRONG("Compression forte", 0.055f),
    CUSTOM("Personnalisé", 0.055f),
}

enum class PocRatioMode(val label: String) {
    PRESERVE("Conserver le ratio"),
    CROP("Recadrer / Crop"),
    FIT("Adapter avec bandes / Fit"),
    FILL("Remplir"),
    ORIGINAL("Dimensions originales"),
}

enum class PocOutputPreset(val label: String) {
    MAJORITY("Format principal détecté"),
    PORTRAIT_1080("Portrait 1080×1920"),
    PORTRAIT_720("Portrait 720×1280"),
    LANDSCAPE_1080("Paysage 1920×1080"),
    LANDSCAPE_720("Paysage 1280×720"),
    SQUARE("Carré 1080×1080"),
    CUSTOM("Personnalisé"),
}

data class PocExportSettings(
    val codec: PocVideoCodec = PocVideoCodec.HEVC,
    val frameRate: PocFrameRate = PocFrameRate.FPS_24,
    val compression: PocCompression = PocCompression.STRONG,
    val ratioMode: PocRatioMode = PocRatioMode.PRESERVE,
    val outputPreset: PocOutputPreset = PocOutputPreset.MAJORITY,
    val audioBitrateKbps: Int? = 128,
    val customWidth: Int = 1080,
    val customHeight: Int = 1920,
    val customVideoBitrateMbps: Float = 4f,
) {
    fun outputSize(analysis: VideoFormatAnalysis?, fallbackWidth: Int, fallbackHeight: Int): Pair<Int, Int> {
        val majority = analysis?.majority?.format
        return when (outputPreset) {
            PocOutputPreset.MAJORITY -> {
                val width = majority?.width?.takeIf { it > 0 } ?: fallbackWidth.coerceAtLeast(2)
                val height = majority?.height?.takeIf { it > 0 } ?: fallbackHeight.coerceAtLeast(2)
                even(width) to even(height)
            }
            PocOutputPreset.PORTRAIT_1080 -> 1080 to 1920
            PocOutputPreset.PORTRAIT_720 -> 720 to 1280
            PocOutputPreset.LANDSCAPE_1080 -> 1920 to 1080
            PocOutputPreset.LANDSCAPE_720 -> 1280 to 720
            PocOutputPreset.SQUARE -> 1080 to 1080
            PocOutputPreset.CUSTOM -> even(customWidth.coerceAtLeast(2)) to even(customHeight.coerceAtLeast(2))
        }
    }

    fun requestedVideoBitrate(width: Int, height: Int): Int {
        if (compression == PocCompression.CUSTOM) {
            return (customVideoBitrateMbps.coerceIn(0.25f, 80f) * 1_000_000f).roundToInt()
        }
        val fps = frameRate.fps ?: 30
        val codecMultiplier = if (codec == PocVideoCodec.HEVC) 1f else 1.55f
        return (width.toLong() * height.toLong() * fps * compression.hevcBitsPerPixel * codecMultiplier)
            .roundToInt()
            .coerceIn(350_000, 60_000_000)
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1
}
