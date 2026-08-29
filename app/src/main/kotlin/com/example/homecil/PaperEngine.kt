package com.example.homecil

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.homecil.native.PaperEngineNative
import java.util.Random
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class PaperSize(
    val widthDp: Dp,
    val heightDp: Dp,
    val label: String,
    val exportW: Int,
    val exportH: Int
) {
    A4(
        widthDp = 1323.dp,
        heightDp = 1871.dp,
        label = "A4",
        exportW = 2480,
        exportH = 3508
    ),

    A5(
        widthDp = 932.dp,
        heightDp = 1323.dp,
        label = "A5",
        exportW = 1748,
        exportH = 2480
    )
}

/**
 * Procedural paper renderer.
 *
 * This object provides two rendering paths:
 * 1. Native C++ rendering via PaperEngineNative (fast, realistic)
 * 2. Pure Kotlin rendering (fallback, compatible)
 *
 * The texture is composed from multiple weak spatial-frequency layers:
 *
 * 1. Large-scale formation variation
 * 2. Medium-scale density variation
 * 3. Fine stochastic grain
 * 4. Sparse cellulose-like fibers
 * 5. Extremely subtle micro-specks
 *
 * The objective is realistic paper variation without making the background
 * look obviously procedural or noisy.
 *
 * Generation is deterministic for a given paper size.
 */
object PaperEngine {

    /**
     * Maximum procedural source texture dimension.
     *
     * We do not need to generate a 2480x3508 texture merely to obtain
     * realistic paper grain. Keeping the procedural source bounded greatly
     * reduces memory and CPU usage on mobile devices.
     */
    private const val MAX_TEXTURE_SIZE = 2048

    /**
     * Stable base seed.
     */
    private const val BASE_SEED = 0x4A4E49

    /**
     * Use native C++ engine for paper rendering.
     * Set to false to use pure Kotlin implementation.
     */
    var useNativeEngine: Boolean = true

    /**
     * Generate a realistic procedural paper texture.
     * Uses native C++ engine when available, falls back to Kotlin implementation.
     */
    fun generateTexture(
        paperSize: PaperSize,
        density: Density,
        paperColor: Color
    ): ImageBitmap {
        return if (useNativeEngine) {
            generateTextureNative(paperSize, density, paperColor)
        } else {
            generateTextureKotlin(paperSize, density, paperColor)
        }
    }

    /**
     * Generate paper texture using native C++ engine.
     */
    private fun generateTextureNative(
        paperSize: PaperSize,
        density: Density,
        paperColor: Color
    ): ImageBitmap {
        val requestedWidth = with(density) {
            paperSize.widthDp.roundToPx()
        }.coerceAtLeast(1)

        val requestedHeight = with(density) {
            paperSize.heightDp.roundToPx()
        }.coerceAtLeast(1)

        /*
         * Reduce the procedural source resolution when necessary.
         *
         * This prevents large UI density values from producing unnecessarily
         * huge intermediate bitmaps.
         */
        val scale = min(
            1f,
            MAX_TEXTURE_SIZE.toFloat() /
                max(
                    requestedWidth,
                    requestedHeight
                ).toFloat()
        )

        val width = max(
            1,
            (requestedWidth * scale).toInt()
        )

        val height = max(
            1,
            (requestedHeight * scale).toInt()
        )

        // Create bitmap for native rendering
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        // Calculate seed based on paper size and color
        val seed = BASE_SEED + width * 31 + height * 17 + paperColor.hashCode()

        // Convert Color to RGB components for native engine
        val argb = paperColor.toArgb()
        val r = (argb and 0x00FF0000) ushr 16 / 255.0f
        val g = (argb and 0x0000FF00) ushr 8 / 255.0f
        val b = (argb and 0x000000FF) / 255.0f

        // Use native engine to render paper
        PaperEngineNative.renderPaper(
            bitmap = bitmap,
            width = width,
            height = height,
            seed = seed,
            grainIntensity = 0.5f,  // Medium grain
            fiberDensity = 0.3f,    // Moderate fiber density
            waterStainCount = 0,    // No water stains for clean paper
            agingYellow = 0.05f,    // Slight aging
            fiberDirection = 0.0f,  // No directional bias
            roughness = 0.2f        // Light roughness
        )

        return bitmap.asImageBitmap()
    }

