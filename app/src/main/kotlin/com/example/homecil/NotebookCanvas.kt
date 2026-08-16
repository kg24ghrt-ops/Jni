package com.example.homecil

import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import kotlin.math.sqrt

// 1. The Ink Data Model
data class InkPoint(val x: Float, val y: Float, val pressure: Float, val time: Long)
class Stroke {
    val points = mutableListOf<InkPoint>()
}

@Composable
fun NotebookCanvas() {
    val strokes = remember { mutableStateListOf<Stroke>() }
    val currentStroke = remember { mutableStateOf<Stroke?>(null) }

    // 2. AGSL Shader: Procedural Notebook Paper (College Ruled)
    val paperShaderCode = """
        uniform float2 resolution;
        half4 main(float2 fragCoord) {
            half3 base = half3(0.97, 0.96, 0.92); // Off-white paper
            float n1 = sin(fragCoord.x * 0.15) * cos(fragCoord.y * 0.2) * 0.015;
            float n2 = sin(fragCoord.x * 0.8 + fragCoord.y * 0.5) * 0.01;
            base += half3(n1 + n2); // Paper fiber noise
            
            float lineSpacing = 75.0; 
            float lineY = abs(fragCoord.y % lineSpacing);
            float hLine = smoothstep(1.5, 0.0, lineY - 1.0);
            half3 blueLine = half3(0.55, 0.75, 0.95); // Soft blue
            
            float marginX = 120.0;
            float vLine = abs(fragCoord.x - marginX);
            float redLine = smoothstep(2.0, 0.0, vLine - 1.5);
            half3 redCol = half3(0.9, 0.35, 0.35); // Red margin
            
            half3 finalColor = base;
            finalColor = mix(finalColor, blueLine, hLine * 0.6);
            finalColor = mix(finalColor, redCol, redLine * 0.7);
            
            return half4(finalColor, 1.0);
        }
    """

    // 3. Safe Shader Initialization
    val paperBrush = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val shader = RuntimeShader(paperShaderCode)
                ShaderBrush(shader)
            } else {
                SolidColor(Color(0xFFF8F6F0)) // Fallback for API 31/32
            }
        } catch (e: Exception) {
            SolidColor(Color(0xFFF8F6F0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(paperBrush)
            // 4. DMI ENGINE: Use pointerInteropFilter to get raw MotionEvent historical points
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val newStroke = Stroke()
                        newStroke.points.add(
                            InkPoint(motionEvent.x, motionEvent.y, motionEvent.pressure, motionEvent.eventTime)
                        )
                        currentStroke.value = newStroke
                    }
                    MotionEvent.ACTION_MOVE -> {
                        currentStroke.value?.let { stroke ->
                            // CRITICAL FOR REALISM: Extract historical points for zero-latency stylus feel
                            val historySize = motionEvent.historySize
                            for (i in 0 until historySize) {
                                stroke.points.add(
                                    InkPoint(
                                        motionEvent.getHistoricalX(i),
                                        motionEvent.getHistoricalY(i),
                                        motionEvent.getHistoricalPressure(i),
                                        motionEvent.getHistoricalEventTime(i)
                                    )
                                )
                            }
                            // Add current position
                            stroke.points.add(
                                InkPoint(motionEvent.x, motionEvent.y, motionEvent.pressure, motionEvent.eventTime)
                            )
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        currentStroke.value?.let { strokes.add(it) }
                        currentStroke.value = null
                    }
                }
                true // Consume the touch/stylus event
            }
            // 5. RENDERING ENGINE
            .drawBehind {
                strokes.forEach { stroke -> drawStroke(stroke) }
                currentStroke.value?.let { drawStroke(it) }
            }
    )
}

// 6. Realistic Ink Physics
fun DrawScope.drawStroke(stroke: Stroke) {
    if (stroke.points.size < 2) return

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = android.graphics.Color.parseColor("#1A237E") // Dark realistic ink
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            // THE SECRET: Multiply blend mode makes ink bleed into the blue lines
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = BlendMode.MULTIPLY
            }
        }

        for (i in 0 until stroke.points.size - 1) {
            val p1 = stroke.points[i]
            val p2 = stroke.points[i + 1]

            // Calculate velocity to simulate real pen physics
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val dist = sqrt(dx * dx + dy * dy)
            val dt = (p2.time - p1.time).coerceAtLeast(1)
            val velocity = dist / dt

            // Map velocity to width: Fast = thin, Slow = thick (ink pooling)
            val maxWidth = 14f
            val minWidth = 3f
            val speedFactor = (1.0f - (velocity / 3.0f).coerceIn(0f, 1f))
            var width = minWidth + (maxWidth - minWidth) * speedFactor

            // Apply Stylus Pressure if available
            if (p1.pressure > 0) width *= (0.5f + p1.pressure)

            paint.strokeWidth = width
            // Use nativeCanvas to draw with the Android Paint object
            canvas.nativeCanvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
    }
}