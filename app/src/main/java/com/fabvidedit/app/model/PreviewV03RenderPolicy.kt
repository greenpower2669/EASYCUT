package com.fabvidedit.app.model

/**
 * EASYCUT 0.0.3 preview contract: the single preview host owns the user's
 * creative transform. For raw ExoPlayer only, the TextureView matrix fits the
 * untransformed source proportionally into the project canvas. The outer Compose
 * layer still owns user zoom/rotation exactly once; Media3 composition already fits.
 */
internal data class PreviewRenderPlan(
    val textureTransform: ClipTransform?,
    val outerTransform: ClipTransform?,
    val fitRawSourceToCanvas: Boolean,
)

internal object PreviewV03RenderPolicy {
    fun plan(simplePreview: Boolean, visualTransform: ClipTransform?): PreviewRenderPlan =
        PreviewRenderPlan(
            textureTransform = null,
            outerTransform = visualTransform.takeIf { simplePreview },
            fitRawSourceToCanvas = simplePreview,
        )
}
