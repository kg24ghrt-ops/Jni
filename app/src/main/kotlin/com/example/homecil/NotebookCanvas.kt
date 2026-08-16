package com.example.homecil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PaperSize(val widthDp: Dp, val heightDp: Dp, val label: String) {
    A4(1323.dp, 1871.dp, "A4 (210x297mm)"),
    A5(932.dp, 1323.dp, "A5 (148x210mm)")
}

@Composable
fun NotebookCanvas() {
    var currentPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var text by remember { mutableStateOf("") }

    val lineSpacing = 50.dp 
    val marginX = 220.dp    
    
    val paperColor = Color(0xFFFBF9F2)
    val lineColor = Color(0xFFA9C2D9)
    val marginColor = Color(0xFFE57373)
    val inkColor = Color(0xDD1A237E)
    val handwritingFont = FontFamily.Cursive

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFD7CCC8))) {
        
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PaperSize.entries.forEach { size ->
                Button(
                    onClick = { currentPaperSize = size },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentPaperSize == size) Color(0xFF1A237E) else Color(0xFF8D6E63)
                    )
                ) {
                    Text(size.label, color = Color.White)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .width(currentPaperSize.widthDp)
                    .height(currentPaperSize.heightDp)
                    .padding(vertical = 24.dp)
                    .shadow(elevation = 12.dp, spotColor = Color.Black) 
                    .background(paperColor)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
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
        }
    }
}