package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class V09MigrationRepairTest {
    @Test fun v08SequentialIndependentStreamsBecomeParallelTracks() {
        val short = VideoClip(
            id = "video0", sourceId = "s0", containerUri = "content://same", sourceStreamIndex = 0,
            timelineTrackIndex = 0, timelineStartMs = 0, uri = "content://video0", name = "video0",
            durationMs = 8_000, width = 1920, height = 1080,
        )
        val long = VideoClip(
            id = "video1", sourceId = "s1", containerUri = "content://same", sourceStreamIndex = 1,
            timelineTrackIndex = 0, timelineStartMs = 8_000, uri = "content://video1", name = "video1",
            durationMs = 272_000, width = 1080, height = 1920,
        )
        val repaired = VideoProject(
            projectFormatVersion = 8, name = "legacy v08", clips = listOf(short, long),
            timelineMode = TimelineMode.MULTITRACK,
        ).repairIndependentStreamLayout().copy(projectFormatVersion = 9)

        val a = repaired.clips.first { it.id == "video0" }
        val b = repaired.clips.first { it.id == "video1" }
        assertNotEquals(a.timelineTrackIndex, b.timelineTrackIndex)
        assertEquals(0L, a.timelineStartMs)
        assertEquals(0L, b.timelineStartMs)
        assertEquals(272_000L, repaired.durationMs)
        assertEquals(9, repaired.projectFormatVersion)
    }
}
