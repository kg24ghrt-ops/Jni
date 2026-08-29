package com.example.homecil

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import com.example.homecil.native.PaperEngineNative
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
        typefaceStyle = android.graphics.Typeface.NORMAL
    ),

    GEL(
        label = "Gel Pen",
        baseColor = Color(0xDD000000),
        typefaceStyle = android.graphics.Typeface.BOLD
    ),

    FOUNTAIN(
        label = "Fountain",
        baseColor = Color(0xDD1A1A3A),
        typefaceStyle = android.graphics.Typeface.ITALIC
    )
}

/**
 * Centralized ink rendering engine.
 *
 * Responsibilities:
 * - Create the brush used by the Compose text renderer.
 * - Create the subtle optical shadow used to make ink feel less flat.
 * - Keep ink effects restrained enough that text remains readable.
 * - Bridge to native C++ ink simulation functions.
 *
 * The engine is stateless and safe to reuse across recompositions.
 */
object InkEngine {

    /**
     * Use native C++ engine for ink simulation.
     */
    var useNativeEngine: Boolean = true

    /**
     * Size of the subtle ink-variation gradient.
     *
     * A small gradient prevents large portions of a page from visibly
     * changing color while still avoiding completely flat digital ink.
     */
    private const val GRADIENT_SIZE = 80f

    /**
     * Maximum opacity used by the internal gradient stops.
     *
     * These values are deliberately below full opacity so the selected
     * pen color remains visually natural.
     */
    private const val DARK_ALPHA = 0.96f
    private const val MID_ALPHA = 0.90f
    private const val LIGHT_ALPHA = 0.84f

    /**
     * Optical shadow configuration.
     *
     * The shadow is intentionally extremely subtle. It should help the
     * ink feel printed onto paper rather than create a glow or blur.
     */
    private const val SHADOW_ALPHA = 0.18f
    private const val SHADOW_X = 0.45f
    private const val SHADOW_Y = 0.55f
    private const val SHADOW_BLUR = 1.0f

    /**
     * Creates the ink brush used by the notebook text renderer.
     *
     * The public signature remains unchanged so callers do not need
     * additional state or migration.
     */
    fun createInkBrush(
        baseColor: Color
    ): Brush {
        val normalizedColor = normalizeColor(baseColor)

        /*
         * Preserve the caller's color while applying only the minimum
         * opacity needed by the visual ink model.
         *
         * max() is safe here because normalizeColor() guarantees that
         * alpha is finite and within the [0, 1] range.
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
            start = androidx.compose.ui.geometry.Offset(
                0f,
                0f
            ),
            end = androidx.compose.ui.geometry.Offset(
                GRADIENT_SIZE,
                GRADIENT_SIZE
            ),
            tileMode = TileMode.Mirror
        )
    }

    /**
     * Creates the subtle shadow used by the text renderer.
     *
     * The shadow follows the selected ink color and scales its opacity
     * from that color's alpha. This keeps partially transparent ink from
     * unexpectedly producing an opaque shadow.
     */
    fun getInkShadow(
        baseColor: Color
    ): Shadow {
        val normalizedColor = normalizeColor(baseColor)

        return Shadow(
            color = normalizedColor.copy(
                alpha = (
                    normalizedColor.alpha *
                        SHADOW_ALPHA
                    ).coerceIn(
                        0f,
                        1f
                    )
            ),
            offset = androidx.compose.ui.geometry.Offset(
                SHADOW_X,
                SHADOW_Y
            ),
            blurRadius = SHADOW_BLUR
        )
    }

    /**
     * Apply native ink simulation to a bitmap.
     */
    fun applyInkToBitmap(
        paperBitmap: Bitmap,
        inkBitmap: Bitmap,
        x: Int,
        y: Int,
        inkColor: Color,
        absorption: Float = 0.3f,
        noiseIntensity: Float = 0.1f,
        seed: Int = 0
    ) {
        if (useNativeEngine) {
            PaperEngineNative.simulateInk(
                bitmap = paperBitmap,
                inkBitmap = inkBitmap,
                x = x,
                y = y,
                inkColorR = inkColor.red / 255.0f,
                inkColorG = inkColor.green / 255.0f,
                inkColorB = inkColor.blue / 255.0f,
                absorption = absorption,
                noiseIntensity = noiseIntensity,
                seed = seed
            )
        }
    }

    /**
     * Apply native ink simulation with simple color stamping.
     */
    fun applyInkSimple(
        paperBitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        inkColor: Color,
        opacity: Float = 1.0f
    ) {
        if (useNativeEngine) {
            PaperEngineNative.simulateInkSimple(
                bitmap = paperBitmap,
                x = x,
                y = y,
                width = width,
                height = height,
                inkColorR = inkColor.red / 255.0f,
                inkColorG = inkColor.green / 255.0f,
                inkColorB = inkColor.blue / 255.0f,
                opacity = opacity
            )
        }
    }

    /**
     * Sanitizes a Compose Color before it reaches the renderer.
     *
     * Compose Color normally guarantees valid components, but keeping this
     * boundary defensive prevents unusual values from propagating into
     * brush/shadow creation.
     *
     * RGB components are intentionally untouched.
     */
    private fun normalizeColor(
        color: Color
    ): Color {
        val alpha = color.alpha

        /*
         * Defensive handling for malformed/non-finite alpha values.
         *
         * A transparent color is allowed to remain transparent here.
         * createInkBrush() will apply its existing visual opacity policy,
         * while getInkShadow() will remain effectively invisible.
         */
        val safeAlpha = when {
            !alpha.isFinite() -> 1f
            else -> alpha.coerceIn(
                0f,
                1f
            )
        }

        return color.copy(
            alpha = safeAlpha
        )
    }
}
