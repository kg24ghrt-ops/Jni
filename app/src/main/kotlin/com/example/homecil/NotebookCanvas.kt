package com.example.homecil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.Dp //
</think>

The compiler error `Unresolved reference 'Dp'` happens because I included the lowercase `dp` extension (for values like `50.dp`) in the imports, but forgot to import the uppercase `Dp` class (the type declaration used in `val widthDp: Dp`). 

Here is the exact, fully corrected `NotebookCanvas.kt` with the missing import added.

### Replace your `NotebookCanvas.kt` completely with this:

```kotlin
package com.example.homecil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.Dp // <--- THIS IS THE FIX (Added missing import)
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Real-world dimensions mapped to Android DP (1 mm ≈ 6.3 dp at standard density)
enum class PaperSize(val widthDp: Dp, val heightDp: Dp, val label: String) {
    A4(1323.dp, 1871.dp, "A4 (210x297mm)"),
    A5(932.dp, 1323.dp, "A5 (148x210mm)")
}

@Composable
fun NotebookCanvas() {
    var currentPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var text by remember { mutableStateOf("") }

    // Realistic School Notebook Dimensions (Standard 8mm ruled spacing)
    val lineSpacing = 50.dp // ~8mm spacing
    val marginX = 220.dp    // ~35mm left margin
    
    val paperColor = Color(0xFFFBF9F2)
    val lineColor = Color(0xFFA9C2D9)
    val marginColor = Color(0xFFE57373)
    val inkColor = Color(0xDD1A237E)
    val handwritingFont = FontFamily.Cursive

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFD7CCC8))) { // Warm desk background
        
        // Paper Size Toggle
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

        // Scrollable Paper Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            // The Paper Sheet
            Box(
                modifier = Modifier
                    .width(currentPaperSize.widthDp)
                    .height(currentPaperSize.heightDp)
                    .padding(vertical = 24.dp)
                    .shadow(elevation = 12.dp, spotColor = Color.Black) // Realistic paper depth
                    .background(paperColor)
            ) {
                // LAYER 1: The Text (Ink)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontFamily = handwritingFont,
                        fontSize = 36.sp, // ~6mm text height
                        lineHeight = lineSpacing.value.sp,
                        color = inkColor
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = marginX + 12.dp, top = 12.dp, end = 24.dp, bottom = 24.dp)
                        // THE INK BLEED EFFECT: Simulates liquid ink absorbing into paper fibers
                        .blur(0.6.dp, 0.6.dp) 
                )

                // LAYER 2: The Notebook Lines (Drawn OVER text with Multiply BlendMode)
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
                            blendMode = BlendMode.Multiply // Physically darkens the ink it crosses
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