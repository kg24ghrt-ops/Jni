package com.example.homecil

import android.graphics.Bitmap
import android.graphics.Color
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXConstants
import java.awt.image.BufferedImage
import kotlin.math.ceil

object MathRenderer {
    val INK_COLOR = HandwritingRenderer.INK_COLOR

    /**
     * Renders a LaTeX string into a realistic ink stroke.
     *
     * @param latex     the formula, e.g. "E=mc^2" or "\frac{a}{b}"
     * @param startX    paper X position
     * @param baselineY paper baseline Y
     * @param textSize  font size in pixels
     * @param seed      unique seed for distortion
     * @return InkStroke ready to stamp, or null if the string is empty / unreadable
     */
    fun createMathStroke(
        latex: String,
        startX: Float,
        baselineY: Float,
        textSize: Float,
        seed: Int
    ): InkStroke? {
        if (latex.isEmpty()) return null

        // 1. Render LaTeX to an AWT BufferedImage
        val formula = TeXFormula(latex)
        val awtImage: BufferedImage = formula.createBufferedImage(
            TeXConstants.STYLE_DISPLAY,
            textSize,
            java.awt.Color.BLACK,   // foreground – we'll recolor later
            null                     // transparent background
        )

        val w = awtImage.width
        val h = awtImage.height
        if (w <= 0 || h <= 0) return null

        // 2. Convert AWT BufferedImage to Android Bitmap
        val pixels = IntArray(w * h)
        awtImage.getRGB(0, 0, w, h, pixels, 0, w)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)

        // 3. Replace black with gel‑pen blue (non‑transparent pixels)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = bmp.getPixel(x, y)
                if (Color.alpha(pixel) > 0) {
                    bmp.setPixel(x, y, INK_COLOR)
                }
            }
        }

        // 4. Apply human‑like distortion
        PaperRenderer.distortBitmap(bmp, 2.0f, seed)

        // 5. Position the bitmap on the paper
        // Baseline for the formula is at the bottom of the image
        val topY = baselineY - h.toFloat()
        return InkStroke("math", bmp, startX.toInt(), topY.toInt())
    }
}