package com.fabvidedit.app.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportCanvasGeometryTest {
    @Test fun portraitUsesProjectAspectEvenWhenMetadataIsSquare() {
        val size = ExportCanvasGeometry.resolve(
            canvasRatio = 9f / 16f,
            requestedShortSide = 720,
            sourceShortSide = 720,
            fallbackWidth = 720,
            fallbackHeight = 720,
        )
        assertEquals(720, size.width)
        assertEquals(1280, size.height)
    }

    @Test fun synthetic16x16GapCannotSelectOutputSize() {
        // Media3 reports 16x16 frames for an initial addGap(). The geometry is
        // decided once from project intent, without taking an inputSizes argument.
        val expected = ExportCanvasGeometry.Canvas(720, 1280)
        repeat(100) {
            assertEquals(expected, ExportCanvasGeometry.resolve(
                9f / 16f, 720, 720, 720, 720,
            ))
        }
    }

    @Test fun preservesLandscapeSquareAndSourceResolution() {
        assertEquals(ExportCanvasGeometry.Canvas(1280, 720),
            ExportCanvasGeometry.resolve(16f / 9f, 720, 720, 720, 720))
        assertEquals(ExportCanvasGeometry.Canvas(720, 720),
            ExportCanvasGeometry.resolve(1f, 720, 720, 720, 720))
        assertEquals(ExportCanvasGeometry.Canvas(1080, 1920),
            ExportCanvasGeometry.resolve(9f / 16f, null, 1080, 1080, 1920))
    }

    @Test fun widthAndHeightRemainEvenForVideoEncoders() {
        val value = ExportCanvasGeometry.resolve(4f / 5f, 541, 720, 720, 900)
        assertEquals(0, value.width % 2)
        assertEquals(0, value.height % 2)
    }
}
