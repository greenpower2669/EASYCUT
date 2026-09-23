package com.fabvidedit.app.model

/** Shared canvas-space mapping for the native TextureView fallback.
 *
 * Media3 Presentation.LAYOUT_SCALE_TO_FIT fits the source within the project
 * canvas BEFORE keyframe transforms. ExoPlayer's raw TextureView otherwise fills
 * the view, so composing a square with a portrait video uses a different basis.
 *
 * Dimensions/translation are in unscaled view pixels; no source URI or project
 * setting is mutated. A transformed clip remains at its exact saved scale.
 */
internal object PreviewCanvasGeometry {
    data class Frame(
        val fitX: Float,
        val fitY: Float,
        val centerX: Float,
        val centerY: Float,
        val pivotX: Float,
        val pivotY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val rotationDegrees: Float,
        val translationX: Float,
        val translationY: Float,
    )

    fun forFrame(
        transform: ClipTransform,
        widthPx: Float,
        heightPx: Float,
        sourceAspectRatio: Float,
        canvasAspectRatio: Float,
    ): Frame {
        require(widthPx > 0f && heightPx > 0f && widthPx.isFinite() && heightPx.isFinite())
        val input = sourceAspectRatio.takeIf { it.isFinite() && it > 0f }
            ?: canvasAspectRatio
        val canvas = canvasAspectRatio.takeIf { it.isFinite() && it > 0f } ?: input
        val normalized = transform.normalized()
        val fitX = (input / canvas).coerceAtMost(1f)
        val fitY = (canvas / input).coerceAtMost(1f)
        return Frame(
            fitX = fitX, fitY = fitY,
            centerX = widthPx * 0.5f, centerY = heightPx * 0.5f,
            pivotX = (normalized.pivotX + 1f) * widthPx * 0.5f,
            pivotY = (1f - normalized.pivotY) * heightPx * 0.5f,
            scaleX = normalized.scaleX, scaleY = normalized.scaleY,
            rotationDegrees = normalized.rotationDegrees,
            translationX = normalized.positionX * widthPx * 0.5f,
            translationY = -normalized.positionY * heightPx * 0.5f,
        )
    }
}
