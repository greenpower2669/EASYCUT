package com.fabvidedit.app.media

import kotlin.math.roundToInt

/** Export-only decisions: profiles are targets, not encoder or file-size guarantees. */
enum class ExportQualityProfile(val label: String) {
    BALANCED("Équilibré"),
    MINIMAL("Fichier minimal"),
    HIGH("Haute qualité"),
    CUSTOM("Personnalisé"),
}

enum class ExportAudioMode(val label: String) {
    KEEP("Conserver le son"),
    MUTE("Sans son"),
}

enum class ExportAudioChannels(val label: String, val ffmpegCount: Int?) {
    KEEP("Canaux d’origine", null),
    MONO("Mono (choisi)", 1),
    STEREO("Stéréo (choisie)", 2),
}

enum class ExportAacBitrate(val label: String, val bitsPerSecond: Int?) {
    AUTO("Auto / original", null),
    K64("64 kbit/s", 64_000),
    K96("96 kbit/s", 96_000),
    K128("128 kbit/s", 128_000),
    K192("192 kbit/s", 192_000),
    K256("256 kbit/s", 256_000),
}

internal object ExportEconomy {
    fun videoBitrate(
        profile: ExportQualityProfile,
        manual: Int?,
        shortSide: Int?,
        fps: Int?,
        hevc: Boolean,
    ): Int? {
        if (manual != null) return manual
        if (profile == ExportQualityProfile.CUSTOM) return null
        val side = (shortSide ?: 1080).coerceIn(240, 2160)
        val base = when {
            side <= 240 -> 650_000
            side <= 360 -> 950_000
            side <= 480 -> 1_450_000
            side <= 540 -> 1_850_000
            side <= 720 -> 3_000_000
            side <= 1080 -> 5_000_000
            side <= 1440 -> 9_000_000
            else -> 17_000_000
        }
        val quality = when (profile) {
            ExportQualityProfile.MINIMAL -> 0.55
            ExportQualityProfile.BALANCED -> 1.0
            ExportQualityProfile.HIGH -> 1.65
            ExportQualityProfile.CUSTOM -> 1.0
        }
        val cadence = ((fps ?: 30) / 30.0).coerceIn(0.45, 1.5)
        val codec = if (hevc) 0.75 else 1.0
        return (base * quality * cadence * codec).roundToInt().coerceIn(250_000, 50_000_000)
    }

    /** MiB decimal, approximate only: actual sources, codec, audio & muxing may differ. */
    fun estimatedMegabytes(durationMs: Long, videoBps: Int?, audioBps: Int): Double? {
        if (durationMs <= 0L || videoBps == null) return null
        return ((videoBps.toLong() + audioBps) * (durationMs / 1_000.0) / 8_000_000.0)
    }

    fun audioBitrate(mode: ExportAudioMode, bitrate: ExportAacBitrate): Int =
        if (mode == ExportAudioMode.MUTE) 0 else bitrate.bitsPerSecond ?: 128_000

    fun formatEstimate(estimatedMb: Double?): String =
        estimatedMb?.let { "≈ %.1f Mo (estimation, non garantie)".format(java.util.Locale.FRANCE, it) }
            ?: "Estimation indisponible : débit automatique"
}
