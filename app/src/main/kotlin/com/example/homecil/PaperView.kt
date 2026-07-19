package com.example.homecil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class PaperView : View {

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private var paperBitmap: Bitmap? = null
    private val drawMatrix = Matrix()

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    private val minScale = 0.5f
    private val maxScale = 5.0f

    private fun init() {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
            paperBitmap = PaperRenderer.createPaperBitmap(w, h)
            drawMatrix.reset()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paperBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, drawMatrix, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val values = FloatArray(9)
            drawMatrix.getValues(values)
            val currentScale = values[Matrix.MSCALE_X]
            var newScale = currentScale * detector.scaleFactor
            newScale = newScale.coerceIn(minScale, maxScale)
            val adjustedFactor = newScale / currentScale
            drawMatrix.postScale(adjustedFactor, adjustedFactor, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            drawMatrix.postTranslate(-distanceX, -distanceY)
            invalidate()
            return true
        }
    }
}