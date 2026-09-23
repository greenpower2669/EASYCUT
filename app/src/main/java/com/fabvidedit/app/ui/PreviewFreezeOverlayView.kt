package com.fabvidedit.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.View
import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.PreviewCanvasGeometry

/**
 * Last-presented-frame bridge between Media3 composition and a decoder fallback.
 * The bridge owns one bounded bitmap; it never copies GPU pixels per pointer event.
 */
internal class PreviewFreezeOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var picture: Bitmap? = null
    private var initial = ClipTransform()
    private var current = ClipTransform()
    private var canvasRatio = 1f
    private var sourceRatio = 1f
    private var outputSnapshot = false
    private var qualityEdge = 0
    var awaitingFirstFrame = false
        private set

    init { visibility = GONE }

    fun beginWait(snapshot: Bitmap?, initialTransform: ClipTransform, ratio: Float) {
        picture = snapshot
        initial = initialTransform
        current = initialTransform
        canvasRatio = ratio
        sourceRatio = ratio
        outputSnapshot = snapshot != null
        qualityEdge = 0
        awaitingFirstFrame = true
        visibility = if (snapshot == null) GONE else VISIBLE
        invalidate()
    }

    fun offerRecoveredFrame(frame: Bitmap, edge: Int, sourceAspectRatio: Float) {
        if (!awaitingFirstFrame || outputSnapshot || edge <= qualityEdge) return
        picture = frame
        qualityEdge = edge
        sourceRatio = sourceAspectRatio
        visibility = VISIBLE
        invalidate()
    }

    fun updateTransform(transform: ClipTransform) {
        current = transform
        if (picture != null) invalidate()
    }

    fun clearFrame() {
        picture = null
        qualityEdge = 0
        awaitingFirstFrame = false
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = picture ?: return
        if (width == 0 || height == 0 || bitmap.isRecycled) return
        val bitmapToScreen = Matrix().apply {
            setScale(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        }
        val render = if (outputSnapshot) {
            // The captured bitmap already contains the last composition transform.
            // Apply only the gesture delta, otherwise the initial zoom doubles.
            val start = canvasMatrix(initial, canvasRatio)
            val inverse = Matrix()
            if (!start.invert(inverse)) return
            val delta = Matrix()
            delta.setConcat(canvasMatrix(current, canvasRatio), inverse)
            Matrix().apply { setConcat(delta, bitmapToScreen) }
        } else {
            // A recovered source frame is raw: apply FIT and the complete gesture.
            Matrix().apply {
                setConcat(canvasMatrix(current, sourceRatio), bitmapToScreen)
            }
        }
        canvas.drawBitmap(bitmap, render, paint)
    }

    private fun canvasMatrix(transform: ClipTransform, sourceAspect: Float): Matrix {
        val f = PreviewCanvasGeometry.forFrame(
            transform, width.toFloat(), height.toFloat(), sourceAspect, canvasRatio,
        )
        return Matrix().apply {
            setScale(f.fitX, f.fitY, f.centerX, f.centerY)
            postScale(f.scaleX, f.scaleY, f.pivotX, f.pivotY)
            postRotate(f.rotationDegrees, f.pivotX, f.pivotY)
            postTranslate(f.translationX, f.translationY)
        }
    }
}
