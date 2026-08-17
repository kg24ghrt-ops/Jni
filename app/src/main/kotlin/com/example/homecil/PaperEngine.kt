package com.example.homecil

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Random

enum class PaperSize(val widthDp: Dp, val heightDp: Dp, val label: String, val exportW: Int, val exportH: Int) {
    A4(1323.dp, 1871.dp, "A4", 2480, 3508),
    A5(932.dp, 1323.dp, "A5", 1748, 2480)
}

object PaperEngine {
    fun generateTexture(paperSize: PaperSize, density: Density, paperColor: Color): ImageBitmap {
        val wPx = with(density) { paperSize.widthDp.roundToPx() }
        val hPx = with(density) { paperSize.heightDp.roundToPx() }

        val maxTexture = 4096
        val texScale = minOf(1f, maxTexture.toFloat() / maxOf(wPx, hPx))
        val bmpW = (wPx * texScale).toInt()
        val bmpH = (hPx * texScale).toInt()

        val bmp = ImageBitmap(bmpW, bmpH)
        val nativeCanvas = android.graphics.Canvas(bmp.asAndroidBitmap())
        nativeCanvas.drawColor(paperColor.toArgb())

        val random = Random(123)
        val fiberPaint = Paint().apply {
            color = android.graphics.Color.argb(25, 100, 80, 60)
            strokeWidth = 0.8f
            isAntiAlias = true
        }
        val fiberCount = (bmpW * bmpH / 1000).coerceAtMost(10000)
        for (i in 0 until fiberCount) {
            val x1 = random.nextFloat() * bmpW
            val y1 = random.nextFloat() * bmpH
            val angle = random.nextFloat() * Math.PI
            val len = random.nextFloat() * 6f + 2f
            nativeCanvas.drawLine(
                x1, y1,
                x1 + (len * Math.cos(angle)).toFloat(),
                y1 + (len * Math.sin(angle)).toFloat(),
                fiberPaint
            )
        }
        return bmp
    }
}