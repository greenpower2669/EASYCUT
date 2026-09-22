package com.fabvidedit.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolutionFrameScannerTest {
    @Test
    fun `compact metadata parses without retaining a video frame`() {
        val sample = "stream_index=1|best_effort_timestamp_time=12.500000|width=1920|height=1080"
        val parsed = ResolutionFrameScanner.parseFrame(sample)
        assertEquals(1, parsed?.streamIndex)
        assertEquals(12_500L, parsed?.timeMs)
        assertEquals(1920, parsed?.width)
        assertEquals(1080, parsed?.height)
    }

    @Test
    fun `invalid frames are ignored rather than retained`() {
        assertNull(ResolutionFrameScanner.parseFrame("stream_index=0|width=0|height=1080"))
        assertNull(ResolutionFrameScanner.parseFrame("stream_index=0|width=1920|height=1080"))
        assertNull(ResolutionFrameScanner.parseFrame(""))
    }
}
