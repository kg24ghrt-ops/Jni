package com.example.homecil

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.scilab.forge.jlatexmath.AndroidGraphics2D
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
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

        // 1. Build the TeX icon with the correct style and size
        val formula = TeXFormula(latex)
        val icon: TeXIcon = formula.new TeXIconBuilder()
            .setStyle(TeXConstants.STYLE_DISPLAY)
            .setSize(textSize)
            .build()

        val w = icon.iconWidth   // these are floats
        val h = icon.iconHeight
        if (w <= 0f || h <= 0f) return null

        val bmpWidth = ceil(w).toInt()
        val bmpHeight = ceil(h).toInt()

        // 2. Render the icon onto an Android bitmap via AndroidGraphics2D
        val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val g2d = AndroidGraphics2D(canvas)
        // paintIcon with null Component (safe on Android) – background is transparent
        icon.paintIcon(null, g2d, 0, 0)

        // 3. Replace black (the default foreground) with gel‑pen blue
        for (y in 0 until bmpHeight) {
            for (x in 0 until bmpWidth) {
                val pixel = bmp.getPixel(x, y)
                if (Color.alpha(pixel) > 0) {
                    bmp.setPixel(x, y, INK_COLOR)
                }
            }
        }

        // 4. Apply human‑like distortion
        PaperRenderer.distortBitmap(bmp, 2.0f, seed)

        // 5. Position: baseline at the bottom of the icon
        val topY = baselineY - h
        return InkStroke("math", bmp, startX.toInt(), topY.toInt())
    }
}