package com.fabvidedit.app.media

import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.TransformKeyframe
import com.fabvidedit.app.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportClipTimingTest {
    @Test fun delayedV2ReachesItsActualKeyframes() {
        val clip = VideoClip(
            uri = "file:///test.mp4", name = "V2", durationMs = 7_533L, timelineStartMs = 5_871L,
            keyframes = listOf(
                TransformKeyframe(timeMs = 1_313L, transform = ClipTransform()),
                TransformKeyframe(timeMs = 2_104L, transform = ClipTransform(scaleX = 2.0855768f, rotationDegrees = 91.86181f)),
                TransformKeyframe(timeMs = 3_024L, transform = ClipTransform()),
            ),
        )
        val beginning = ExportClipTiming.sourceLocalMs(5_871_000L, 5_871L, clip.speed, clip.sourceDurationMs)
        val peak = ExportClipTiming.sourceLocalMs(7_975_000L, 5_871L, clip.speed, clip.sourceDurationMs)
        val end = ExportClipTiming.sourceLocalMs(8_895_000L, 5_871L, clip.speed, clip.sourceDurationMs)
        assertEquals(0L, beginning)
        assertEquals(2_104L, peak)
        assertEquals(3_024L, end)
        assertEquals(1f, clip.transformAtSourceTime(beginning).scaleX, .001f)
        assertEquals(2.0855768f, clip.transformAtSourceTime(peak).scaleX, .001f)
        assertEquals(91.86181f, clip.transformAtSourceTime(peak).rotationDegrees, .001f)
        assertEquals(1f, clip.transformAtSourceTime(end).scaleX, .001f)
    }

    @Test fun V1AndSequentialKeepOriginZero() {
        for (ms in listOf(0L, 1_000L, 3_941L, 4_580L, 6_066L)) {
            assertEquals(ms, ExportClipTiming.sourceLocalMs(ms * 1_000L, 0L, 1f, 6_066L))
        }
    }

    @Test fun speedAndBoundsUseSourceLocalMs() {
        assertEquals(1_000L, ExportClipTiming.sourceLocalMs(6_371_000L, 5_871L, 2f, 7_533L))
        assertEquals(0L, ExportClipTiming.sourceLocalMs(5_500_000L, 5_871L, 1f, 7_533L))
        assertEquals(7_533L, ExportClipTiming.sourceLocalMs(25_000_000L, 5_871L, 1f, 7_533L))
    }
}
