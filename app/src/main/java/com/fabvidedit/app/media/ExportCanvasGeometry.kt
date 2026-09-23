package com.fabvidedit.app.media

import kotlin.math.roundToInt

/** Stable export texture size, independent of Media3's 16x16 gap placeholders.
 * Pure math; does not change any player/preview path or input frame transforms.
 */
internal object ExportCanvasGeometry {
    data class Canvas(val width: Int, val height: Int)

    fun resolve(
        canvasRatio: Float?,
        requestedShortSide: Int?,
        sourceShortSide: Int?,
        fallbackWidth: Int?,
        fallbackHeight: Int?,
    ): Canvas {
        val base = (requestedShortSide ?: sourceShortSide ?: 720).coerceIn(2, 4096)
        fun even(value: Int): Int = ((value.coerceIn(2, 8192) + 1) / 2) * 2
        val shortSide = even(base)
        val fallbackRatio = if ((fallbackWidth ?: 0) > 16 && (fallbackHeight ?: 0) > 16) {
            fallbackWidth!!.toFloat() / fallbackHeight!!
        } else 1f
        val ratio = canvasRatio?.takeIf { it.isFinite() && it > 0.1f && it < 10f }
            ?: fallbackRatio
        return if (ratio < 1f) {
            Canvas(shortSide, even((shortSide / ratio).roundToInt()))
        } else {
            Canvas(even((shortSide * ratio).roundToInt()), shortSide)
        }
    }
}
