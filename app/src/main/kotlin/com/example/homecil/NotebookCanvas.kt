package com.example.homecil

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun NotebookCanvas() {
    var currentPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var currentPen by remember { mutableStateOf(PenType.BALLPOINT) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var currentLineIndex by remember { mutableStateOf(-1) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var isMarginMode by remember { mutableStateOf(false) }

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

    LaunchedEffect(textFieldValue.selection, layoutResult) {
        layoutResult?.let { result ->
            val offset = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
            currentLineIndex = result.getLineForOffset(offset)
        }
    }

    val paperTexture = remember(currentPaperSize, density) {
        PaperEngine.generateTexture(currentPaperSize, density, paperColor)
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
                    .background(paperColor)
            ) {
                Image(bitmap = paperTexture, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (currentLineIndex >= 0 && layoutResult != null) {
                        val highlightColor = Color(0x40FFEB3B)
                        val textTopPadding = with(density) { 12.dp.toPx() }
                        val lineTop = layoutResult!!.getLineTop(currentLineIndex) + textTopPadding
                        val lineBottom = layoutResult!!.getLineBottom(currentLineIndex) + textTopPadding
                        drawRect(color = highlightColor, topLeft = Offset(0f, lineTop), size = Size(size.width, lineBottom - lineTop))
                    }

                    val lineSpacingPx = lineSpacing.toPx()
                    val marginXPx = marginX.toPx()
                    var y = lineSpacingPx
                    while (y < size.height) {
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 2f)
                        y += lineSpacingPx
                    }
                    drawLine(marginColor, Offset(marginXPx, 0f), Offset(marginXPx, size.height), 3f)
                }

                val inkBrush = remember(currentPen) { InkEngine.createInkBrush(currentPen.baseColor) }
                val inkShadow = remember(currentPen) { InkEngine.getInkShadow(currentPen.baseColor) }

                val textStartPadding = if (isMarginMode) 12.dp else (marginX + 12.dp)

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = {
                        if (!isPanMode) {
                            textFieldValue = it
                            if (it.text.contains("\n") && isMarginMode) {
                                isMarginMode = false
                            }
                        }
                    },
                    onTextLayout = { layoutResult = it },
                    enabled = !isPanMode,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Default,
                        brush = inkBrush,
                        shadow = inkShadow,
                        fontSize = with(density) { 36.dp.toPx().toSp() },
                        lineHeight = with(density) { lineSpacing.toPx().toSp() }
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = -0.4f }
                        .padding(start = textStartPadding, top = 12.dp, end = 24.dp, bottom = 24.dp)
                        .then(if (!isPanMode) Modifier.verticalScroll(scrollState) else Modifier)
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
                    onClick = { isMarginMode = !isMarginMode },
                    colors = ButtonDefaults.buttonColors(if (isMarginMode) Color(0xFF6A1B9A) else Color(0xFF424242)),
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(if (isMarginMode) "Margin" else "Indent", color = Color.White) }
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
                        coroutineScope.launch {
                            ExportEngine.exportToPng(
                                context = context,
                                density = density,
                                paperSize = currentPaperSize,
                                paperTexture = paperTexture,
                                text = textFieldValue.text,
                                penType = currentPen,
                                lineSpacingDp = lineSpacing,
                                marginXDp = marginX
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(Color(0xFF2E7D32))
                ) { Text("Export PNG", color = Color.White) }
            }
        }
    }
}