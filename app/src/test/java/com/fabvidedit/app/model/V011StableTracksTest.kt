package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class V011StableTracksTest {
    @Test fun singleClipCanMoveFromV1ToEmptyV6WithoutRenumbering() {
        val clip = VideoClip(id="one", uri="content://one", name="one", durationMs=5_000, timelineTrackIndex=0)
        val moved = VideoProject(name="stable", clips=listOf(clip), timelineMode=TimelineMode.MULTITRACK)
            .moveClipOnTimeline("one", 5, 1_000)
            .clips.single()
        assertEquals(5, moved.timelineTrackIndex)
        assertEquals(1_000L, moved.timelineStartMs)
    }

    @Test fun deletingV1DoesNotRenameV3ToV1() {
        val v1 = VideoClip(id="v1", uri="content://v1", name="v1", durationMs=2_000, timelineTrackIndex=0)
        val v3 = VideoClip(id="v3", uri="content://v3", name="v3", durationMs=2_000, timelineTrackIndex=2)
        val remaining = VideoProject(name="stable", clips=listOf(v1, v3), timelineMode=TimelineMode.MULTITRACK)
            .rippleDeleteClip("v1")
            .clips.single()
        assertEquals(2, remaining.timelineTrackIndex)
    }
}
