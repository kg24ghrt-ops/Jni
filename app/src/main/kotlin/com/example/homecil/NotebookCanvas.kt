package com.example.homecil

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PaperSize(val widthDp: Dp, val heightDp: Dp, val label: String, val exportW: Int, val exportH: Int) {
    A4(1323.dp, 1871.dp, "A4", 2480, 3508),
    A5(932.dp, 1323.dp, "A5", 1748, 2480)
}

enum class PenType(val label: String, val color: Color, val blur: Dp, val typefaceStyle: Int) {
    BALLPOINT("Ballpoint", Color(0xDD1A237E), 0.dp, Typeface.NORMAL),
    GEL("Gel Pen", Color(0xDD000000), 0.8.dp, Typeface.BOLD),
    FOUNTAIN("Fountain", Color(0xDD1A1A3A), 0.4.dp, Typeface.ITALIC)
}

@Composable
fun NotebookCanvas() {
    var currentPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var currentPen by remember { mutableStateOf(PenType.BALLPOINT) }
    
    // Use TextFieldValue to support native Android text selection, cursor tracking, and Copy/Paste
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    
    // Track current line and layout result for the highlight effect
    var currentLineIndex by remember { mutableStateOf(-1) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    var baseScale by remember { mutableStateOf(-1f) }
    var userScale by remember { mutableStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }
    var isPanMode by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val lineSpacing = 50.dp
    val marginX = 220.dp

    val paperColor = Color(0xFFFBF9F2)
    val lineColor = Color(0xFFA9C2D9)
    val marginColor = Color(0xFFE57373)

    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Update the active line index whenever the cursor moves or text layout changes
    LaunchedEffect(textFieldValue.selection, layoutResult) {
        layoutResult?.let { result ->
            val offset = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
            currentLineIndex = result.getLineForOffset(offset)
        }
    }

    val paperTexture = remember(currentPaperSize, density) {
        val wPx = with(density) { currentPaperSize.widthDp.roundToPx() }
        val hPx = with(density) { currentPaperSize.heightDp.roundToPx() }

        val maxTexture = 4096
        val texScale = minOf(1f, maxTexture.toFloat() / maxOf(wPx, hPx))
        val bmpW = (wPx * texScale).toInt()
        val bmpH = (hPx * texScale).toInt()

        val bmp = ImageBitmap(bmpW, bmpH)
        val nativeCanvas = android.graphics.Canvas(bmp.asAndroidBitmap())
        nativeCanvas.drawColor(paperColor.toArgb())

        val random = java.util.Random(123)
        val fiberPaint = Paint().apply { color = android.graphics.Color.argb(25, 100, 80, 60); strokeWidth = 0.8f; isAntiAlias = true }
        val fiberCount = (bmpW * bmpH / 1000).coerceAtMost(10000)
        for (i in 0 until fiberCount) {
            val x1 = random.nextFloat() * bmpW
            val y1 = random.nextFloat() * bmpH
            val angle = random.nextFloat() * Math.PI
            val len = random.nextFloat() * 6f + 2f
            nativeCanvas.drawLine(x1, y1, x1 + (len * Math.cos(angle)).toFloat(), y1 + (len * Math.sin(angle)).toFloat(), fiberPaint)
        }
        bmp
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFFD7CCC8))) {

        val paperWpx = with(density) { currentPaperSize.widthDp.toPx() }
        val paperHpx = with(density) { currentPaperSize.heightDp.toPx() }
        val fitScale = minOf(
            (constraints.maxWidth * 0.92f) / paperWpx,
            (constraints.maxHeight * 0.92f) / paperHpx
        )
        val totalScale = (if (baseScale < 0f) fitScale else baseScale) * userScale

        LaunchedEffect(currentPaperSize, constraints.maxWidth, constraints.maxHeight) {
            baseScale = fitScale
            userScale = 1f
            userOffset = Offset.Zero
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isPanMode) {
                    if (isPanMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            userScale = (userScale * zoom).coerceIn(0.1f, 10f)
                            userOffset += pan
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = totalScale
                        scaleY = totalScale
                        translationX = userOffset.x
                        translationY = userOffset.y
                    }
                    .requiredWidth(currentPaperSize.widthDp)
                    .requiredHeight(currentPaperSize.heightDp)
                    .shadow(elevation = 12.dp, spotColor = Color.Black)
                    .background(paperColor)
            ) {
                Image(bitmap = paperTexture, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 1. Draw Active Line Highlight (Behind the rules for a notebook feel)
                    if (currentLineIndex >= 0 && layoutResult != null) {
                        val highlightColor = Color(0x40FFEB3B) // Transparent Yellow
                        val textTopPadding = with(density) { 12.dp.toPx() }
                        val lineTop = layoutResult!!.getLineTop(currentLineIndex) + textTopPadding
                        val lineBottom = layoutResult!!.getLineBottom(currentLineIndex) + textTopPadding
                        
                        drawRect(
                            color = highlightColor,
                            topLeft = Offset(0f, lineTop),
                            size = Size(size.width, lineBottom - lineTop)
                        )
                    }

                    // 2. Draw Ruled Lines and Margin
                    val lineSpacingPx = lineSpacing.toPx()
                    val marginXPx = marginX.toPx()
                    var y = lineSpacingPx
                    while (y < size.height) {
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 2f)
                        y += lineSpacingPx
                    }
                    drawLine(marginColor, Offset(marginXPx, 0f), Offset(marginXPx, size.height), 3f)
                }

                // 3. Text Field (Supports Native Copy/Paste via TextFieldValue)
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { if (!isPanMode) textFieldValue = it },
                    onTextLayout = { layoutResult = it },
                    enabled = !isPanMode,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Cursive,
                        fontSize = with(density) { 36.dp.toPx().toSp() },
                        lineHeight = with(density) { lineSpacing.toPx().toSp() },
                        color = currentPen.color
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        // REMOVED marginX constraint to allow typing OUTSIDE the left margin
                        .padding(start = 12.dp, top = 12.dp, end = 24.dp, bottom = 24.dp)
                        .then(if (!isPanMode) Modifier.verticalScroll(scrollState) else Modifier)
                        .blur(currentPen.blur)
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(PaperSize.values()) { size ->
                Button(
                    onClick = { currentPaperSize = size },
                    colors = ButtonDefaults.buttonColors(if (currentPaperSize == size) Color(0xFF1A237E) else Color(0xFF424242)),
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(size.label, color = Color.White) }
            }

            items(PenType.values()) { pen ->
                Button(
                    onClick = { currentPen = pen },
                    colors = ButtonDefaults.buttonColors(if (currentPen == pen) Color(0xFF00695C) else Color(0xFF424242)),
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(pen.label, color = Color.White) }
            }

            item {
                Button(
                    onClick = { isPanMode = !isPanMode },
                    colors = ButtonDefaults.buttonColors(if (isPanMode) Color(0xFFD84315) else Color(0xFF1565C0)),
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(if (isPanMode) "Pan ON" else "Type", color = Color.White) }
            }

            item {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val w = currentPaperSize.exportW
                                val h = currentPaperSize.exportH

                                val exportBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                val exportCanvas = android.graphics.Canvas(exportBitmap)

                                val grainRect = Rect(0, 0, paperTexture.width, paperTexture.height)
                                exportCanvas.drawBitmap(paperTexture.asAndroidBitmap(), grainRect, Rect(0, 0, w, h), null)

                                val scaleW = w.toFloat() / with(density) { currentPaperSize.widthDp.toPx() }
                                val scaleH = h.toFloat() / with(density) { currentPaperSize.heightDp.toPx() }

                                val linePaint = Paint().apply { color = lineColor.toArgb(); strokeWidth = 2f * scaleH; isAntiAlias = true }
                                val marginXPx = with(density) { marginX.toPx() } * scaleW
                                val lineSpacingPx = with(density) { lineSpacing.toPx() } * scaleH

                                var y = lineSpacingPx
                                while (y < h) { exportCanvas.drawLine(0f, y, w.toFloat(), y, linePaint); y += lineSpacingPx }
                                exportCanvas.drawLine(marginXPx, 0f, marginXPx, h.toFloat(), Paint().apply { color = marginColor.toArgb(); strokeWidth = 3f * scaleW; isAntiAlias = true })

                                val textPaint = TextPaint().apply {
                                    color = currentPen.color.toArgb()
                                    textSize = with(density) { 36.dp.toPx() } * scaleH
                                    typeface = Typeface.create("cursive", currentPen.typefaceStyle)
                                    isAntiAlias = true
                                }

                                val fm = textPaint.fontMetrics
                                val extraSpacing = lineSpacingPx - (fm.descent - fm.ascent)

                                val textWidth = w - (with(density) { 12.dp.toPx() } * scaleW).toInt() - (with(density) { 24.dp.toPx() } * scaleW).toInt()
                                val layout = StaticLayout.Builder.obtain(textFieldValue.text, 0, textFieldValue.text.length, textPaint, textWidth)
                                    .setLineSpacing(extraSpacing, 1f)
                                    .build()

                                exportCanvas.save()
                                // Match the new start padding (no margin offset)
                                exportCanvas.translate(with(density) { 12.dp.toPx() } * scaleW, with(density) { 12.dp.toPx() } * scaleH)
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
                    },
                    colors = ButtonDefaults.buttonColors(Color(0xFF2E7D32))
                ) { Text("Export PNG", color = Color.White) }
            }
        }
    }
}