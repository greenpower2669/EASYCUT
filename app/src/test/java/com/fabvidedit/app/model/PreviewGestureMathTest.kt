package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewGestureMathTest {
    @Test
    fun oneFingerDragIsOneToOneInOutputFrameAndDoesNotRotate() {
        val start = ClipTransform(scaleX = 2f, scaleY = 2f, rotationDegrees = 35f)
        val result = PreviewGestureMath.panZoom(start, 300f, 200f, 150f, 100f, 60f, 30f, 1f)
        assertEquals(0.4f, result.positionX, 0.0001f)
        assertEquals(-0.3f, result.positionY, 0.0001f)
        assertEquals(2f, result.scaleX, 0.0001f)
        assertEquals(35f, result.rotationDegrees, 0.0001f)
    }

    @Test
    fun pinchKeepsCentroidAnchoredInsteadOfJumpingToScreenCenter() {
        val result = PreviewGestureMath.panZoom(
            ClipTransform(), 300f, 200f, 250f, 100f, 0f, 0f, 2f,
        )
        assertEquals(2f, result.scaleX, 0.0001f)
        assertEquals(-2f / 3f, result.positionX, 0.0001f)
        assertEquals(0f, result.positionY, 0.0001f)
        assertEquals(0f, result.rotationDegrees, 0.0001f)
    }

    @Test
    fun subsequentDragAfterPinchUsesSameVisibleFramePixels() {
        val zoomed = PreviewGestureMath.panZoom(
            ClipTransform(), 200f, 200f, 150f, 100f, 0f, 0f, 2f,
        )
        val dragged = PreviewGestureMath.panZoom(
            zoomed, 200f, 200f, 150f, 100f, 20f, -10f, 1f,
        )
        assertEquals(-0.3f, dragged.positionX, 0.0001f)
        assertEquals(0.1f, dragged.positionY, 0.0001f)
        assertEquals(2f, dragged.scaleX, 0.0001f)
    }

    @Test
    fun existingRotationAndOffCenterPivotArePreserved() {
        val start = ClipTransform(pivotX = 1f, rotationDegrees = -42f)
        val actual = PreviewGestureMath.panZoom(
            start, 300f, 200f, 200f, 100f, 0f, 0f, 2f,
        )
        assertEquals(2f / 3f, actual.positionX, 0.0001f)
        assertEquals(-42f, actual.rotationDegrees, 0.0001f)
        assertEquals(1f, actual.pivotX, 0.0001f)
    }

    @Test
    fun zoomAtScaleLimitDoesNotAddPhantomTranslation() {
        val start = ClipTransform(scaleX = 4f, scaleY = 4f, positionX = 0.1f)
        val actual = PreviewGestureMath.panZoom(
            start, 300f, 200f, 240f, 100f, 0f, 0f, 2f,
        )
        assertEquals(4f, actual.scaleX, 0.0001f)
        assertEquals(start.positionX, actual.positionX, 0.0001f)
    }

    @Test
    fun intentionalRotationKeepsOffCenterTouchPointUnderFingers() {
        val actual = PreviewGestureMath.panZoom(
            ClipTransform(), 300f, 200f, 250f, 100f, 0f, 0f, 1f, 90f,
        )
        assertEquals(90f, actual.rotationDegrees, 0.0001f)
        assertEquals(2f / 3f, actual.positionX, 0.0001f)
        assertEquals(1f, actual.positionY, 0.0001f)
    }

    @Test
    fun pinchAndRotationCanHappenTogetherAfterGateUnlocks() {
        val actual = PreviewGestureMath.panZoom(
            ClipTransform(), 300f, 200f, 250f, 100f, 0f, 0f, 2f, 90f,
        )
        assertEquals(2f, actual.scaleX, 0.0001f)
        assertEquals(90f, actual.rotationDegrees, 0.0001f)
        assertEquals(2f / 3f, actual.positionX, 0.0001f)
        assertEquals(2f, actual.positionY, 0.0001f)
    }

    @Test
    fun rotationAtAngleLimitCannotCreatePhantomTranslation() {
        val start = ClipTransform(rotationDegrees = 720f, positionX = 0.12f)
        val actual = PreviewGestureMath.panZoom(
            start, 300f, 200f, 220f, 100f, 0f, 0f, 1f, 20f,
        )
        assertEquals(720f, actual.rotationDegrees, 0.0001f)
        assertEquals(start.positionX, actual.positionX, 0.0001f)
    }

    @Test
    fun existingOffCenterPivotAndRotationRemainStableOnPureDrag() {
        val start = ClipTransform(
            scaleX = 2f, scaleY = 1.5f,
            pivotX = 0.6f, pivotY = -0.4f,
            rotationDegrees = 45f,
        )
        val actual = PreviewGestureMath.panZoom(
            start, 300f, 200f, 140f, 120f, 15f, -12f, 1f, 0f,
        )
        assertEquals(0.1f, actual.positionX, 0.0001f)
        assertEquals(0.12f, actual.positionY, 0.0001f)
        assertEquals(45f, actual.rotationDegrees, 0.0001f)
    }
}
