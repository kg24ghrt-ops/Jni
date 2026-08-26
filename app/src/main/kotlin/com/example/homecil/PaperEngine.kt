package com.example.homecil

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * The texture is deliberately built from several weak, independent spatial
 * scales rather than one obvious noise pattern:
 *
 * 1. Broad formation variation
 * 2. Medium-scale density variation
 * 3. Fine stochastic grain
 * 4. Sparse cellulose-like fibers
 * 5. Extremely subtle micro-specks
 *
 * This produces a cleaner and more physically plausible paper appearance
 * while avoiding obvious repeating digital patterns.
 *
 * The renderer is deterministic for a given paper size, which is useful for
 * reproducible exports and testing.
 */
object PaperEngine {

    /*
     * Keep the procedural source texture bounded.
     *
     * A5/A4 exports are much larger than necessary for generating the texture
     * itself. The texture is later scaled by the export pipeline. Limiting
     * this source bitmap prevents unnecessary memory and CPU consumption.
     */
    private const val MAX_TEXTURE_SIZE = 2048

    /*
     * Fixed seed makes the same paper configuration visually stable between
     * regenerations.
     */
    private const val BASE_SEED = 0x4A4E49

    /**
     * Generates a procedural paper texture.
     */
    fun generateTexture(
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
         * Scale the procedural source down when the UI representation is
         * larger than MAX_TEXTURE_SIZE.
         */
        val textureScale =
            min(
                1f,
                MAX_TEXTURE_SIZE.toFloat() /
                    max(requestedWidth, requestedHeight).toFloat()
            )

        val width =
            max(
                1,
                (requestedWidth * textureScale).toInt()
            )

        val height =
            max(
                1,
                (requestedHeight * textureScale).toInt()
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
         * Separate random stream for geometric fibers/specks.
         * The seed also incorporates the texture dimensions so A4 and A5
         * don't accidentally share identical spatial distributions.
         */
        val random = Random(
            BASE_SEED.toLong() +
                width.toLong() * 31L +
                height.toLong() * 17L
        )

        /*
         * First generate the continuous paper-density field.
         *
         * Three frequency bands are combined:
         *
         * broad  -> large-scale paper formation
         * medium -> subtle sheet structure
         * fine   -> microscopic irregularity
         *
         * The amplitudes are intentionally tiny because realistic paper
         * should normally look nearly uniform at first glance.
         */
        val pixels = IntArray(width * height)

        for (y in 0 until height) {

            val v = y.toDouble() / height.toDouble()

            for (x in 0 until width) {

                val u = x.toDouble() / width.toDouble()

                /*
                 * Broad variation.
                 *
                 * Very low frequency prevents the texture from looking like
                 * clouds or marble.
                 */
                val broad = smoothNoise(
                    x = u * 7.0,
                    y = v * 7.0,
                    seed = BASE_SEED
                )

                /*
                 * Medium variation provides the subtle uneven density
                 * normally perceived in real paper.
                 */
                val medium = smoothNoise(
                    x = u * 24.0,
                    y = v * 24.0,
                    seed = BASE_SEED + 71
                )

                /*
                 * High-frequency stochastic component.
                 */
                val fine = hashNoise(
                    x = x,
                    y = y,
                    seed = BASE_SEED + 113
                )

                /*
                 * Very conservative amplitudes.
                 *
                 * The goal is texture that is visible when inspected,
                 * rather than a visibly noisy background.
                 */
                val variation =
                    broad * 2.4f +
                        medium * 1.25f +
                        fine * 0.65f

                pixels[y * width + x] =
                    adjustPaperColor(
                        baseColor,
                        variation
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
         * Add the physical-looking fiber structure after the continuous
         * density field.
         */
        drawFibers(
            canvas = canvas,
            width = width,
            height = height,
            random = random
        )

        /*
         * Add extremely sparse micro imperfections.
         */
        drawMicroSpecks(
            canvas = canvas,
            width = width,
            height = height,
            random = random
        )

        return ImageBitmap(bitmap)
    }

    /**
     * Draws sparse irregular cellulose-like fibers.
     *
     * Real paper fibers aren't represented well by thousands of identical
     * straight lines, so each strand gets:
     *
     * - random length
     * - random direction
     * - slight directional bias
     * - slight curvature
     * - randomized opacity
     * - randomized warm/cool tone
     * - randomized thickness
     */
    private fun drawFibers(
        canvas: Canvas,
        width: Int,
        height: Int,
        random: Random
    ) {

        /*
         * Fiber density scales with area but is capped so generation remains
         * predictable on mobile hardware.
         */
        val fiberCount =
            min(
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
                random.nextFloat() * width.toFloat()

            val startY =
                random.nextFloat() * height.toFloat()

            /*
             * Most fibers are short.
             *
             * A small percentage are longer strands, preventing the texture
             * from becoming visually uniform.
             */
            val length =
                if (random.nextFloat() < 0.92f) {
                    1.5f + random.nextFloat() * 5.5f
                } else {
                    5.0f + random.nextFloat() * 13.0f
                }

            /*
             * Weak directional structure.
             *
             * Completely isotropic fibers tend to look like digital noise.
             * A very weak bias gives the sheet a more natural structure.
             */
            val baseAngle =
                random.nextFloat() * Math.PI.toFloat()

            val directionalBias =
                (random.nextFloat() - 0.5f) * 0.55f

            val angle =
                baseAngle + directionalBias

            /*
             * Small curvature amount.
             */
            val bend =
                (random.nextFloat() - 0.5f) * 1.2f

            /*
             * Most fibers remain nearly invisible.
             */
            val alpha =
                if (random.nextFloat() < 0.70f) {
                    5 + random.nextInt(9)
                } else {
                    10 + random.nextInt(10)
                }

            /*
             * Slightly different fiber tones avoid the "one paint color"
             * appearance.
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
             * Keep fibers extremely thin.
             */
            paint.strokeWidth =
                0.28f +
                    random.nextFloat() * 0.52f

            val dx =
                cos(angle) * length

            val dy =
                sin(angle) * length

            /*
             * Cubic curves make the strands look much less synthetic than
             * straight line segments.
             */
            val path =
                android.graphics.Path().apply {

                    moveTo(
                        startX,
                        startY
                    )

                    cubicTo(
                        startX + dx * 0.30f,
                        startY + dy * 0.30f + bend,

                        startX + dx * 0.72f,
                        startY + dy * 0.72f - bend,

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
     * Adds microscopic imperfections.
     *
     * These are intentionally much weaker than the fibers. Their purpose is
     * to break up perfectly smooth areas without making the page visibly
     * dirty.
     */
    private fun drawMicroSpecks(
        canvas: Canvas,
        width: Int,
        height: Int,
        random: Random
    ) {

        val count =
            min(
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
                random.nextFloat() * width.toFloat()

            val y =
                random.nextFloat() * height.toFloat()

            /*
             * Sub-pixel-sized source marks become naturally softened when
             * the texture is scaled for export.
             */
            val radius =
                0.15f +
                    random.nextFloat() * 0.38f

            /*
             * Very low opacity is essential here.
             */
            val alpha =
                2 + random.nextInt(7)

            paint.color =
                android.graphics.Color.argb(
                    alpha,
                    75 + random.nextInt(35),
                    67 + random.nextInt(30),
                    57 + random.nextInt(25)
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
     *
     * This produces continuous low-frequency variation instead of isolated
     * random pixels.
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
            x - x0.toDouble()

        val fy =
            y - y0.toDouble()

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
     * Deterministic lattice value.
     */
    private fun lattice(
        x: Int,
        y: Int,
        seed: Int
    ): Double {

        var h =
            x.toLong() * 374761393L +
                y.toLong() * 668265263L +
                seed.toLong() * 1442695041L

        h =
            (h xor (h ushr 13)) *
                1274126177L

        h =
            h xor (h ushr 16)

        return (
            (h and 0x7FFFFFFF).toDouble() /
                1073741823.5
            ) - 1.0
    }

    /**
     * Fast deterministic per-pixel noise.
     */
    private fun hashNoise(
        x: Int,
        y: Int,
        seed: Int
    ): Float {

        var h =
            x * 374761393 +
                y * 668265263 +
                seed * 1442695041

        h =
            (h xor (h ushr 13)) *
                1274126177

        h =
            h xor (h ushr 16)

        return (
            (h and 0xFFFF) /
                32767.5f
            ) - 1f
    }

    /**
     * Smoothstep interpolation curve.
     */
    private fun fade(
        t: Double
    ): Double {
        return t * t * (3.0 - 2.0 * t)
    }

    /**
     * Linear interpolation.
     */
    private fun lerp(
        a: Double,
        b: Double,
        t: Double
    ): Double {
        return a + (b - a) * t
    }

    /**
     * Applies an extremely small luminance shift to the supplied paper color.
     *
     * The green and blue channels receive slightly smaller changes than red,
     * producing a subtle warm-paper response instead of neutral grayscale
     * noise.
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
                .coerceIn(0, 255)

        val green =
            (
                android.graphics.Color.green(color) +
                    delta * 0.92f
                )
                .toInt()
                .coerceIn(0, 255)

        val blue =
            (
                android.graphics.Color.blue(color) +
                    delta * 0.78f
                )
                .toInt()
                .coerceIn(0, 255)

        return android.graphics.Color.argb(
            255,
            red,
            green,
            blue
        )
    }
}