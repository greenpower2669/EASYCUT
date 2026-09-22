package com.fabvidedit.app.media

import com.fabvidedit.app.model.AspectRatioPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatOptionsTest {

    @Test
    fun `export codec choices expose h264 and h265`() {
        assertEquals(
            listOf(ExportVideoCodec.H264, ExportVideoCodec.H265),
            ExportVideoCodec.entries,
        )
        assertTrue(ExportVideoCodec.H264.label.contains("H.264"))
        assertTrue(ExportVideoCodec.H265.label.contains("H.265"))
    }

    @Test
    fun `export frame rate choices expose source 24 25 30 50 and 60 fps`() {
        assertEquals(
            listOf(null, 24, 25, 30, 50, 60),
            ExportFrameRate.entries.map(ExportFrameRate::fps),
        )
        assertNull(ExportFrameRate.SOURCE.fps)
    }

    @Test
    fun `output format choices include all checkpoint ratios`() {
        assertEquals(
            listOf("Source", "9:16", "16:9", "1:1", "4:5", "3:4", "4:3", "21:9"),
            AspectRatioPreset.entries.map(AspectRatioPreset::label),
        )
    }
}
