package com.example.homecil

import android.graphics.*
import android.text.TextPaint

object HandwritingRenderer {
    /** Gel pen blue colour */
    val INK_COLOR = Color.rgb(0x1A, 0x4D, 0x8C)

    /**
     * Renders the given character onto the paper bitmap as realistic ink,
     * with human‑like imperfections.
     * Returns the advance width so the cursor can be moved.
     */
    fun stampCharacter(
        paperBitmap: Bitmap,
        char: String,
        x: Float,
        y: Float,
        paint: Paint
    ): Float {
        if (char.isEmpty()) return 0f

        // Measure the character
        val width = paint.measureText(char)
        val fm = paint.fontMetrics
        val height = (fm.descent - fm.ascent).toInt() + 2
        val charWidth = width.toInt() + 2

        if (charWidth <= 0 || height <= 0) return width

        // Create a small bitmap for the character
        val inkBitmap = Bitmap.createBitmap(charWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inkBitmap)

        // Draw the character in the gel pen colour
        val textPaint = TextPaint(paint).apply {
            this.color = INK_COLOR
            isAntiAlias = true
        }
        canvas.drawText(char, 0f, -fm.ascent, textPaint)

        // 🔥 New: Apply human‑like distortion to kill perfect shapes
        PaperRenderer.distortBitmap(inkBitmap, 2.0f)   // strength ~2 pixels

        // Stamp it onto the paper
        PaperRenderer.simulateInk(paperBitmap, inkBitmap, (x).toInt(), (y + fm.ascent).toInt())

        inkBitmap.recycle()
        return width
    }
}