    /**
     * Generate paper texture using pure Kotlin implementation (fallback).
     */
    private fun generateTextureKotlin(
        paperSize: PaperSize,
        density: Density,
        paperColor: Color
    ): ImageBitmap {

        val requestedWidth = with(density) {
            paperSize.widthDp.roundToPx()
        }.coerceAtLeast(1)

        val requestedHeight = with(density) {
            paperSize.heightDp.roundToPx()
        }.coerceAtLeast(1)

        /*
         * Reduce the procedural source resolution when necessary.
         *
         * This prevents large UI density values from producing unnecessarily
         * huge intermediate bitmaps.
         */
        val scale = min(
            1f,
            MAX_TEXTURE_SIZE.toFloat() /
                max(
                    requestedWidth,
                    requestedHeight
                ).toFloat()
        )

        val width = max(
            1,
            (requestedWidth * scale).toInt()
        )

        val height = max(
            1,
            (requestedHeight * scale).toInt()
        )

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        val baseColor = paperColor.toArgb()

        canvas.drawColor(baseColor)

        /*
         * Separate deterministic random stream for fibers and microscopic
         * imperfections.
         */
        val random = Random(
            BASE_SEED.toLong() +
                width.toLong() * 31L +
                height.toLong() * 17L
        )

        /*
         * Continuous paper formation field.
         *
         * Three frequency bands are combined:
         *
         * broad  = large paper formation
         * medium = subtle sheet density
         * fine   = microscopic stochastic variation
         */
        val pixels = IntArray(width * height)

        for (y in 0 until height) {

            val v =
                y.toDouble() /
                    height.toDouble()

            for (x in 0 until width) {

                val u =
                    x.toDouble() /
                        width.toDouble()

                /*
                 * Large-scale variation.
                 *
                 * Low frequency prevents the texture from becoming
                 * cloud-like or marble-like.
                 */
                val broad = smoothNoise(
                    x = u * 7.0,
                    y = v * 7.0,
                    seed = BASE_SEED
                )

                /*
                 * Medium-scale formation.
                 */
                val medium = smoothNoise(
                    x = u * 24.0,
                    y = v * 24.0,
                    seed = BASE_SEED + 71
                )

                /*
                 * Fine stochastic component.
                 */
                val fine = hashNoise(
                    x = x,
                    y = y,
                    seed = BASE_SEED + 113
                )

                /*
                 * Very low amplitudes are intentional.
                 *
                 * Real paper is usually visually close to uniform. The
                 * texture should become apparent on close inspection rather
                 * than dominate handwriting.
                 */
                val variation =
                    broad * 2.4f +
                        medium * 1.25f +
                        fine * 0.65f

                pixels[
                    y * width + x
                ] = adjustPaperColor(
                    color = baseColor,
                    delta = variation
                )
            }
        }

        bitmap.setPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        /*
         * Add cellulose-like fibers.
         */
        drawFibers(
            canvas = canvas,
            width = width,
            height = height,
            random = random
        )

        /*
         * Add extremely subtle microscopic imperfections.
         */
        drawMicroSpecks(
            canvas = canvas,
            width = width,
            height = height,
            random = random
        )

        /*
         * Correct Compose Android conversion.
         *
         * ImageBitmap(bitmap) is not a valid constructor in the Compose
         * version used by this project.
         */
        return bitmap.asImageBitmap()
    }

