package com.fabvidedit.app.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.fabvidedit.app.model.FormatRupture
import com.fabvidedit.app.model.MediaStreamInfo
import com.fabvidedit.app.model.VideoFormatAnalysis
import com.fabvidedit.app.model.VideoFormatSample
import com.fabvidedit.app.model.VideoFrameFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PocVideoAnalyzer {
    private val samplePercentages = listOf(0.5f, 2f, 10f, 20f, 30f, 40f, 50f, 60f, 70f, 80f, 95f)

    suspend fun analyze(context: Context, uri: Uri): VideoFormatAnalysis = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: 1L
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            val containerWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: 0
            val containerHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: 0
            val trackInfo = readVideoTrackInfo(context, uri)
            val fallback = VideoFrameFormat(
                width = containerWidth,
                height = containerHeight,
                rotationDegrees = rotation,
                codecMime = trackInfo.codecMime,
                frameRate = trackInfo.frameRate,
                pixelAspectRatio = trackInfo.pixelAspectRatio,
            )

            val samples = samplePercentages.map { percent ->
                val timeMs = (durationMs * (percent / 100f)).toLong().coerceIn(0L, durationMs - 1L)
                VideoFormatSample(
                    positionPercent = percent,
                    timeMs = timeMs,
                    format = frameFormatAt(retriever, timeMs, fallback),
                )
            }

            buildAnalysis(
                durationMs = durationMs,
                fallback = fallback,
                samples = samples,
                boundaryResolver = { left, right, leftFormat ->
                    refineBoundary(
                        retriever = retriever,
                        leftTimeMs = left,
                        rightTimeMs = right,
                        leftFormat = leftFormat,
                        fallback = fallback,
                    )
                },
                frameResolver = { timeMs -> frameFormatAt(retriever, timeMs, fallback) },
            )
        } finally {
            retriever.release()
        }
    }

    suspend fun analyzeTrack(
        context: Context,
        uri: Uri,
        stream: MediaStreamInfo,
        containerDurationMs: Long,
    ): VideoFormatAnalysis = withContext(Dispatchers.IO) {
        val durationMs = (stream.durationMs ?: containerDurationMs).coerceAtLeast(1L)
        val fallback = VideoFrameFormat(
            width = stream.width,
            height = stream.height,
            rotationDegrees = stream.rotationDegrees,
            codecMime = codecMime(stream.codecName),
            frameRate = stream.frameRate,
            pixelAspectRatio = stream.sampleAspectRatio.takeIf { it > 0f } ?: 1f,
        )
        suspend fun formatAt(timeMs: Long): VideoFrameFormat {
            val dimensions = FfmpegTrackTools.frameDimensions(
                context = context,
                uri = uri,
                streamIndex = stream.index,
                timeMs = timeMs,
            )
            return if (dimensions == null) fallback else fallback.copy(width = dimensions.first, height = dimensions.second)
        }

        val samples = mutableListOf<VideoFormatSample>()
        for (percent in samplePercentages) {
            val timeMs = (durationMs * (percent / 100f)).toLong().coerceIn(0L, durationMs - 1L)
            samples += VideoFormatSample(
                positionPercent = percent,
                timeMs = timeMs,
                format = formatAt(timeMs),
            )
        }

        val grouped = FormatDetector.counts(samples, fallback)
        val majority = grouped.first()
        val ruptures = mutableListOf<FormatRupture>()
        for ((left, right) in samples.zipWithNext()) {
            if (left.format.key != right.format.key) {
                val boundary = refineBoundaryTrack(
                    leftTimeMs = left.timeMs,
                    rightTimeMs = right.timeMs,
                    leftFormat = left.format,
                    frameResolver = ::formatAt,
                )
                ruptures += FormatRupture(
                    timeMs = boundary,
                    from = formatAt((boundary - 80L).coerceAtLeast(0L)),
                    to = formatAt((boundary + 80L).coerceAtMost(durationMs - 1L)),
                )
            }
        }
        val distinctRuptures = ruptures.sortedBy(FormatRupture::timeMs).distinctBy { it.timeMs / 250L }
        val firstSampleIsSecondary = samples.firstOrNull()?.format?.key != majority.format.key
        val firstRupture = distinctRuptures.firstOrNull()
        val possibleIntroOrAd = firstSampleIsSecondary && firstRupture != null &&
            firstRupture.timeMs <= minOf(20_000L, (durationMs * 0.12f).toLong().coerceAtLeast(4_000L))

        VideoFormatAnalysis(
            durationMs = durationMs,
            sourceFormat = fallback,
            samples = samples,
            majority = majority,
            formats = grouped,
            ruptures = distinctRuptures,
            possibleIntroOrAd = possibleIntroOrAd,
        )
    }

    private fun buildAnalysis(
        durationMs: Long,
        fallback: VideoFrameFormat,
        samples: List<VideoFormatSample>,
        boundaryResolver: (Long, Long, VideoFrameFormat) -> Long,
        frameResolver: (Long) -> VideoFrameFormat,
    ): VideoFormatAnalysis {
        val grouped = FormatDetector.counts(samples, fallback)
        val majority = grouped.first()
        val ruptures = buildList {
            samples.zipWithNext().forEach { (left, right) ->
                if (left.format.key != right.format.key) {
                    val boundary = boundaryResolver(left.timeMs, right.timeMs, left.format)
                    add(
                        FormatRupture(
                            timeMs = boundary,
                            from = frameResolver((boundary - 80).coerceAtLeast(0)),
                            to = frameResolver((boundary + 80).coerceAtMost(durationMs - 1)),
                        ),
                    )
                }
            }
        }.sortedBy { it.timeMs }
            .distinctBy { it.timeMs / 250L }

        val firstSampleIsSecondary = samples.firstOrNull()?.format?.key != majority.format.key
        val firstRupture = ruptures.firstOrNull()
        val possibleIntroOrAd = firstSampleIsSecondary && firstRupture != null &&
            firstRupture.timeMs <= minOf(20_000L, (durationMs * 0.12f).toLong().coerceAtLeast(4_000L))

        return VideoFormatAnalysis(
            durationMs = durationMs,
            sourceFormat = fallback,
            samples = samples,
            majority = majority,
            formats = grouped,
            ruptures = ruptures,
            possibleIntroOrAd = possibleIntroOrAd,
        )
    }

    private fun frameFormatAt(
        retriever: MediaMetadataRetriever,
        timeMs: Long,
        fallback: VideoFrameFormat,
    ): VideoFrameFormat {
        val bitmap = runCatching {
            retriever.getFrameAtTime(
                timeMs.coerceAtLeast(0L) * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST,
            )
        }.getOrNull()
        return if (bitmap == null) {
            fallback
        } else {
            try {
                fallback.copy(width = bitmap.width, height = bitmap.height)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun refineBoundary(
        retriever: MediaMetadataRetriever,
        leftTimeMs: Long,
        rightTimeMs: Long,
        leftFormat: VideoFrameFormat,
        fallback: VideoFrameFormat,
    ): Long {
        var left = leftTimeMs
        var right = rightTimeMs
        var iterations = 0
        while (right - left > 80L && iterations < 14) {
            val middle = left + (right - left) / 2L
            val middleFormat = frameFormatAt(retriever, middle, fallback)
            if (middleFormat.key == leftFormat.key) {
                left = middle
            } else {
                right = middle
            }
            iterations++
        }
        return right
    }

    private suspend fun refineBoundaryTrack(
        leftTimeMs: Long,
        rightTimeMs: Long,
        leftFormat: VideoFrameFormat,
        frameResolver: suspend (Long) -> VideoFrameFormat,
    ): Long {
        var left = leftTimeMs
        var right = rightTimeMs
        var iterations = 0
        while (right - left > 120L && iterations < 10) {
            val middle = left + (right - left) / 2L
            if (frameResolver(middle).key == leftFormat.key) left = middle else right = middle
            iterations++
        }
        return right
    }

    private data class TrackInfo(
        val codecMime: String? = null,
        val frameRate: Float? = null,
        val pixelAspectRatio: Float = 1f,
    )

    private fun readVideoTrackInfo(context: Context, uri: Uri): TrackInfo {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var videoFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoFormat = format
                    break
                }
            }
            val format = videoFormat ?: return TrackInfo()
            val sarWidth = runCatching { format.getInteger("sar-width") }.getOrNull() ?: 1
            val sarHeight = runCatching { format.getInteger("sar-height") }.getOrNull() ?: 1
            TrackInfo(
                codecMime = format.getString(MediaFormat.KEY_MIME),
                frameRate = runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull()
                    ?: runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrNull(),
                pixelAspectRatio = if (sarWidth > 0 && sarHeight > 0) sarWidth.toFloat() / sarHeight.toFloat() else 1f,
            )
        } catch (_: Throwable) {
            TrackInfo()
        } finally {
            extractor.release()
        }
    }

    private fun codecMime(codecName: String?): String? = when (codecName?.lowercase()) {
        "h264", "avc" -> "video/avc"
        "hevc", "h265" -> "video/hevc"
        "vp9" -> "video/x-vnd.on2.vp9"
        "vp8" -> "video/x-vnd.on2.vp8"
        "av1" -> "video/av01"
        else -> codecName?.let { "video/$it" }
    }
}
