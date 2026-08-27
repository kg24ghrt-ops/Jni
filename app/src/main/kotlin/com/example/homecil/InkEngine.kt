package com.example.homecil

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import kotlin.math.max

/**
 * Ink rendering configuration.
 *
 * The enum intentionally keeps the public API small because the actual
 * rendering implementation belongs inside InkEngine.
 */
enum class PenType(
    val label: String,
    val baseColor: Color,
    val typefaceStyle: Int
) {
    BALLPOINT(
        label = "Ballpoint",
        baseColor = Color(0xDD1A237E),
        typefaceStyle = Typeface.NORMAL
    ),

    GEL(
        label = "Gel Pen",
        baseColor = Color(0xDD000000),
        typefaceStyle = Typeface.BOLD
    ),

    FOUNTAIN(
        label = "Fountain",
        baseColor = Color(0xDD1A1A3A),
        typefaceStyle = Typeface.ITALIC
    )
}

/**
 * Centralized ink rendering engine.
 *
 * Responsibilities:
 * - Create the brush used by the Compose text renderer.
 * - Create the subtle optical shadow used to make ink feel less flat.
 * - Keep ink effects restrained enough that text remains readable.
 *
 * This class deliberately does not perform any state management.
 * NotebookCanvas owns the UI state and remembers the resulting Brush/Shadow.
 */
object InkEngine {

    /*
     * Gradient dimensions are expressed in the same coordinate space as
     * Compose drawing coordinates. The values are intentionally modest:
     * large gradients make a whole page visibly change color, which does
     * not look like real ink.
     */
    private const val GRADIENT_SIZE = 80f

    /*
     * Keep the ink mostly opaque. The original alpha values are retained
     * as the maximum opacity so existing pen colors do not change radically.
     */
    private const val DARK_ALPHA = 0.96f
    private const val MID_ALPHA = 0.90f
    private const val LIGHT_ALPHA = 0.84f

    /*
     * Very small shadow. This is an optical edge effect, not a glow.
     */
    private const val SHADOW_ALPHA = 0.18f
    private const val SHADOW_X = 0.45f
    private const val SHADOW_Y = 0.55f
    private const val SHADOW_BLUR = 1.0f

    /**
     * Creates the ink brush used by BasicTextField.
     *
     * The brush contains only a subtle luminance variation. This prevents
     * the old strong five-stop gradient from making the writing look like
     * metallic or digitally shaded text.
     *
     * The public signature is unchanged so NotebookCanvas requires no
     * modification.
     */
    fun createInkBrush(baseColor: Color): Brush {
        val normalizedColor = normalizeColor(baseColor)

        /*
         * A very small three-stop gradient is enough to introduce tiny
         * natural-looking ink variation while keeping the selected pen color
         * dominant.
         */
        val darkInk = normalizedColor.copy(
            alpha = max(
                normalizedColor.alpha,
                DARK_ALPHA
            )
        )

        val midInk = normalizedColor.copy(
            alpha = max(
                normalizedColor.alpha,
                MID_ALPHA
            )
        )

        val lightInk = normalizedColor.copy(
            alpha = max(
                normalizedColor.alpha,
                LIGHT_ALPHA
            )
        )

        return Brush.linearGradient(
            colors = listOf(
                darkInk,
                midInk,
                darkInk,
                lightInk,
                darkInk
            ),
            start = Offset(
                0f,
                0f
            ),
            end = Offset(
                GRADIENT_SIZE,
                GRADIENT_SIZE
            ),
            tileMode = TileMode.Mirror
        )
    }

    /**
     * Creates the subtle shadow used by the text renderer.
     *
     * The previous implementation used a comparatively strong alpha and
     * blur. This version keeps the effect barely visible so handwriting
     * remains crisp instead of looking blurred.
     *
     * Public signature remains unchanged.
     */
    fun getInkShadow(baseColor: Color): Shadow {
        val normalizedColor = normalizeColor(baseColor)

        return Shadow(
            color = normalizedColor.copy(
                alpha = normalizedColor.alpha *
                    SHADOW_ALPHA
            ),
            offset = Offset(
                SHADOW_X,
                SHADOW_Y
            ),
            blurRadius = SHADOW_BLUR
        )
    }

    /**
     * Ensures an invalid/fully transparent color cannot accidentally produce
     * unusable ink.
     *
     * We preserve RGB exactly and only guarantee a sensible alpha range.
     */
    private fun normalizeColor(
        color: Color
    ): Color {
        val alpha = color.alpha.coerceIn(
            0f,
            1f
        )

        /*
         * Completely transparent ink is not useful for the notebook.
         * Give it a tiny visible alpha rather than allowing the renderer
         * to effectively disappear.
         */
        val safeAlpha = max(
            alpha,
            0.01f
        )

        return color.copy(
            alpha = safeAlpha
        )
    }
}