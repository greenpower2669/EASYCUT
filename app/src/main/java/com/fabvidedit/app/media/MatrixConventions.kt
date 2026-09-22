package com.fabvidedit.app.media

/**
 * Shared coordinate conventions for clip keyframe matrices.
 *
 * FabVidEdit's user-facing model follows the editor canvas:
 * - +X moves right;
 * - +Y moves up;
 * - positive rotation is clockwise, matching Compose gestures/graphicsLayer.
 *
 * Media3 MatrixTransformation uses GL normalized-device coordinates and its positive rotation
 * direction is counter-clockwise, so model rotations must be negated at that boundary.
 */
internal object MatrixConventions {
    fun modelRotationToMedia3Degrees(modelDegrees: Float): Float = -modelDegrees
}
