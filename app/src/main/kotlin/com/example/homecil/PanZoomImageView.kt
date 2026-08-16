package com.example.homecil

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class PanZoomImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private var mode = NONE
    private val start = PointF()
    private var orig = FloatArray(9)
    private var minScale = 1f
    private var maxScale = 5f
    private val mScaleDetector = ScaleGestureDetector(context, ScaleListener())

    init { scaleType = ScaleType.MATRIX }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(event)
        matrix.getValues(orig)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { start.set(event.x, event.y); mode = DRAG }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    matrix.postTranslate(event.x - start.x, event.y - start.y)
                    start.set(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> { mode = NONE }
        }
        imageMatrix = matrix
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var scaleFactor = detector.scaleFactor
            val currentScale = orig[Matrix.MSCALE_X]
            val newScale = currentScale * scaleFactor
            if (newScale > maxScale) scaleFactor = maxScale / currentScale
            else if (newScale < minScale) scaleFactor = minScale / currentScale
            matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    }

    companion object { const val NONE = 0; const val DRAG = 1 }
}