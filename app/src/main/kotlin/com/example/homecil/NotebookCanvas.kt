package com.example.homecil

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalComposeUiApi::class) // Kept just in case, though no longer strictly needed
@Composable
fun NotebookCanvas() {
    // 1. PAPER DIMENSIONS
    val lineSpacing = 40.dp // Distance between blue lines
    val marginX = 60.dp     // Position of the red margin line
    
    // 2. COLORS
    val paperColor = Color(0xFFFBF9F2) // Off-white realistic paper
    val lineColor = Color(0xFFA9C2D9)  // Soft notebook blue
    val marginColor = Color(0xFFE57373) // Soft red margin
    val inkColor = Color(0xDD1A237E)   // Dark, realistic ink blue

    // 3. FONT (Using system Cursive for instant compatibility. 
    // For max realism, download "Caveat.ttf", put it in res/font/, and use FontFamily(Font(R.font.caveat)))
    val handwritingFont = FontFamily.Cursive 

    var text by remember { 
        mutableStateOf("Start typing your homework here...\n\nThe ink will physically bleed into the paper lines.") 
    }

    Box(modifier = Modifier.fillMaxSize().background(paperColor)) {
        
        // --- LAYER 1: THE NOTEBOOK PAPER (GPU Accelerated Canvas) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineSpacingPx = lineSpacing.toPx()
            val marginXPx = marginX.toPx()
            
            // Draw Blue Horizontal Lines
            var y = lineSpacingPx
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                y += lineSpacingPx
            }
            
            // Draw Red Vertical Margin Line
            drawLine(marginColor, Offset(marginXPx, 0f), Offset(marginXPx, size.height), strokeWidth = 2f)
        }

        // --- LAYER 2: THE TYPING SURFACE (Realistic Ink Engine) ---
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(
                fontFamily = handwritingFont,
                fontSize = 26.sp,
                // CRITICAL: lineHeight matches the blue line spacing perfectly
                lineHeight = lineSpacing.value.sp, 
                color = inkColor
            ),
            modifier = Modifier
                .fillMaxSize()
                // Padding aligns the text baseline exactly onto the first blue line
                .padding(start = marginX + 12.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState())
                .graphicsLayer {
                    // THE SECRET SAUCE 1: Multiply blend mode makes ink darken the blue lines
                    compositingStrategy = CompositingStrategy.Offscreen
                    blendMode = BlendMode.Multiply
                    
                    // THE SECRET SAUCE 2: Ink bleed effect (simulates liquid ink absorbing into paper)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect.createBlurEffect(0.8f, 0.8f, Shader.TileMode.DECAL)
                    }
                }
        )
    }
}