package com.fabvidedit.app.media

import com.fabvidedit.app.model.VideoFormatSample
import com.fabvidedit.app.model.VideoFrameFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatDetectorTest {
    @Test
    fun portraitMainContentWinsOverLandscapeIntro() {
        val landscape = VideoFrameFormat(width = 1920, height = 1080)
        val portrait = VideoFrameFormat(width = 1080, height = 1920)
        val samples = buildList {
            add(VideoFormatSample(0.5f, 1_360L, landscape))
            add(VideoFormatSample(2f, 5_440L, landscape))
            listOf(10f, 20f, 30f, 40f, 50f, 60f, 70f, 80f, 95f).forEachIndexed { index, percent ->
                add(VideoFormatSample(percent, 27_200L + index * 20_000L, portrait))
            }
        }

        val counts = FormatDetector.counts(samples, landscape)
        val majority = counts.first()

        assertEquals(1080, majority.format.width)
        assertEquals(1920, majority.format.height)
        assertEquals("9:16", majority.format.ratioLabel)
        assertEquals(9, majority.samples)
        assertTrue(majority.share > 0.80f)
        assertEquals(2, counts[1].samples)
    }

    @Test
    fun emptySamplesUseFallback() {
        val fallback = VideoFrameFormat(width = 1280, height = 720)
        val majority = FormatDetector.majority(emptyList(), fallback)

        assertEquals(fallback, majority.format)
        assertEquals(1, majority.samples)
        assertEquals(1f, majority.share)
    }
}
