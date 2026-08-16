package com.example.homecil

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PaperSize(val widthDp: Dp, val heightDp: Dp, val label: String) {
    A4(1323.dp, 1871.dp, "A4"),
    A5(932.dp, 1323.dp, "A5")
}

enum class PenType(val label: String, val color: Color, val blur: Dp, val typefaceStyle: Int) {
    BALLPOINT("Ballpoint", Color(0xDD1A237E), 0.dp, Typeface.NORMAL),
    GEL("Gel Pen", Color(0xFF000000), 0.8.dp, Typeface.BOLD),
    FOUNTAIN("Fountain", Color(0xFF1A1A3A), 0.4.dp, Typeface.ITALIC)
}

@Composable
fun NotebookCanvas() {
    var currentPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var currentPen by remember { mutableStateOf(PenType.BALLPOINT) }
    var text by remember { mutableStateOf("") }
    
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isPanMode by remember { mutableStateOf(false) }

    val lineSpacing = 50.dp 
    val marginX = 220.dp    
    
    val paperColor = Color(0xFFFBF9F2)
    val lineColor = Color(0xFFA9C2D9)
    val marginColor = Color(0xFFE57373)

    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // 1. PROCEDURAL PAPER GRAIN ENGINE
    // Draws fibers and mottling once into an ImageBitmap for 60fps performance
    val paperTexture = remember(currentPaperSize, density) {
        val w = with(density) { currentPaperSize.widthDp.roundToPx() }
        val h = with(density) { currentPaperSize.heightDp.roundToPx() }
        val bmp = ImageBitmap(w, h)
        val nativeCanvas = android.graphics.Canvas(bmp.asAndroidBitmap())
        
        nativeCanvas.drawColor(paperColor.toArgb())
        
        val random = java.util.Random(123)
        val fiberPaint = Paint().apply {
            color = android.graphics.Color.argb(25, 100, 80, 60)
            strokeWidth = 0.8f
            isAntiAlias = true
        }
        
        // Draw 5000 microscopic wood pulp fibers
        for (i in 0 until 5000) {
            val x1 = random.nextFloat() * w
            val y1 = random.nextFloat() * h
            val angle = random.nextFloat() * Math.PI
            val len = random.nextFloat() * 6f + 2f
            val x2 = x1 + (len * Math.cos(angle)).toFloat()
            val y2 = y1 + (len * Math.sin(angle)).toFloat()
            nativeCanvas.drawLine(x1, y1, x2, y2, fiberPaint)
        }
        
        // Draw subtle paper mottling/shadows
        val mottlePaint = Paint().apply { color = android.graphics.Color.argb(10, 0, 0, 0) }
        for (i in 0 until 2000) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            val r = random.nextFloat() * 15f + 5f
            nativeCanvas.drawCircle(x, y, r, mottlePaint)
        }
        
        bmp
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFD7CCC8))) {
        
        // THE PAPER SHEET
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(isPanMode) {
                    if (isPanMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 4.0f)
                            offset = Offset(offset.x + pan.x, offset.y + pan.y)
                        }
                    }
                }
                .width(currentPaperSize.widthDp)
                .height(currentPaperSize.heightDp)
                .shadow(elevation = 12.dp, spotColor = Color.Black) 
                .background(paperColor)
        ) {
            // Layer 1: Paper Grain Texture
            Image(
                bitmap = paperTexture,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // Layer 2: The Ink (Text)
            BasicTextField(
                value = text,
                onValueChange = { if (!isPanMode) text = it },
                enabled = !isPanMode,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Cursive,
                    fontSize = 36.sp, 
                    lineHeight = lineSpacing.value.sp,
                    color = currentPen.color
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = marginX + 12.dp, top = 12.dp, end = 24.dp, bottom = 24.dp)
                    .blur(currentPen.blur) // Simulates ink bleeding into fibers
            )

            // Layer 3: The Ruled Lines (Multiply Blend)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                val lineSpacingPx = lineSpacing.toPx()
                val marginXPx = marginX.toPx()
                
                var y = lineSpacingPx
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 2f, blendMode = BlendMode.Multiply)
                    y += lineSpacingPx
                }
                drawLine(marginColor, Offset(marginXPx, 0f), Offset(marginXPx, size.height), 3f, blendMode = BlendMode.Multiply)
            }
        }

        // TOP CONTROL BAR
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).fillMaxWidth(), 
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            PaperSize.entries.forEach { size ->
                Button(
                    onClick = { currentPaperSize = size },
                    colors = ButtonDefaults.buttonColors(if (currentPaperSize == size) Color(0xFF1A237E) else Color(0xFF8D6E63))
                ) { Text(size.label, color = Color.White, fontSize = 12.sp) }
            }
            
            PenType.entries.forEach { pen ->
                Button(
                    onClick = { currentPen = pen },
                    colors = ButtonDefaults.buttonColors(if (currentPen == pen) Color(0xFF00695C) else Color(0xFF546E7A))
                ) { Text(pen.label, color = Color.White, fontSize = 10.sp) }
            }

            Button(
                onClick = { isPanMode = !isPanMode },
                colors = ButtonDefaults.buttonColors(if (isPanMode) Color(0xFFD84315) else Color(0xFF1565C0))
            ) { Text(if (isPanMode) "Pan ON" else "Type", color = Color.White, fontSize = 12.sp) }

            // EXPORT PNG ENGINE
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val w = with(density) { currentPaperSize.widthDp.roundToPx() }
                            val h = with(density) { currentPaperSize.heightDp.roundToPx() }
                            
                            // 1. Clone the paper texture
                            val exportBitmap = paperTexture.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
                            val exportCanvas = android.graphics.Canvas(exportBitmap)
                            
                            // 2. Draw Lines with Native Multiply Blending
                            val linePaint = Paint().apply { 
                                color = lineColor.toArgb(); strokeWidth = 2f; isAntiAlias = true
                                xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                            }
                            val marginXPx = with(density) { marginX.toPx() }
                            val lineSpacingPx = with(density) { lineSpacing.toPx() }
                            
                            var y = lineSpacingPx
                            while (y < h) {
                                exportCanvas.drawLine(0f, y, w.toFloat(), y, linePaint)
                                y += lineSpacingPx
                            }
                            val marginPaint = Paint().apply { 
                                color = marginColor.toArgb(); strokeWidth = 3f; isAntiAlias = true
                                xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                            }
                            exportCanvas.drawLine(marginXPx, 0f, marginXPx, h.toFloat(), marginPaint)
                            
                            // 3. Draw Text using StaticLayout (Perfect Word Wrap & Language Support)
                            val textPaint = TextPaint().apply {
                                color = currentPen.color.toArgb()
                                textSize = with(density) { 36.sp.toPx() }
                                typeface = Typeface.create("cursive", currentPen.typefaceStyle)
                                isAntiAlias = true
                            }
                            val textWidth = w - marginXPx.toInt() - with(density) { 24.dp.toPx().toInt() }
                            val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth).build()
                            
                            exportCanvas.save()
                            exportCanvas.translate(marginXPx + with(density) { 12.dp.toPx() }, with(density) { 12.dp.toPx() })
                            layout.draw(exportCanvas)
                            exportCanvas.restore()
                            
                            // 4. Save to Gallery
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, "Homework_${System.currentTimeMillis()}.png")
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HomeCil")
                            }
                            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            uri?.let {
                                context.contentResolver.openOutputStream(it)?.use { stream ->
                                    exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                }
                            }
                            
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Saved to Pictures/HomeCil!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(Color(0xFF2E7D32))
            ) { Text("Export", color = Color.White, fontSize = 12.sp) }
        }
    }
}