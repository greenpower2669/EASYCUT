package com.fabvidedit.app.media

import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.TransformKeyframe
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.displayAspectRatio
import org.junit.Assert.*
import org.junit.Test

class SarKeyframeGeometryTest {
    private fun clip(sar: Float = 1f, dar: Float = 0f) = VideoClip(
        uri = "file:///sample.mp4", name = "SAR", durationMs = 6000,
        width = 720, height = 576, sampleAspectRatio = sar, displayAspectRatio = dar,
    )

    @Test fun pixelsCarrésRestentInchangés() {
        assertEquals(1f, SarKeyframeGeometry.pixelWidthRatio(clip(), 2000), 0.0001f)
        val before = floatArrayOf(0.8f, -0.4f, 0.3f, 0.4f, 0.8f, -0.2f, 0f, 0f, 1f)
        assertArrayEquals(before, SarKeyframeGeometry.adjustMatrix(before, 1f), 0.00001f)
    }

    @Test fun sarDetectedWithoutForcingRawPlayback() {
        val video = clip(sar = 16f / 15f)
        assertEquals(16f / 15f, SarKeyframeGeometry.pixelWidthRatio(video, 0), 0.0001f)
        assertEquals(4f / 3f, video.displayAspectRatio()!!, 0.001f)
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertArrayEquals(identity, SarKeyframeGeometry.adjustMatrix(identity, 16f / 15f), 0.00001f)
    }

    @Test fun manualOverrideFollowsDiamondAndCanResetAuto() {
        val video = clip(16f / 15f).copy(keyframes = listOf(
            TransformKeyframe(timeMs = 1000, transform = ClipTransform(), sarOverride = 4f / 3f),
            TransformKeyframe(timeMs = 3000, transform = ClipTransform(), sarOverride = null),
        ))
        assertEquals(16f / 15f, SarKeyframeGeometry.pixelWidthRatio(video, 999), .0001f)
        assertEquals(4f / 3f, SarKeyframeGeometry.pixelWidthRatio(video, 2000), .0001f)
        assertEquals(16f / 15f, SarKeyframeGeometry.pixelWidthRatio(video, 3000), .0001f)
    }

    @Test fun rotationCoordinatesAreConjugatedWithoutChangingIdentity() {
        val rotate90 = floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val corrected = SarKeyframeGeometry.adjustMatrix(rotate90, 4f / 3f)
        assertEquals(-4f / 3f, corrected[1], .00001f)
        assertEquals(3f / 4f, corrected[3], .00001f)
        assertEquals(1f, corrected[8], .00001f)
    }

    @Test fun darManualCanOverrideSarAndInvalidMetadataIsSafe() {
        val video = clip(16f / 15f).copy(keyframes = listOf(
            TransformKeyframe(timeMs = 1000, transform = ClipTransform(), darOverride = 16f / 9f),
        ))
        assertEquals((16f / 9f) * 576f / 720f,
            SarKeyframeGeometry.pixelWidthRatio(video, 1200), .0001f)
        assertEquals(1f, SarKeyframeGeometry.pixelWidthRatio(clip(sar = Float.NaN), 0), .0001f)
    }
}
