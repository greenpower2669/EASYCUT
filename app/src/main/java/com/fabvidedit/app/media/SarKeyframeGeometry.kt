package com.fabvidedit.app.media

import com.fabvidedit.app.model.VideoClip
import kotlin.math.abs

/** Experimental keyframe-only correction. Media3/ExoPlayer already render the raw video.
 * Identity is preserved; the creative 3x3 matrix is expressed in display-pixel coordinates.
 * No source pixels, frame timestamps or audio are changed.
 */
internal object SarKeyframeGeometry {
    /** Geometry of display pixels relative to encoded pixels, from FFprobe or a diamond. */
    fun pixelWidthRatio(clip: VideoClip, sourceTimeMs: Long): Float {
        val selected = clip.keyframes.filter { it.timeMs <= sourceTimeMs }
            .maxByOrNull { it.timeMs }
        val (sarOverrideOrAuto, darOverrideOrAuto) = clip.aspectAtSourceTime(sourceTimeMs)
        val effectiveDar = if (selected?.darOverride != null) selected.darOverride
            else if (selected?.sarOverride != null) null // manual SAR beats stream DAR
            else darOverrideOrAuto
        val validDar = effectiveDar?.takeIf { it.isFinite() && it in 0.1f..10f }
        val fromDar = if (clip.width > 0 && clip.height > 0 && validDar != null) {
            validDar * clip.height / clip.width
        } else null
        return (fromDar ?: sarOverrideOrAuto).takeIf { it.isFinite() && it in 0.2f..5f } ?: 1f
    }

    /** C M C^-1; C=diag(pixelWidthRatio,1). No double scaling of the decoded image.
     * Identity and purely uniform scale stay untouched, rotations/pans use display geometry.
     */
    fun adjustMatrix(input: FloatArray, aspect: Float): FloatArray {
        require(input.size == 9) { "Une matrice 3x3 est nécessaire" }
        if (!aspect.isFinite() || abs(aspect - 1f) < 0.0001f || aspect !in 0.2f..5f) {
            return input.copyOf()
        }
        return input.copyOf().apply {
            this[1] = input[1] * aspect
            this[2] = input[2] * aspect
            this[3] = input[3] / aspect
            this[6] = input[6] / aspect
        }
    }
}
