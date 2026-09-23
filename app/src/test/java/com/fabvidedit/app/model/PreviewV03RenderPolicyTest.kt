package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewV03RenderPolicyTest {
    @Test fun simplePreviewOwnsTransformExactlyOnceOutsideTextureView() {
        // Fab's 0.0.3 visual reference: S=0.66 and R=-70 degrees.
        val transform = ClipTransform(
            scaleX = 0.66f, scaleY = 0.66f,
            positionX = -0.04f, positionY = -0.06f,
            rotationDegrees = -70f, pivotX = 0.15f, pivotY = -0.10f,
        )
        val plan = PreviewV03RenderPolicy.plan(simplePreview = true, visualTransform = transform)
        assertNull("No second TextureView zoom", plan.textureTransform)
        assertEquals(transform, plan.outerTransform)
    }

    @Test fun compositionPreviewNeverAddsASecondTransform() {
        val plan = PreviewV03RenderPolicy.plan(
            simplePreview = false,
            visualTransform = ClipTransform(scaleX = 3.21f, scaleY = 3.8f),
        )
        assertNull(plan.textureTransform)
        assertNull(plan.outerTransform)
    }

    @Test fun emptyIntervalHasNoPreviewTransform() {
        val plan = PreviewV03RenderPolicy.plan(simplePreview = true, visualTransform = null)
        assertNull(plan.textureTransform)
        assertNull(plan.outerTransform)
    }
}
