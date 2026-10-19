package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class TransitionTimingTest {
    private val clip = VideoClip(uri = "file:///test.mp4", name = "test", durationMs = 1_500L)
    private val fade = ClipTransition(fromClipId = "before", toClipId = clip.id,
        type = TransitionType.FADE, durationMs = 2_000L)

    @Test fun shortClipCapsTransitionToOneThirdOfSource() {
        assertEquals(0f, TransitionTiming.progress(clip, 0L, fade, true)!!, 0.0001f)
        assertEquals(0.5f, TransitionTiming.progress(clip, 250L, fade, true)!!, 0.0001f)
        assertEquals(1f, TransitionTiming.progress(clip, 500L, fade, true)!!, 0.0001f)
        assertNull(TransitionTiming.progress(clip, 501L, fade, true))
        assertEquals(0f, TransitionTiming.progress(clip, 1000L, fade, false)!!, 0.0001f)
        assertEquals(1f, TransitionTiming.progress(clip, 1500L, fade, false)!!, 0.0001f)
    }

    @Test fun acceleratedClipScalesTransitionClockBySpeed() {
        val fast = clip.copy(speed = 2f, durationMs = 6_000L, trimEndMs = 6_000L)
        // Two seconds selected => one second on the output, two seconds in the source at speed x2.
        assertEquals(0.5f, TransitionTiming.progress(fast, 1000L, fade, true)!!, 0.0001f)
        assertNotNull(TransitionTiming.progress(fast, 1999L, fade, true))
        assertNull(TransitionTiming.progress(fast, 2001L, fade, true))
    }

    @Test fun nullTransitionDoesNotAnimate() {
        assertNull(TransitionTiming.progress(clip, 0L, null, true))
    }
}
