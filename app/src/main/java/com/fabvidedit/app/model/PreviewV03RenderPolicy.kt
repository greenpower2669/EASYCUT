package com.fabvidedit.app.model

/**
 * EASYCUT 0.0.3 preview contract: the single preview host owns the user's
 * transform. The TextureView matrix stays identity; export has its own
 * independently corrected Media3 pipeline and must never inherit this view zoom.
 */
internal data class PreviewRenderPlan(
    val textureTransform: ClipTransform?,
    val outerTransform: ClipTransform?,
)

internal object PreviewV03RenderPolicy {
    fun plan(simplePreview: Boolean, visualTransform: ClipTransform?): PreviewRenderPlan =
        PreviewRenderPlan(
            textureTransform = null,
            outerTransform = visualTransform.takeIf { simplePreview },
        )
}
