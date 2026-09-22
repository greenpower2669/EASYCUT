package com.fabvidedit.app.media

import com.fabvidedit.app.model.VideoFormatCount
import com.fabvidedit.app.model.VideoFormatSample
import com.fabvidedit.app.model.VideoFrameFormat

object FormatDetector {
    fun counts(samples: List<VideoFormatSample>, fallback: VideoFrameFormat): List<VideoFormatCount> {
        if (samples.isEmpty()) return listOf(VideoFormatCount(fallback, 1, 1f))
        return samples
            .groupBy { it.format.key }
            .values
            .map { group ->
                VideoFormatCount(
                    format = group.first().format,
                    samples = group.size,
                    share = group.size.toFloat() / samples.size.toFloat(),
                )
            }
            .sortedWith(
                compareByDescending<VideoFormatCount> { it.samples }
                    .thenByDescending { it.format.width.toLong() * it.format.height.toLong() },
            )
    }

    fun majority(samples: List<VideoFormatSample>, fallback: VideoFrameFormat): VideoFormatCount =
        counts(samples, fallback).first()
}
