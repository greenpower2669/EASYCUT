package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClipTransformMatrixTest {
    @Test
    fun interpolateTo_interpolatesScalePositionRotationAndPivot() {
        val start = ClipTransform(
            scaleX = 1f,
            scaleY = 2f,
            positionX = -1f,
            positionY = 1f,
            rotationDegrees = 0f,
            pivotX = -1f,
            pivotY = 1f,
        )
        val end = ClipTransform(
            scaleX = 3f,
            scaleY = 4f,
            positionX = 1f,
            positionY = -1f,
            rotationDegrees = 90f,
            pivotX = 1f,
            pivotY = -1f,
        )

        val mid = start.interpolateTo(end, 0.5f)

        assertEquals(2f, mid.scaleX, 0.001f)
        assertEquals(3f, mid.scaleY, 0.001f)
        assertEquals(0f, mid.positionX, 0.001f)
        assertEquals(0f, mid.positionY, 0.001f)
        assertEquals(45f, mid.rotationDegrees, 0.001f)
        assertEquals(0f, mid.pivotX, 0.001f)
        assertEquals(0f, mid.pivotY, 0.001f)
    }

    @Test
    fun interpolateTo_rotationUsesShortestArcAcross180Degrees() {
        val start = ClipTransform(rotationDegrees = 170f)
        val end = ClipTransform(rotationDegrees = -170f)

        val mid = start.interpolateTo(end, 0.5f)

        assertTrue("rotation should cross the ±180° boundary", abs(abs(mid.rotationDegrees) - 180f) < 0.01f)
    }

    @Test
    fun transformAtSourceTime_interpolatesCompleteKeyframeMatrix() {
        val clip = VideoClip(
            uri = "file:///tmp/test.mp4",
            name = "test",
            durationMs = 1_000L,
            transform = ClipTransform(),
            keyframes = listOf(
                TransformKeyframe(
                    timeMs = 1_000L,
                    easing = MotionEasing.LINEAR,
                    transform = ClipTransform(
                        scaleX = 2f,
                        scaleY = 3f,
                        positionX = 1f,
                        positionY = -1f,
                        rotationDegrees = 90f,
                        pivotX = 0.5f,
                        pivotY = -0.5f,
                    ),
                ),
            ),
        )

        val mid = clip.transformAtSourceTime(500L)

        assertEquals(1.5f, mid.scaleX, 0.001f)
        assertEquals(2f, mid.scaleY, 0.001f)
        assertEquals(0.5f, mid.positionX, 0.001f)
        assertEquals(-0.5f, mid.positionY, 0.001f)
        assertEquals(45f, mid.rotationDegrees, 0.001f)
        assertEquals(0.25f, mid.pivotX, 0.001f)
        assertEquals(-0.25f, mid.pivotY, 0.001f)
    }
}
