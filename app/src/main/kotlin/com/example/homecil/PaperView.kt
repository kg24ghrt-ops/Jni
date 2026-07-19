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

    /** The underlying paper bitmap (may contain ink strokes). */
    var paperBitmap: Bitmap? = null
        internal set

    private val drawMatrix = Matrix()

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    private val minScale = 0.5f
    private val maxScale = 5.0f

    // --- Ink stroke management ---
    private val inkStrokes = mutableListOf<InkStroke>()
    private var basePaperBitmap: Bitmap? = null   // clean paper without any ink

    /** Current paper style; changing it triggers a redraw and clears ink. */
    var paperStyle: PaperStyle = PaperStyle.REALISTIC
        set(value) {
            if (field != value) {
                field = value
                paperBitmap = null
                inkStrokes.clear()   // ink doesn’t belong to the new style
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
            generatePaperBitmap()   // will preserve strokes now
            drawMatrix.reset()
            invalidate()
        }
    }

    /** Creates the appropriate base bitmap for the current paperStyle,
     *  then re‑applies all existing ink strokes. */
    private fun generatePaperBitmap() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        basePaperBitmap = when (paperStyle) {
            PaperStyle.REALISTIC -> PaperRenderer.createPaperBitmap(w, h)
            PaperStyle.PLAIN -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.rgb(0xF5, 0xF0, 0xE6))
                bmp
            }
            PaperStyle.LINED -> createLinedPaperBitmap(w, h)
        }

        rebuildPaperWithInk()   // put the saved ink back on top
    }

    private fun createLinedPaperBitmap(width: Int, height: Int): Bitmap {
        val base = PaperRenderer.createPaperBitmap(width, height)
        val canvas = Canvas(base)

        val linePaint = Paint().apply {
            color = Color.argb(180, 0x70, 0xA0, 0xD0)
            strokeWidth = 2f
            isAntiAlias = true
        }
        var y = 0
        while (y < height) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), linePaint)
            y += 80
        }

        val marginPaint = Paint().apply {
            color = Color.argb(200, 0xE0, 0x60, 0x60)
            strokeWidth = 4f
            isAntiAlias = true
        }
        val marginX = 120f
        canvas.drawLine(marginX, 0f, marginX, height.toFloat(), marginPaint)

        val subMarginPaint = Paint().apply {
            color = Color.argb(140, 0xE0, 0x60, 0x60)
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawLine(marginX + 20, 0f, marginX + 20, height.toFloat(), subMarginPaint)

        return base
    }

    /** Returns the Y coordinate of the nearest horizontal line (for LINED paper). */
    fun snapToLine(y: Float): Float {
        if (paperStyle != PaperStyle.LINED) return y
        val lineSpacing = 80f
        return Math.round(y / lineSpacing) * lineSpacing
    }

    /** Adds an ink stroke and stamps it onto the current paper. */
    fun addInkStroke(stroke: InkStroke) {
        inkStrokes.add(stroke)
        paperBitmap?.let {
            PaperRenderer.simulateInk(it, stroke.inkBitmap, stroke.x, stroke.y)
            invalidate()
        }
    }

    /** Removes the last ink stroke and redraws the paper. */
    fun removeLastStroke() {
        if (inkStrokes.isNotEmpty()) {
            inkStrokes.removeLast()
            rebuildPaperWithInk()
            invalidate()
        }
    }

    /** Clears all ink strokes. */
    fun clearAllInk() {
        inkStrokes.clear()
        rebuildPaperWithInk()
        invalidate()
    }

    private fun rebuildPaperWithInk() {
        basePaperBitmap?.let { base ->
            paperBitmap = base.copy(Bitmap.Config.ARGB_8888, true)
            for (stroke in inkStrokes) {
                PaperRenderer.simulateInk(paperBitmap!!, stroke.inkBitmap, stroke.x, stroke.y)
            }
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