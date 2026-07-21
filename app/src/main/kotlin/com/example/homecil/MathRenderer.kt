package com.example.homecil

import android.graphics.*
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
import org.scilab.forge.jlatexmath.TeXConstants
import kotlin.math.ceil

object MathRenderer {
    val INK_COLOR = HandwritingRenderer.INK_COLOR   // gel pen blue

    /**
     * Renders a LaTeX string into a realistic ink stroke.
     * @param latex     the formula, e.g. "E=mc^2" or "\frac{a}{b}"
     * @param startX    paper X position
     * @param baselineY paper baseline Y
     * @param textSize  font size (in pixels)
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

        // Render LaTeX to a TeXIcon with default style
        val formula = TeXFormula(latex)
        val icon = formula.createTeXIcon(
            TeXConstants.STYLE_DISPLAY,    // display style
            textSize
        )

        val w = icon.trueIconWidth
        val h = icon.trueIconHeight

        if (w <= 0 || h <= 0) return null

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // The icon is drawn at (0, 0) – it includes its own baseline offset
        icon.paintIcon(null, canvas, 0, 0)

        // Convert to gel‑pen colour by replacing non‑transparent pixels
        // (JLaTeXMath draws black text by default)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = bmp.getPixel(x, y)
                if (Color.alpha(pixel) > 0) {
                    bmp.setPixel(x, y, INK_COLOR)
                }
            }
        }

        // Apply human‑like distortion
        PaperRenderer.distortBitmap(bmp, 2.0f, seed)

        // The icon's baseline is (0, icon.iconHeight) in its coordinate system.
        // We need to position the top‑left of the bitmap so that the baseline aligns.
        // The icon places the baseline at (0, icon.trueIconHeight) relative to the bitmap.
        val topY = baselineY - icon.trueIconHeight

        return InkStroke("math", bmp, startX.toInt(), topY.toInt())
    }
}