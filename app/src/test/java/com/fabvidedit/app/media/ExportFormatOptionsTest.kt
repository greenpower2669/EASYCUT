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
    fun `export frame rate choices include small cadences and existing presets`() {
        assertEquals(
            listOf(null, 1, 2, 3, 4, 5, 10, 12, 15, 20, 24, 25, 30, 50, 60),
            ExportFrameRate.entries.map(ExportFrameRate::fps),
        )
        assertNull(ExportFrameRate.SOURCE.fps)
    }

    @Test fun `custom fps overrides preset without modifying source`() {
        assertEquals(1, ExportSettings(customFps = 1).effectiveFps)
        assertEquals(60, ExportSettings(customFps = 60).effectiveFps)
        assertEquals(15, ExportSettings(frameRate = ExportFrameRate.FPS_15).effectiveFps)
        assertNull(ExportSettings().effectiveFps)
        assertEquals("10 i/s", ExportSettings(customFps = 10).fpsLabel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom fps rejects value above 60`() {
        ExportSettings(customFps = 61)
    }

    @Test
    fun `output format choices include all checkpoint ratios`() {
        assertEquals(
            listOf("Source", "9:16", "16:9", "1:1", "4:5", "3:4", "4:3", "21:9"),
            AspectRatioPreset.entries.map(AspectRatioPreset::label),
        )
    }
}
