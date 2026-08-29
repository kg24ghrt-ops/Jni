package com.example.homecil

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import androidx.compose.ui.unit.dp

/**
 * Handles rendering the current notebook page into a PNG and saving it
 * through Android MediaStore.
 *
 * Design goals:
 * - Safe MediaStore writes
 * - No partially-visible exports
 * - Correct cancellation behavior
 * - Predictable line spacing
 * - High-quality bitmap scaling
 * - Proper resource cleanup
 * - Compatible with scoped storage
 */
object ExportEngine {

    private const val DIRECTORY_NAME = "HomeCil"

    private const val PAPER_COLOR = 0xFFFBF9F2
    private const val LINE_COLOR = 0xFFA9C2D9
    private const val MARGIN_COLOR = 0xFFE57373

    private const val TEXT_SIZE_DP = 36f
    private const val TEXT_START_PADDING_DP = 12f
    private const val TEXT_END_PADDING_DP = 24f

    private const val LINE_WIDTH_DP = 2f
    private const val MARGIN_WIDTH_DP = 3f

    /**
     * Exports the current notebook page as a PNG.
     *
     * All expensive work runs on Dispatchers.IO.
     */
    suspend fun exportToPng(
        context: Context,
        density: Density,
        paperSize: PaperSize,
        paperTexture: ImageBitmap,
        text: String,
        penType: PenType,
        lineSpacingDp: Dp,
        marginXDp: Dp
    ) {
        withContext(Dispatchers.IO) {
            var exportBitmap: Bitmap? = null
            var outputUri = android.net.Uri.EMPTY

            try {
                val resolver = context.contentResolver

                val width = paperSize.exportW
                val height = paperSize.exportH

                require(width > 0 && height > 0) {
                    "Invalid export dimensions: ${width}x$height"
                }

                val paperWidthPx = with(density) {
                    paperSize.widthDp.toPx()
                }

                val paperHeightPx = with(density) {
                    paperSize.heightDp.toPx()
                }

                require(paperWidthPx > 0f && paperHeightPx > 0f) {
                    "Invalid paper dimensions"
                }

                /*
                 * Separate horizontal/vertical scaling is intentional.
                 *
                 * PaperSize's logical dimensions and export dimensions
                 * represent the same physical page, but their pixel
                 * representations are not necessarily identical.
                 */
                val scaleX = width.toFloat() / paperWidthPx
                val scaleY = height.toFloat() / paperHeightPx

                exportBitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                val canvas = Canvas(exportBitmap)

                /*
                 * Base paper color first. The procedural texture is drawn
                 * over this, so any uncovered/transparent texture pixels
                 * still have a valid paper background.
                 */
                canvas.drawColor(PAPER_COLOR.toInt())

                drawPaperTexture(
                    canvas = canvas,
                    texture = paperTexture,
                    width = width,
                    height = height
                )

                drawNotebookLines(
                    canvas = canvas,
                    density = density,
                    paperSize = paperSize,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    lineSpacingDp = lineSpacingDp,
                    marginXDp = marginXDp
                )

                drawText(
                    canvas = canvas,
                    density = density,
                    paperSize = paperSize,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    marginXDp = marginXDp,
                    text = text,
                    penType = penType
                )

                /*
                 * Android 10+ supports atomic MediaStore publishing.
                 *
                 * IS_PENDING keeps the image invisible to other apps until
                 * the complete PNG has been successfully written.
                 */
                val values = ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        createFileName()
                    )
                    put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/png"
                    )
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME"
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.IS_PENDING,
                            1
                        )
                    }
                }

                outputUri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IOException(
                    "MediaStore refused to create the output file"
                )

                resolver.openOutputStream(outputUri, "w")?.use { output ->
                    val compressed = exportBitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output
                    )

                    if (!compressed) {
                        throw IOException(
                            "Bitmap PNG compression failed"
                        )
                    }

                    output.flush()
                } ?: throw IOException(
                    "Unable to open output stream"
                )

                /*
                 * Publish only after the complete PNG exists.
                 */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publishValues = ContentValues().apply {
                        put(
                            MediaStore.Images.Media.IS_PENDING,
                            0
                        )
                    }

                    val updatedRows = resolver.update(
                        outputUri,
                        publishValues,
                        null,
                        null
                    )

                    if (updatedRows != 1) {
                        throw IOException(
                            "Unable to publish exported image"
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Saved to Pictures/$DIRECTORY_NAME",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                /*
                 * Never swallow coroutine cancellation.
                 *
                 * If the export coroutine is cancelled, cleanup the
                 * incomplete MediaStore entry and propagate cancellation.
                 */
                if (outputUri != android.net.Uri.EMPTY) {
                    runCatching {
                        context.contentResolver.delete(
                            outputUri,
                            null,
                            null
                        )
                    }
                }

                throw cancelled

            } catch (error: Exception) {
                /*
                 * Delete any partially-created MediaStore item.
                 */
                if (outputUri != android.net.Uri.EMPTY) {
                    runCatching {
                        context.contentResolver.delete(
                            outputUri,
                            null,
                            null
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    val message = error.message
                        ?.takeIf { it.isNotBlank() }
                        ?: "Unknown export error"

                    Toast.makeText(
                        context,
                        "Export failed: $message",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } finally {
                /*
                 * Explicitly release the large ARGB bitmap.
                 *
                 * A 2480x3508 ARGB_8888 bitmap is roughly 33 MiB,
                 * so keeping it around unnecessarily is undesirable
                 * on mobile devices.
                 */
                exportBitmap?.let { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    /**
     * Draws the procedural paper texture while enabling filtering.
     *
     * Filtering is important because PaperEngine may generate a smaller
     * source texture than the final export resolution.
     */
    private fun drawPaperTexture(
        canvas: Canvas,
        texture: ImageBitmap,
        width: Int,
        height: Int
    ) {
        val source = texture.asAndroidBitmap()

        if (source.width <= 0 || source.height <= 0) {
            return
        }

        val sourceRect = Rect(
            0,
            0,
            source.width,
            source.height
        )

        val destinationRect = Rect(
            0,
            0,
            width,
            height
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        canvas.drawBitmap(
            source,
            sourceRect,
            destinationRect,
            paint
        )
    }

    /**
     * Draws notebook ruling and the red margin.
     */
    private fun drawNotebookLines(
        canvas: Canvas,
        density: Density,
        paperSize: PaperSize,
        scaleX: Float,
        scaleY: Float,
        lineSpacingDp: Dp,
        marginXDp: Dp
    ) {
        val lineSpacingPx = with(density) {
            lineSpacingDp.toPx()
        } * scaleY

        val marginXPx = with(density) {
            marginXDp.toPx()
        } * scaleX

        if (lineSpacingPx <= 0f) {
            return
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(LINE_COLOR.toInt()).toArgb()
            strokeWidth = LINE_WIDTH_DP * scaleY
            style = Paint.Style.STROKE
            isDither = true
        }

        var y = lineSpacingPx

        while (y < paperSize.exportH) {
            canvas.drawLine(
                0f,
                y,
                paperSize.exportW.toFloat(),
                y,
                linePaint
            )

            y += lineSpacingPx
        }

        val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(MARGIN_COLOR.toInt()).toArgb()
            strokeWidth = MARGIN_WIDTH_DP * scaleX
            style = Paint.Style.STROKE
            isDither = true
        }

        canvas.drawLine(
            marginXPx,
            0f,
            marginXPx,
            paperSize.exportH.toFloat(),
            marginPaint
        )
    }

    /**
     * Renders the handwritten text.
     */
    private fun drawText(
        canvas: Canvas,
        density: Density,
        paperSize: PaperSize,
        scaleX: Float,
        scaleY: Float,
        marginXDp: Dp,
        text: String,
        penType: PenType
    ) {
        if (text.isEmpty()) {
            return
        }

        val marginXPx = with(density) {
            marginXDp.toPx()
        } * scaleX

        val startPaddingPx = with(density) {
            TEXT_START_PADDING_DP.dp.toPx()
        } * scaleX

        val endPaddingPx = with(density) {
            TEXT_END_PADDING_DP.dp.toPx()
        } * scaleX

        val textSizePx = with(density) {
            TEXT_SIZE_DP.dp.toPx()
        } * scaleY

        val availableWidth = (
            paperSize.exportW -
                marginXPx -
                startPaddingPx -
                endPaddingPx
            ).toInt()

        if (availableWidth <= 0) {
            return
        }

        val textPaint = createTextPaint(
            density = density,
            scaleX = scaleX,
            scaleY = scaleY,
            penType = penType,
            textSizePx = textSizePx
        )

        val requestedLineSpacingPx = with(density) {
            50.dp.toPx()
        } * scaleY

        val fontMetrics = textPaint.fontMetrics

        /*
         * StaticLayout's extra spacing is added to the natural font
         * height. Never provide a negative value because that can cause
         * glyph overlap and inconsistent line positioning.
         */
        val naturalLineHeight =
            fontMetrics.descent - fontMetrics.ascent

        val extraSpacing = maxOf(
            0f,
            requestedLineSpacingPx - naturalLineHeight
        )

        val layout = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            textPaint,
            availableWidth
        )
            .setIncludePad(false)
            .setLineSpacing(
                extraSpacing,
                1f
            )
            .build()

        canvas.save()

        canvas.translate(
            marginXPx + startPaddingPx,
            with(density) {
                TEXT_START_PADDING_DP.dp.toPx()
            } * scaleY
        )

        layout.draw(canvas)

        canvas.restore()
    }

    /**
     * Creates the Android text renderer corresponding to the selected pen.
     */
    private fun createTextPaint(
        density: Density,
        scaleX: Float,
        scaleY: Float,
        penType: PenType,
        textSizePx: Float
    ): TextPaint {
        val baseColor = penType.baseColor

        /*
         * A very subtle directional ink variation makes the exported text
         * feel less like a completely flat digital font while remaining
         * sharp enough for handwriting-style output.
         */
        val shaderWidth = maxOf(
            1f,
            96f * scaleX
        )

        val shader = LinearGradient(
            0f,
            0f,
            shaderWidth,
            maxOf(1f, 96f * scaleY),
            intArrayOf(
                baseColor.copy(alpha = 0.96f).toArgb(),
                baseColor.copy(alpha = 0.88f).toArgb(),
                baseColor.copy(alpha = 0.98f).toArgb()
            ),
            floatArrayOf(
                0f,
                0.52f,
                1f
            ),
            Shader.TileMode.MIRROR
        )

        return TextPaint(
            Paint.ANTI_ALIAS_FLAG or
                Paint.SUBPIXEL_TEXT_FLAG or
                Paint.DITHER_FLAG
        ).apply {
            textSize = textSizePx
            color = baseColor.toArgb()
            this.shader = shader

            typeface = when (penType.typefaceStyle) {
                Typeface.BOLD -> Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )

                Typeface.ITALIC -> Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.ITALIC
                )

                Typeface.BOLD_ITALIC -> Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD_ITALIC
                )

                else -> Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.NORMAL
                )
            }

            isAntiAlias = true
            isSubpixelText = true
            isDither = true
        }
    }

    private fun createFileName(): String {
        return "Homework_${System.currentTimeMillis()}.png"
    }
}
