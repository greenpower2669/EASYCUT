package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultitrackProjectTest {
    @Test
    fun independentStreamsKeepTheirOwnTracksAndDuration() {
        val short = VideoClip(
            uri = "content://fab/video0",
            name = "VIDEO 0",
            durationMs = 8_000L,
            width = 1920,
            height = 1080,
            sourceStreamIndex = 0,
            timelineTrackIndex = 0,
            timelineStartMs = 0L,
        )
        val long = VideoClip(
            uri = "content://fab/video1",
            name = "VIDEO 1",
            durationMs = 272_000L,
            width = 1080,
            height = 1920,
            sourceStreamIndex = 1,
            timelineTrackIndex = 1,
            timelineStartMs = 0L,
        )
        val audio = SourceAudioTrack(
            sourceId = "audio-source",
            sourceStreamIndex = 2,
            timelineTrackIndex = 0,
            uri = "content://fab/audio2",
            name = "AUDIO 2",
            durationMs = 272_000L,
        )
        val project = VideoProject(
            name = "multistream",
            clips = listOf(short, long),
            sourceAudioTracks = listOf(audio),
            timelineMode = TimelineMode.MULTITRACK,
        )

        assertEquals(TimelineMode.MULTITRACK, project.timelineMode)
        assertEquals(0, short.timelineTrackIndex)
        assertEquals(1, long.timelineTrackIndex)
        assertEquals(0L, short.timelineStartMs)
        assertEquals(0L, long.timelineStartMs)
        assertEquals(272_000L, project.durationMs)
        assertEquals(1920, short.width)
        assertEquals(1080, short.height)
        assertEquals(1080, long.width)
        assertEquals(1920, long.height)
        assertEquals(0, short.sourceStreamIndex)
        assertEquals(1, long.sourceStreamIndex)
        assertNull(project.nextClipOnTrack(0))
        assertNull(project.nextClipOnTrack(1))
        assertEquals(2, project.clips.map(VideoClip::timelineTrackIndex).distinct().size)
    }

    @Test
    fun v06RepairsLegacyStreamsThatWerePutEndToEndOnOneTrack() {
        val container = "content://fab/original-container"
        val first = VideoClip(
            sourceId = "stream-0",
            containerUri = container,
            sourceStreamIndex = 0,
            timelineTrackIndex = 0,
            timelineStartMs = 0L,
            uri = "content://fab/video0",
            name = "VIDEO 0",
            durationMs = 8_000L,
        )
        val second = VideoClip(
            sourceId = "stream-1",
            containerUri = container,
            sourceStreamIndex = 1,
            timelineTrackIndex = 0,
            timelineStartMs = 8_000L,
            uri = "content://fab/video1",
            name = "VIDEO 1",
            durationMs = 272_000L,
        )
        val legacy = VideoProject(
            name = "legacy bad layout",
            clips = listOf(first, second),
            timelineMode = TimelineMode.MULTITRACK,
        )

        val repaired = legacy.repairIndependentStreamLayout()

        assertNotEquals(repaired.clips[0].timelineTrackIndex, repaired.clips[1].timelineTrackIndex)
        assertEquals(0L, repaired.clips[0].timelineStartMs)
        assertEquals(0L, repaired.clips[1].timelineStartMs)
        assertEquals(272_000L, repaired.durationMs)
    }


    @Test
    fun fourIndependentVideoStreamsCreateFourTracksAtZero() {
        val clips = (0..3).map { streamIndex ->
            VideoClip(
                sourceId = "source-$streamIndex",
                containerUri = "content://fab/four-stream-container",
                sourceStreamIndex = streamIndex,
                timelineTrackIndex = streamIndex,
                timelineStartMs = 0L,
                uri = "content://fab/video$streamIndex",
                name = "VIDEO $streamIndex",
                durationMs = 10_000L + streamIndex,
                width = 1920,
                height = 1080,
            )
        }
        val project = VideoProject(
            name = "four streams",
            clips = clips,
            timelineMode = TimelineMode.MULTITRACK,
        )

        assertTrue(project.clips.all { it.timelineStartMs == 0L })
        assertEquals(4, project.clips.map(VideoClip::timelineTrackIndex).distinct().size)
        val uiLabels = project.clips
            .groupBy(VideoClip::timelineTrackIndex)
            .toSortedMap(compareByDescending { it })
            .keys
            .map { "V${it + 1}" }
        assertEquals(listOf("V4", "V3", "V2", "V1"), uiLabels)
    }

    @Test
    fun splitClipsFromSameStreamStayTogetherDuringMigration() {
        val container = "content://fab/split-protection"
        val left = VideoClip(
            sourceId = "stream-0",
            containerUri = container,
            sourceStreamIndex = 0,
            timelineTrackIndex = 0,
            timelineStartMs = 0L,
            uri = "content://fab/video0",
            name = "VIDEO 0 left",
            durationMs = 8_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
        )
        val right = left.copy(
            id = "split-right",
            timelineStartMs = 4_000L,
            trimStartMs = 4_000L,
            trimEndMs = 8_000L,
            name = "VIDEO 0 right",
        )
        val independent = VideoClip(
            sourceId = "stream-1",
            containerUri = container,
            sourceStreamIndex = 1,
            timelineTrackIndex = 0,
            timelineStartMs = 8_000L,
            uri = "content://fab/video1",
            name = "VIDEO 1",
            durationMs = 20_000L,
        )
        val repaired = VideoProject(
            name = "split migration",
            clips = listOf(left, right, independent),
            timelineMode = TimelineMode.MULTITRACK,
        ).repairIndependentStreamLayout()

        val repairedLeft = repaired.clips[0]
        val repairedRight = repaired.clips[1]
        val repairedIndependent = repaired.clips[2]
        assertEquals(repairedLeft.timelineTrackIndex, repairedRight.timelineTrackIndex)
        assertEquals(0L, repairedLeft.timelineStartMs)
        assertEquals(4_000L, repairedRight.timelineStartMs)
        assertNotEquals(repairedLeft.timelineTrackIndex, repairedIndependent.timelineTrackIndex)
        assertEquals(0L, repairedIndependent.timelineStartMs)
    }
}
