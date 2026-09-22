package com.fabvidedit.app.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure touch-overlay math (pixel coordinates in the UNSCALED output frame).
 * ClipTransform uses +Y up, screen pixels use +Y down. Rotation is clockwise
 * on Android; Media3's sign conversion remains at its existing export boundary.
 *
 * Keep the source point under the previous pinch centroid under zoom AND
 * intentional rotation, then add finger pan. Works with off-centre pivots and
 * non-uniform old scales. Saturation is based on the ACTUAL clamped transform.
 */
internal object PreviewGestureMath {
    fun panZoom(
        old: ClipTransform,
        frameWidthPx: Float,
        frameHeightPx: Float,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        rotationDeltaDegrees: Float = 0f,
    ): ClipTransform {
        if (frameWidthPx <= 0f || frameHeightPx <= 0f ||
            !frameWidthPx.isFinite() || !frameHeightPx.isFinite() ||
            !centroidX.isFinite() || !centroidY.isFinite() ||
            !zoom.isFinite() || !rotationDeltaDegrees.isFinite() ||
            !panX.isFinite() || !panY.isFinite()
        ) return old

        val requestedZoom = zoom.coerceIn(0.1f, 10f)
        val nextScaleX = (old.scaleX * requestedZoom).coerceIn(0.25f, 4f)
        val nextScaleY = (old.scaleY * requestedZoom).coerceIn(0.25f, 4f)
        val nextRotation = (old.rotationDegrees + rotationDeltaDegrees).coerceIn(-720f, 720f)

        val pivotXpx = (old.pivotX + 1f) * frameWidthPx / 2f
        val pivotYpx = (1f - old.pivotY) * frameHeightPx / 2f
        val oldTx = old.positionX * frameWidthPx / 2f
        val oldTy = -old.positionY * frameHeightPx / 2f

        val oldAngle = old.rotationDegrees * PI / 180.0
        val oldCos = cos(oldAngle).toFloat()
        val oldSin = sin(oldAngle).toFloat()
        val nextAngle = nextRotation * PI / 180.0
        val nextCos = cos(nextAngle).toFloat()
        val nextSin = sin(nextAngle).toFloat()

        // Invert the existing R*S at the old touch centroid to identify the
        // underlying untransformed point relative to the clip pivot.
        val sx = centroidX - pivotXpx - oldTx
        val sy = centroidY - pivotYpx - oldTy
        val localX = (oldCos * sx + oldSin * sy) / old.scaleX.coerceAtLeast(0.01f)
        val localY = (-oldSin * sx + oldCos * sy) / old.scaleY.coerceAtLeast(0.01f)

        // Project that SAME local point with the new rotation and zoom;
        // translate to the new centroid (old centroid + pan) without jumping.
        val scaledX = localX * nextScaleX
        val scaledY = localY * nextScaleY
        val rotatedX = nextCos * scaledX - nextSin * scaledY
        val rotatedY = nextSin * scaledX + nextCos * scaledY
        val nextTx = centroidX + panX - pivotXpx - rotatedX
        val nextTy = centroidY + panY - pivotYpx - rotatedY

        return old.copy(
            scaleX = nextScaleX,
            scaleY = nextScaleY,
            positionX = nextTx * 2f / frameWidthPx,
            positionY = -nextTy * 2f / frameHeightPx,
            rotationDegrees = nextRotation,
        ).normalized()
    }
}
