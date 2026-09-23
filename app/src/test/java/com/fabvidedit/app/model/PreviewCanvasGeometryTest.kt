package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewCanvasGeometryTest {
    @Test fun portraitVideoOnSquareCanvasUsesFitBeforeUserZoom() {
        val desired = ClipTransform(scaleX = 3.21f, scaleY = 3.21f,
            positionX = -0.10f, positionY = -1.15f, rotationDegrees = 2f)
        val actual = PreviewCanvasGeometry.forFrame(
            desired, 720f, 720f, 720f / 1280f, 1f)
        assertEquals(720f / 1280f, actual.fitX, 0.0001f)
        assertEquals(1f, actual.fitY, 0.0001f)
        assertEquals(3.21f, actual.scaleX, 0.0001f)
        assertEquals(3.21f, actual.scaleY, 0.0001f)
        assertEquals(-36f, actual.translationX, 0.0001f)
        assertEquals(414f, actual.translationY, 0.0001f)
        assertEquals(2f, actual.rotationDegrees, 0.0001f)
    }

    @Test fun squareAndPortraitCanvasesDontAddAnotherUnwantedScale() {
        val t = ClipTransform(scaleX = 3.80f, scaleY = 3.80f)
        val a = PreviewCanvasGeometry.forFrame(t, 360f, 640f, 9f/16f, 9f/16f)
        assertEquals(1f, a.fitX, 0.0001f)
        assertEquals(1f, a.fitY, 0.0001f)
        assertEquals(3.80f, a.scaleX, 0.0001f)
        assertEquals(3.80f, a.scaleY, 0.0001f)
    }

    @Test fun landscapeInputHasLetterboxButKeepsUserScaleAndPivot() {
        val t = ClipTransform(scaleX = 3.21f, scaleY = 2f,
            pivotX = 0.5f, pivotY = -0.5f)
        val a = PreviewCanvasGeometry.forFrame(t, 400f, 400f, 16f/9f, 1f)
        assertEquals(1f, a.fitX, 0.0001f)
        assertEquals(9f/16f, a.fitY, 0.0001f)
        assertEquals(300f, a.pivotX, 0.0001f)
        assertEquals(300f, a.pivotY, 0.0001f)
        assertEquals(3.21f, a.scaleX, 0.0001f)
    }    @Test fun sourceOrientationRatioIsSharedWithTheExport() {
        val upright = VideoClip(id="v1", uri="file://v1", name="portrait",
            durationMs=3_000L, width=720, height=1280)
        val sideways = upright.copy(rotationDegrees=90)
        assertEquals(720f / 1280f, upright.displayAspectRatio()!!, 0.0001f)
        assertEquals(1280f / 720f, sideways.displayAspectRatio()!!, 0.0001f)
        assertEquals(null, upright.copy(width=0).displayAspectRatio())
    }


}
