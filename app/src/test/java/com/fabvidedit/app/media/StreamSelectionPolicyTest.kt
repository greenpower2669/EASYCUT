package com.fabvidedit.app.media

import com.fabvidedit.app.model.MediaStreamInfo
import com.fabvidedit.app.model.MediaStreamKind
import com.fabvidedit.app.model.StreamSelectionConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSelectionPolicyTest {
    @Test
    fun `long portrait track wins over short landscape overlay`() {
        val streams = listOf(
            MediaStreamInfo(
                index = 0,
                kind = MediaStreamKind.VIDEO,
                codecName = "h264",
                width = 1920,
                height = 1080,
                frameRate = 25f,
                durationMs = 8_200L,
                startTimeMs = 0L,
                frameCount = 205L,
            ),
            MediaStreamInfo(
                index = 1,
                kind = MediaStreamKind.VIDEO,
                codecName = "h264",
                width = 1080,
                height = 1920,
                frameRate = 30f,
                durationMs = 272_000L,
                startTimeMs = 0L,
                frameCount = 8_160L,
                isDefault = true,
            ),
            MediaStreamInfo(
                index = 2,
                kind = MediaStreamKind.AUDIO,
                codecName = "aac",
                durationMs = 272_000L,
                isDefault = true,
            ),
        )

        val result = StreamSelectionPolicy.recommend(streams, 272_000L)

        assertEquals(1, result.selectedVideoIndex)
        assertEquals(2, result.selectedAudioIndex)
        assertEquals(StreamSelectionConfidence.HIGH, result.confidence)
        assertEquals(setOf(0), result.automaticallyExcludedVideoIndexes)
        assertTrue(result.simultaneousVideoConflict)
        assertFalse(result.requiresManualChoice)
    }

    @Test
    fun `similar simultaneous tracks require manual choice and no automatic exclusion`() {
        val streams = listOf(
            MediaStreamInfo(
                index = 0,
                kind = MediaStreamKind.VIDEO,
                codecName = "h264",
                width = 1920,
                height = 1080,
                durationMs = 120_000L,
                frameCount = 3_000L,
            ),
            MediaStreamInfo(
                index = 1,
                kind = MediaStreamKind.VIDEO,
                codecName = "h264",
                width = 1920,
                height = 1080,
                durationMs = 120_000L,
                frameCount = 3_000L,
            ),
        )

        val result = StreamSelectionPolicy.recommend(streams, 120_000L)

        assertEquals(StreamSelectionConfidence.LOW, result.confidence)
        assertTrue(result.requiresManualChoice)
        assertTrue(result.automaticallyExcludedVideoIndexes.isEmpty())
        assertTrue(result.simultaneousVideoConflict)
    }

    @Test
    fun `normal one video one audio file keeps classic automatic behaviour`() {
        val streams = listOf(
            MediaStreamInfo(
                index = 0,
                kind = MediaStreamKind.VIDEO,
                codecName = "hevc",
                width = 1080,
                height = 1920,
                durationMs = 90_000L,
            ),
            MediaStreamInfo(
                index = 1,
                kind = MediaStreamKind.AUDIO,
                codecName = "aac",
                durationMs = 90_000L,
            ),
        )

        val result = StreamSelectionPolicy.recommend(streams, 90_000L)

        assertEquals(0, result.selectedVideoIndex)
        assertEquals(1, result.selectedAudioIndex)
        assertEquals(StreamSelectionConfidence.HIGH, result.confidence)
        assertTrue(result.automaticallyExcludedVideoIndexes.isEmpty())
        assertFalse(result.simultaneousVideoConflict)
    }

    @Test
    fun `attached picture is not treated as a competing video`() {
        val streams = listOf(
            MediaStreamInfo(
                index = 0,
                kind = MediaStreamKind.VIDEO,
                codecName = "mjpeg",
                width = 1000,
                height = 1000,
                isAttachedPicture = true,
            ),
            MediaStreamInfo(
                index = 1,
                kind = MediaStreamKind.VIDEO,
                codecName = "h264",
                width = 1280,
                height = 720,
                durationMs = 60_000L,
            ),
        )

        val result = StreamSelectionPolicy.recommend(streams, 60_000L)

        assertEquals(1, result.selectedVideoIndex)
        assertEquals(StreamSelectionConfidence.HIGH, result.confidence)
        assertFalse(result.simultaneousVideoConflict)
    }
}
