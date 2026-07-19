package com.example.homecil

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

enum class PaperStyle {
    REALISTIC,  // original JNI texture
    PLAIN,      // clean off-white, no lines
    LINED       // school notebook with realistic texture + lines
}

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

    /** Current paper style; changing it triggers a redraw. */
    var paperStyle: PaperStyle = PaperStyle.REALISTIC
        set(value) {
            if (field != value) {
                field = value
                paperBitmap = null
                if (width > 0 && height > 0) {
                    generatePaperBitmap()
                }
                invalidate()
            }
        }

    private fun init() {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != oldw || h != oldh || paperBitmap == null)) {
            generatePaperBitmap()
            drawMatrix.reset()
            invalidate()
        }
    }

    /** Creates the appropriate bitmap for the current paperStyle. */
    private fun generatePaperBitmap() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        paperBitmap = when (paperStyle) {
            PaperStyle.REALISTIC -> PaperRenderer.createPaperBitmap(w, h)
            PaperStyle.PLAIN -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.rgb(0xF5, 0xF0, 0xE6))   // warm white
                bmp
            }
            PaperStyle.LINED -> createLinedPaperBitmap(w, h)
        }
    }

    /**
     * Generates a school notebook‑style bitmap using the realistic JNI texture
     * as a base, then draws blue lines and a red margin.
     */
    private fun createLinedPaperBitmap(width: Int, height: Int): Bitmap {
        // 1. Get the realistic paper texture from the native library
        val base = PaperRenderer.createPaperBitmap(width, height)

        // 2. Create a Canvas to draw the notebook lines on top
        val canvas = Canvas(base)

        // 3. Horizontal blue lines every 80 pixels
        val linePaint = Paint().apply {
            color = Color.argb(180, 0x70, 0xA0, 0xD0)  // semi‑transparent soft blue
            strokeWidth = 2f
            isAntiAlias = true
        }
        var y = 0
        while (y < height) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), linePaint)
            y += 80
        }

        // 4. Red margin line at 120 px from left
        val marginPaint = Paint().apply {
            color = Color.argb(200, 0xE0, 0x60, 0x60)  // soft red, slightly transparent
            strokeWidth = 4f
            isAntiAlias = true
        }
        val marginX = 120f
        canvas.drawLine(marginX, 0f, marginX, height.toFloat(), marginPaint)

        // 5. Optional thin red line 20px right of the margin
        val subMarginPaint = Paint().apply {
            color = Color.argb(140, 0xE0, 0x60, 0x60)
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawLine(marginX + 20, 0f, marginX + 20, height.toFloat(), subMarginPaint)

        return base   // now contains texture + lines
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