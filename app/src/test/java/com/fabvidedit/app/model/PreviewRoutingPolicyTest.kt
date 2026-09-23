package com.fabvidedit.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewRoutingPolicyTest {
    private fun video() = VideoClip(uri = "file:///video.mp4", name = "Vidéo", durationMs = 10_000L)
    private fun project(clip: VideoClip = video()) =
        VideoProject(name = "Test", clips = listOf(clip))

    @Test fun singleVideoStartsOnSimpleReaderWithoutFirstPinchHandoff() {
        val p = project()
        assertTrue(PreviewRoutingPolicy.singleVideoEligible(p))
        assertTrue(PreviewRoutingPolicy.initialSimple(p, unequalVideoSpans = false))
    }

    @Test fun stillImageAndCompositedContentKeepCompositionPath() {
        assertFalse(PreviewRoutingPolicy.singleVideoEligible(
            project(video().copy(mediaKind = VisualMediaKind.IMAGE))))
        assertFalse(PreviewRoutingPolicy.singleVideoEligible(
            project(video().copy(filter = ClipFilter.WARM))))
        assertFalse(PreviewRoutingPolicy.singleVideoEligible(
            project(video().copy(brightness = 1.2f))))
        assertFalse(PreviewRoutingPolicy.singleVideoEligible(
            project(video().copy(keyframes = listOf(
                TransformKeyframe(timeMs = 500L, transform = ClipTransform(), brightness = 1.3f),
            )))))
        assertFalse(PreviewRoutingPolicy.singleVideoEligible(
            project().copy(textLayers = listOf(TextLayer(text = "Texte", startMs = 0L, endMs = 2000L)))))
        assertFalse(PreviewRoutingPolicy.singleVideoEligible(
            project().copy(clips = listOf(video(), video().copy(id = "second")))))
    }

    @Test fun transitionsFromOneVideoToMultipisteWithoutResettingExistingFallback() {
        assertFalse(PreviewRoutingPolicy.afterRoutingChange(
            currentSimple = true, wasSingleEligible = true,
            isSingleEligible = false, unequalVideoSpans = false))
        assertTrue(PreviewRoutingPolicy.afterRoutingChange(
            currentSimple = true, wasSingleEligible = false,
            isSingleEligible = false, unequalVideoSpans = false))
        assertTrue(PreviewRoutingPolicy.afterRoutingChange(
            currentSimple = false, wasSingleEligible = false,
            isSingleEligible = true, unequalVideoSpans = false))
        assertTrue(PreviewRoutingPolicy.afterRoutingChange(
            currentSimple = false, wasSingleEligible = true,
            isSingleEligible = false, unequalVideoSpans = true))
    }
}