    /**
     * Apply distortion to a bitmap using native C++ engine.
     */
    fun distortBitmap(
        bitmap: Bitmap,
        seed: Int,
        distortionScale: Float = 0.5f,
        sineWarpScale: Float = 0.3f,
        curvatureScale: Float = 0.2f
    ) {
        if (useNativeEngine) {
            PaperEngineNative.distortBitmap(
                bitmap = bitmap,
                seed = seed,
                distortionScale = distortionScale,
                sineWarpScale = sineWarpScale,
                curvatureScale = curvatureScale
            )
        } else {
            // Fallback: apply simple distortion in Kotlin
            // For now, just do nothing (native path handles it)
        }
    }

    /**
     * Apply distortion optimized for character bitmaps.
     */
    fun distortCharacter(
        bitmap: Bitmap,
        seed: Int,
        scale: Float = 0.5f
    ) {
        if (useNativeEngine) {
            PaperEngineNative.distortCharacter(
                bitmap = bitmap,
                seed = seed,
                scale = scale
            )
        }
    }

    /**
     * Simulate ink on paper using native C++ engine.
     */
    fun simulateInk(
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
     * Simplified ink simulation for direct color stamping.
     */
    fun simulateInkSimple(
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
     * Draw sparse, irregular cellulose-like fibers.
     */
    private fun drawFibers(
        canvas: Canvas,
        width: Int,
        height: Int,
        random: Random
    ) {

        /*
         * Scale fiber count with image area but keep a hard upper bound.
         */
        val fiberCount = min(
            9000,
            max(
                900,
                width * height / 900
            )
        )

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        repeat(fiberCount) {

            val startX =
                random.nextFloat() *
                    width.toFloat()

            val startY =
                random.nextFloat() *
                    height.toFloat()

            /*
             * Most fibers are short.
             *
             * A small number of longer fibers creates natural variation.
             */
            val length =
                if (random.nextFloat() < 0.92f) {
                    1.5f +
                        random.nextFloat() * 5.5f
                } else {
                    5.0f +
                        random.nextFloat() * 13.0f
                }

            /*
             * Random orientation with a weak directional component.
             */
            val baseAngle =
                random.nextFloat() *
                    Math.PI.toFloat()

            val directionalBias =
                (
                    random.nextFloat() -
                        0.5f
                    ) * 0.55f

            val angle =
                baseAngle +
                    directionalBias

            /*
             * Small curvature.
             */
            val bend =
                (
                    random.nextFloat() -
                        0.5f
                    ) * 1.2f

            /*
             * Most fibers are almost invisible.
             */
            val alpha =
                if (random.nextFloat() < 0.70f) {
                    5 +
                        random.nextInt(9)
                } else {
                    10 +
                        random.nextInt(10)
                }

            /*
             * Slightly varying fiber tones.
             */
            paint.color =
                if (random.nextBoolean()) {

                    android.graphics.Color.argb(
                        alpha,
                        92,
                        76,
                        61
                    )

                } else {

                    android.graphics.Color.argb(
                        alpha,
                        125,
                        119,
                        108
                    )
                }

            /*
             * Extremely thin fibers.
             */
            paint.strokeWidth =
                0.28f +
                    random.nextFloat() * 0.52f

            val dx =
                cos(angle) *
                    length

            val dy =
                sin(angle) *
                    length

            /*
             * Curved fiber rather than an artificial straight line.
             */
            val path =
                android.graphics.Path().apply {

                    moveTo(
                        startX,
                        startY
                    )

                    cubicTo(
                        startX +
                            dx * 0.30f,

                        startY +
                            dy * 0.30f +
                            bend,

                        startX +
                            dx * 0.72f,

                        startY +
                            dy * 0.72f -
                            bend,

                        startX + dx,
                        startY + dy
                    )
                }

            canvas.drawPath(
                path,
                paint
            )
        }
    }

    /**
     * Add microscopic paper imperfections.
     */
    private fun drawMicroSpecks(
        canvas: Canvas,
        width: Int,
        height: Int,
        random: Random
    ) {

        val count = min(
            7000,
            max(
                700,
                width * height / 1800
            )
        )

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            style = Paint.Style.FILL
        }

        repeat(count) {

            val x =
                random.nextFloat() *
                    width.toFloat()

            val y =
                random.nextFloat() *
                    height.toFloat()

            /*
             * Tiny source radius. Scaling during rendering naturally
             * softens these imperfections.
             */
            val radius =
                0.15f +
                    random.nextFloat() * 0.38f

            /*
             * Extremely low opacity.
             */
            val alpha =
                2 +
                    random.nextInt(7)

            paint.color =
                android.graphics.Color.argb(
                    alpha,
                    75 +
                        random.nextInt(35),
                    67 +
                        random.nextInt(30),
                    57 +
                        random.nextInt(25)
                )

            canvas.drawCircle(
                x,
                y,
                radius,
                paint
            )
        }
    }

    /**
     * Smooth interpolated value noise.
     */
    private fun smoothNoise(
        x: Double,
        y: Double,
        seed: Int
    ): Float {

        val x0 =
            floor(x).toInt()

        val y0 =
            floor(y).toInt()

        val fx =
            x -
                x0.toDouble()

        val fy =
            y -
                y0.toDouble()

        val sx =
            fade(fx)

        val sy =
            fade(fy)

        val n00 =
            lattice(
                x0,
                y0,
                seed
            )

        val n10 =
            lattice(
                x0 + 1,
                y0,
                seed
            )

        val n01 =
            lattice(
                x0,
                y0 + 1,
                seed
            )

        val n11 =
            lattice(
                x0 + 1,
                y0 + 1,
                seed
            )

        val nx0 =
            lerp(
                n00,
                n10,
                sx
            )

        val nx1 =
            lerp(
                n01,
                n11,
                sx
            )

        return lerp(
            nx0,
            nx1,
            sy
        ).toFloat()
    }

    /**
     * Deterministic lattice noise.
     */
    private fun lattice(
        x: Int,
        y: Int,
        seed: Int
    ): Double {

        var h =
            x.toLong() *
                374761393L +
                y.toLong() *
                668265263L +
                seed.toLong() *
                1442695041L

        h =
            (h xor (h ushr 13)) *
                1274126177L

        h =
            h xor
                (h ushr 16)

        return (
            (h and 0x7FFFFFFF).toDouble() /
                1073741823.5
            ) - 1.0
    }

    /**
     * Fast deterministic fine-grain noise.
     */
    private fun hashNoise(
        x: Int,
        y: Int,
        seed: Int
    ): Float {

        var h =
            x *
                374761393 +
                y *
                668265263 +
                seed *
                1442695041

        h =
            (h xor (h ushr 13)) *
                1274126177

        h =
            h xor
                (h ushr 16)

        return (
            (h and 0xFFFF) /
                32767.5f
            ) - 1f
    }

    /**
     * Smoothstep interpolation.
     */
    private fun fade(
        t: Double
    ): Double {
        return t * t *
            (3.0 - 2.0 * t)
    }

    /**
     * Linear interpolation.
     */
    private fun lerp(
        a: Double,
        b: Double,
        t: Double
    ): Double {
        return a +
            (b - a) * t
    }

    /**
     * Apply subtle luminance variation while preserving the original
     * paper color.
     *
     * Slightly lower green/blue response gives the texture a natural warm
     * cellulose character without forcing the page itself to become yellow.
     */
    private fun adjustPaperColor(
        color: Int,
        delta: Float
    ): Int {

        val red =
            (
                android.graphics.Color.red(color) +
                    delta
                )
                .toInt()
                .coerceIn(
                    0,
                    255
                )

        val green =
            (
                android.graphics.Color.green(color) +
                    delta * 0.92f
                )
                .toInt()
                .coerceIn(
                    0,
                    255
                )

        val blue =
            (
                android.graphics.Color.blue(color) +
                    delta * 0.78f
                )
                .toInt()
                .coerceIn(
                    0,
                    255
                )

        return android.graphics.Color.argb(
            255,
            red,
            green,
            blue
        )
    }
}
