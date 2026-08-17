package com.example.homecil

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.Context
import androidx.compose.ui.platform.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExportEngine {
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
            try {
                val w = paperSize.exportW
                val h = paperSize.exportH
                val paperColor = Color(0xFFFBF9F2)
                val lineColor = Color(0xFFA9C2D9)
                val marginColor = Color(0xFFE57373)

                val exportBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val exportCanvas = android.graphics.Canvas(exportBitmap)
                exportCanvas.drawColor(paperColor.toArgb())

                val grainRect = Rect(0, 0, paperTexture.width, paperTexture.height)
                exportCanvas.drawBitmap(paperTexture.asAndroidBitmap(), grainRect, Rect(0, 0, w, h), null)

                val scaleW = w.toFloat() / with(density) { paperSize.widthDp.toPx() }
                val scaleH = h.toFloat() / with(density) { paperSize.heightDp.toPx() }

                val linePaint = Paint().apply { color = lineColor.toArgb(); strokeWidth = 2f * scaleH; isAntiAlias = true }
                val marginXPx = with(density) { marginXDp.toPx() } * scaleW
                val lineSpacingPx = with(density) { lineSpacingDp.toPx() } * scaleH

                var y = lineSpacingPx
                while (y < h) { exportCanvas.drawLine(0f, y, w.toFloat(), y, linePaint); y += lineSpacingPx }
                exportCanvas.drawLine(marginXPx, 0f, marginXPx, h.toFloat(), Paint().apply { color = marginColor.toArgb(); strokeWidth = 3f * scaleW; isAntiAlias = true })

                val textPaint = TextPaint().apply {
                    color = penType.baseColor.toArgb()
                    textSize = with(density) { 36.dp.toPx() } * scaleH
                    // Use DEFAULT instead of "cursive" to ensure Burmese characters render properly
                    typeface = Typeface.create(Typeface.DEFAULT, penType.typefaceStyle) 
                    isAntiAlias = true
                    // Simulate ink bleed in the final PNG
                    maskFilter = BlurMaskFilter(1.5f * scaleH, BlurMaskFilter.Blur.NORMAL)
                }

                val fm = textPaint.fontMetrics
                val extraSpacing = lineSpacingPx - (fm.descent - fm.ascent)

                val textWidth = w - marginXPx.toInt() - (with(density) { 24.dp.toPx() } * scaleW).toInt()
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth)
                    .setLineSpacing(extraSpacing, 1f)
                    .build()

                exportCanvas.save()
                exportCanvas.translate(marginXPx + with(density) { 12.dp.toPx() } * scaleW, with(density) { 12.dp.toPx() } * scaleH)
                layout.draw(exportCanvas)
                exportCanvas.restore()

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "Homework_${System.currentTimeMillis()}.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HomeCil")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { context.contentResolver.openOutputStream(it)?.use { stream -> exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) } }

                withContext(Dispatchers.Main) { Toast.makeText(context, "Saved to Pictures/HomeCil!", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}