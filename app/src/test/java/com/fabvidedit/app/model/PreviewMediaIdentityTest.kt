package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PreviewMediaIdentityTest {
    private val clip = VideoClip(
        id = "video", uri = "file:///example.mp4", name = "Sample",
        durationMs = 12_000L, trimEndMs = 8_000L,
        timelineStartMs = 2_000L, timelineTrackIndex = 1,
    )
    private val project = VideoProject(
        name = "Preview", clips = listOf(clip), timelineMode = TimelineMode.MULTITRACK,
    )

    @Test fun visualChangesMustNotReprepareOrSeekMedia() {
        val original = project.previewMediaIdentity()
        val movedVisual = project.copy(clips = listOf(clip.copy(
            transform = ClipTransform(scaleX = 3.21f, scaleY = 3.21f),
            keyframes = listOf(TransformKeyframe(
                timeMs = 3_000L, transform = ClipTransform(scaleX = 3.8f, scaleY = 3.8f))),
            brightness = 0.7f,
        )))
        assertEquals(original, movedVisual.previewMediaIdentity())
    }

    @Test fun actualMediaTimingSourceAndVisibilityMustRefresh() {
        val key = project.previewMediaIdentity()
        assertNotEquals(key, project.copy(
            clips = listOf(clip.copy(timelineStartMs = 4_000L)),
        ).previewMediaIdentity())
        assertNotEquals(key, project.copy(
            clips = listOf(clip.copy(uri = "file:///other.mp4")),
        ).previewMediaIdentity())
        assertNotEquals(key, project.copy(
            clips = listOf(clip.copy(speed = 1.5f)),
        ).previewMediaIdentity())
        assertNotEquals(key, project.copy(
            clips = listOf(clip.copy(trimEndMs = 7_000L)),
        ).previewMediaIdentity())
        assertNotEquals(key, project.copy(
            clips = listOf(clip.copy(opacity = 0f)),
        ).previewMediaIdentity())
    }
}
