package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectMathTest {
    @Test
    fun durationAccountsForTrimAndSpeed() {
        val project = VideoProject(
            name = "Test",
            clips = listOf(
                VideoClip(uri = "a", name = "A", durationMs = 10_000, trimStartMs = 1_000, trimEndMs = 9_000),
                VideoClip(uri = "b", name = "B", durationMs = 8_000, trimStartMs = 0, trimEndMs = 8_000, speed = 2f),
            ),
        )

        assertEquals(12_000, project.durationMs)
        assertEquals(8_000, project.clipStartMs(1))
    }

    @Test
    fun keyframesInterpolateTransformWithEasing() {
        val clip = VideoClip(
            uri = "a",
            name = "A",
            durationMs = 10_000,
            keyframes = listOf(
                TransformKeyframe(
                    timeMs = 10_000,
                    transform = ClipTransform(scaleX = 2f, scaleY = 2f, positionX = 1f),
                    easing = MotionEasing.LINEAR,
                ),
            ),
        )

        val middle = clip.transformAtSourceTime(5_000)
        assertEquals(1.5f, middle.scaleX, 0.001f)
        assertEquals(0.5f, middle.positionX, 0.001f)
    }

    @Test
    fun oneDiamondInterpolatesTransformBrightnessAndVolumeTogether() {
        val clip = VideoClip(
            uri = "a",
            name = "A",
            durationMs = 10_000,
            brightness = 1f,
            volume = 1f,
            keyframes = listOf(
                TransformKeyframe(
                    timeMs = 10_000,
                    transform = ClipTransform(scaleX = 2f, scaleY = 2f),
                    brightness = 0.5f,
                    volume = 0f,
                    easing = MotionEasing.LINEAR,
                ),
            ),
        )

        assertEquals(1.5f, clip.transformAtSourceTime(5_000).scaleX, 0.001f)
        assertEquals(0.75f, clip.brightnessAtSourceTime(5_000), 0.001f)
        assertEquals(0.5f, clip.volumeAtSourceTime(5_000), 0.001f)
    }

    @Test
    fun audioTrianglesInterpolateVolumeOnProjectTimeline() {
        val track = AudioTrack(
            uri = "music",
            name = "Music",
            durationMs = 4_000,
            volume = 1f,
            keyframes = listOf(
                AudioKeyframe(
                    timeMs = 10_000,
                    volume = 0f,
                    easing = MotionEasing.LINEAR,
                ),
            ),
        )

        assertEquals(0.5f, track.volumeAtProjectTime(5_000), 0.001f)
        assertEquals(0f, track.volumeAtProjectTime(12_000), 0.001f)
    }

    @Test
    fun projectAndSourceTimesAccountForClipSpeed() {
        val project = VideoProject(
            name = "Test",
            clips = listOf(
                VideoClip(uri = "a", name = "A", durationMs = 10_000, speed = 2f),
            ),
        )

        assertEquals(5_000, project.durationMs)
        assertEquals(4_000, project.sourceTimeForProjectPosition(0, 2_000))
        assertEquals(2_000, project.projectPositionForSourceTime(0, 4_000))
    }

    @Test
    fun invalidTransitionsAreRemovedAfterReordering() {
        val first = VideoClip(uri = "a", name = "A", durationMs = 1_000)
        val second = VideoClip(uri = "b", name = "B", durationMs = 1_000)
        val third = VideoClip(uri = "c", name = "C", durationMs = 1_000)
        val project = VideoProject(
            name = "Test",
            clips = listOf(first, second, third),
            transitions = listOf(
                ClipTransition(fromClipId = first.id, toClipId = second.id),
                ClipTransition(fromClipId = first.id, toClipId = third.id),
            ),
        ).withValidTransitions()

        assertEquals(1, project.transitions.size)
        assertTrue(project.transitions.single().toClipId == second.id)
    }
}
