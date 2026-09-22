package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FABVID-MULTI-007: prevent an upper VIDEO gap from hiding a lower clip. */
class VideoLayerPolicyTest {
    private val upper = VideoClip(
        id = "v3", uri = "v3.mp4", name = "V3",
        durationMs = 10_000L, trimEndMs = 3_000L,
        timelineTrackIndex = 2, timelineStartMs = 0L,
    )
    private val lower = VideoClip(
        id = "v2", uri = "v2.mp4", name = "V2",
        durationMs = 10_000L,
        timelineTrackIndex = 1, timelineStartMs = 6_000L,
    )
    private val audio = SourceAudioTrack(
        sourceId = "a1", uri = "a1.m4a", name = "A1",
        timelineTrackIndex = 0, durationMs = 16_000L,
    )
    private val project = VideoProject(
        name = "V3 then V2", clips = listOf(upper, lower),
        sourceAudioTracks = listOf(audio), timelineMode = TimelineMode.MULTITRACK,
    )

    @Test fun upperLaneGapIsTransparentUntilVideoStartsAndAfterItEnds() {
        assertEquals(listOf(2, 1), VideoLayerPolicy.frontToBack(project))
        assertEquals(1f, VideoLayerPolicy.opacityAt(project.clips, 2, 2_999L), 0f)
        assertEquals(0f, VideoLayerPolicy.opacityAt(project.clips, 2, 3_000L), 0f)
        assertEquals(0f, VideoLayerPolicy.opacityAt(project.clips, 1, 5_999L), 0f)
        assertEquals(1f, VideoLayerPolicy.opacityAt(project.clips, 1, 6_000L), 0f)
        assertEquals(1f, VideoLayerPolicy.opacityAt(project.clips, 1, 15_999L), 0f)
        assertEquals(0f, VideoLayerPolicy.opacityAt(project.clips, 1, 16_000L), 0f)
    }

    @Test fun fallbackNeverShowsOldClipDuringHoleOrSelectedClipBehindActiveOne() {
        assertEquals("v3", VideoLayerPolicy.activeClip(project.clips, 500L)?.id)
        assertNull(VideoLayerPolicy.activeClip(project.clips, 4_000L))
        assertEquals("v2", VideoLayerPolicy.activeClip(project.clips, 7_000L)?.id)
        assertNull(VideoLayerPolicy.activeClip(project.clips, 16_000L))
        val overlapped = lower.copy(timelineStartMs = 1_000L)
        assertEquals("v3", VideoLayerPolicy.activeClip(listOf(overlapped, upper), 2_000L)?.id)
    }

    @Test fun durationUsesLastClipEndAndNeverEndsWithTheFirstTrack() {
        assertEquals(16_000L, project.durationMs)
        val prolonged = project.copy(clips = listOf(upper, lower.copy(trimEndMs = 10_000L,
            timelineStartMs = 8_000L)))
        assertEquals(18_000L, prolonged.durationMs)
    }

    @Test fun exchangePlansChangesNeitherTimesNorAudioAndCanUndoByMovingBack() {
        val before = project.clips.associateBy(VideoClip::id)
        val after = VideoLayerPolicy.moveLayer(project, "v2", 1)
        assertEquals(16_000L, after.durationMs)
        assertEquals(2, after.clips.first { it.id == "v2" }.timelineTrackIndex)
        assertEquals(1, after.clips.first { it.id == "v3" }.timelineTrackIndex)
        assertTrue(after.clips.all { it.timelineStartMs == before.getValue(it.id).timelineStartMs })
        assertEquals(project.sourceAudioTracks, after.sourceAudioTracks)
        assertEquals(project.clips, VideoLayerPolicy.moveLayer(after, "v2", -1).clips)
    }

    @Test fun opacityDoesNotTurnEmptyTimeIntoOpaqueContent() {
        val faded = upper.copy(opacity = 0.35f)
        assertEquals(0.35f, VideoLayerPolicy.opacityAt(listOf(faded, lower), 2, 1_000L), 0.001f)
        assertEquals(0f, VideoLayerPolicy.opacityAt(listOf(faded, lower), 2, 4_000L), 0f)
        assertEquals("v2", VideoLayerPolicy.activeClip(listOf(faded.copy(opacity = 0f),
            lower.copy(timelineStartMs = 0L)), 1_000L)?.id)
    }
}
