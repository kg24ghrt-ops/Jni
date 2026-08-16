package com.example.homecil

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PaperSize(val widthDp: Dp, val heightDp: Dp, val label: String) {
    A4(1323.dp, 1871.dp, "A4 (210x297mm)"),
    A5(932.dp, 1323.dp, "A5 (148x210mm)")
}

@Composable
fun NotebookCanvas() {
    var currentPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var text by remember { mutableStateOf("") }
    
    // Pan & Zoom State
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isPanMode by remember { mutableStateOf(false) } // Toggle to prevent touch conflicts with typing

    val lineSpacing = 50.dp 
    val marginX = 220.dp    
    
    val paperColor = Color(0xFFFBF9F2)
    val lineColor = Color(0xFFA9C2D9)
    val marginColor = Color(0xFFE57373)
    val inkColor = Color(0xDD1A237E)
    val handwritingFont = FontFamily.Cursive

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer() // API for capturing the composable to a bitmap

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFD7CCC8))) { // Desk background
        
        // The Paper Sheet (Pannable & Zoomable)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                // Pan & Zoom Logic
                .pointerInput(isPanMode) {
                    if (isPanMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 4.0f)
                            offset = Offset(
                                x = offset.x + pan.x,
                                y = offset.y + pan.y
                            )
                        }
                    }
                }
                .width(currentPaperSize.widthDp)
                .height(currentPaperSize.heightDp)
                .padding(vertical = 24.dp)
                .shadow(elevation = 12.dp, spotColor = Color.Black) 
                .background(paperColor)
                .drawWithContent {
                    // Records everything drawn inside this Box for PNG export
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                }
        ) {
            BasicTextField(
                value = text,
                onValueChange = { if (!isPanMode) text = it },
                enabled = !isPanMode, // Disabled in Pan Mode so gestures work
                textStyle = TextStyle(
                    fontFamily = handwritingFont,
                    fontSize = 36.sp, 
                    lineHeight = lineSpacing.value.sp,
                    color = inkColor
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = marginX + 12.dp, top = 12.dp, end = 24.dp, bottom = 24.dp)
                    .blur(0.6.dp, 0.6.dp) 
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                val lineSpacingPx = lineSpacing.toPx()
                val marginXPx = marginX.toPx()
                
                var y = lineSpacingPx
                while (y < size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2f,
                        blendMode = BlendMode.Multiply
                    )
                    y += lineSpacingPx
                }
                
                drawLine(
                    color = marginColor,
                    start = Offset(marginXPx, 0f),
                    end = Offset(marginXPx, size.height),
                    strokeWidth = 3f,
                    blendMode = BlendMode.Multiply
                )
            }
        }

        // Top Controls Bar (Stays fixed on screen)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PaperSize.entries.forEach { size ->
                Button(
                    onClick = { currentPaperSize = size },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentPaperSize == size) Color(0xFF1A237E) else Color(0xFF8D6E63)
                    )
                ) {
                    Text(size.label, color = Color.White, fontSize = 12.sp)
                }
            }

            // Toggle Mode Button
            Button(
                onClick = { 
                    isPanMode = !isPanMode 
                    if (isPanMode) {
                        // Reset view when entering pan mode
                        scale = 1f
                        offset = Offset.Zero
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPanMode) Color(0xFFD84315) else Color(0xFF1565C0)
                )
            ) {
                Text(if (isPanMode) "Pan Mode: ON" else "Type Mode", color = Color.White, fontSize = 12.sp)
            }

            // Export PNG Button
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // 1. Capture the paper to a Bitmap
                            val imageBitmap = graphicsLayer.toImageBitmap()
                            val bitmap = imageBitmap.asAndroidBitmap()
                            
                            // 2. Save to MediaStore (Pictures/HomeCil folder)
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, "Homework_${System.currentTimeMillis()}.png")
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HomeCil")
                            }
                            
                            val resolver = context.contentResolver
                            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            uri?.let {
                                resolver.openOutputStream(it)?.use { stream ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)) // Green
            ) {
                Text("Export PNG", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}