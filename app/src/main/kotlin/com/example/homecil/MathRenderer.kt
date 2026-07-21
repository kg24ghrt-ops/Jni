package com.example.homecil

import android.graphics.*
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXConstants
import kotlin.math.ceil

object MathRenderer {
    val INK_COLOR = HandwritingRenderer.INK_COLOR

    /**
     * Renders a LaTeX string into a realistic ink stroke.
     * @param latex     the formula, e.g. "E=mc^2"
     * @param startX    paper X position
     * @param baselineY paper baseline Y
     * @param textSize  font size in pixels (Float)
     * @param seed      unique seed for distortion
     * @return InkStroke ready to stamp
     */
    fun createMathStroke(
        latex: String,
        startX: Float,
        baselineY: Float,
        textSize: Float,
        seed: Int
    ): InkStroke? {
        if (latex.isEmpty()) return null

        val formula = TeXFormula(latex)
        val icon = formula.createTeXIcon(
            TeXConstants.STYLE_DISPLAY,
            textSize
        )

        val w = icon.trueIconWidth   // Float
        val h = icon.trueIconHeight  // Float

        if (w <= 0f || h <= 0f) return null

        val bmpWidth = ceil(w).toInt()
        val bmpHeight = ceil(h).toInt()

        val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Call the Android‑specific paintIcon(Canvas, int, int)
        icon.paintIcon(canvas, 0, 0)

        // Replace black pixels with gel‑pen blue
        for (y in 0 until bmpHeight) {
            for (x in 0 until bmpWidth) {
                val pixel = bmp.getPixel(x, y)
                if (Color.alpha(pixel) > 0) {
                    bmp.setPixel(x, y, INK_COLOR)
                }
            }
        }

        // Apply human‑like distortion
        PaperRenderer.distortBitmap(bmp, 2.0f, seed)

        // The icon's baseline is at its bottom edge
        val topY = baselineY - h
        return InkStroke("math", bmp, startX.toInt(), topY.toInt())
    }
}