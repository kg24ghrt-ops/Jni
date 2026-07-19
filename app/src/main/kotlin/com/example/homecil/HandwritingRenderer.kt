package com.example.homecil

import android.graphics.*
import android.text.TextPaint

object HandwritingRenderer {
    /** Gel pen blue colour */
    val INK_COLOR = Color.rgb(0x1A, 0x4D, 0x8C)

    /**
     * Creates an ink bitmap for the given character with realistic distortion.
     * Returns the [InkStroke] ready to be stamped (does not stamp here).
     */
    fun createInkStroke(
        char: String,
        x: Float,
        y: Float,        // baseline y
        paint: Paint
    ): InkStroke? {
        if (char.isEmpty()) return null

        val width = paint.measureText(char)
        val fm = paint.fontMetrics
        val height = (fm.descent - fm.ascent).toInt() + 2
        val charWidth = width.toInt() + 2
        if (charWidth <= 0 || height <= 0) return null

        val inkBitmap = Bitmap.createBitmap(charWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inkBitmap)

        val textPaint = TextPaint(paint).apply {
            color = INK_COLOR
            isAntiAlias = true
        }
        canvas.drawText(char, 0f, -fm.ascent, textPaint)

        // Apply human‑like distortion
        PaperRenderer.distortBitmap(inkBitmap, 2.0f)

        // The y passed is the baseline, so we place the bitmap so that its baseline aligns
        val bitmapTop = y + fm.ascent   // fm.ascent is negative
        return InkStroke(char, inkBitmap, x.toInt(), bitmapTop.toInt())
    }
}