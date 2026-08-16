package com.example.homecil

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import kotlin.math.sqrt

// Data model for vector strokes (Safe, memory-efficient, exportable)
data class InkPoint(val x: Float, val y: Float, val pressure: Float, val time: Long)
class Stroke {
    val points = mutableListOf<InkPoint>()
    fun addPoint(x: Float, y: Float, p: Float, t: Long) {
        points.add(InkPoint(x, y, p, t))
    }
}

@Composable
fun NotebookCanvas() {
    val strokes = remember { mutableStateListOf<Stroke>() }
    val currentStroke = remember { mutableStateOf<Stroke?>(null) }

    // AGSL Shader: Procedural Notebook Paper (College Ruled)
    val paperShaderCode = """
        uniform float2 resolution;
        half4 main(float2 fragCoord) {
            // Base off-white notebook paper color
            half3 base = half3(0.97, 0.96, 0.92);
            
            // Procedural paper fiber noise
            float n1 = sin(fragCoord.x * 0.15) * cos(fragCoord.y * 0.2) * 0.015;
            float n2 = sin(fragCoord.x * 0.8 + fragCoord.y * 0.5) * 0.01;
            base += half3(n1 + n2);
            
            // College Ruled Lines (~7.1mm spacing scaled to pixels)
            float lineSpacing = 75.0; 
            float lineY = abs(fragCoord.y % lineSpacing);
            float hLine = smoothstep(1.5, 0.0, lineY - 1.0);
            half3 blueLine = half3(0.55, 0.75, 0.95); // Soft blue
            
            // Red Margin Line
            float marginX = 120.0;
            float vLine = abs(fragCoord.x - marginX);
            float redLine = smoothstep(2.0, 0.0, vLine - 1.5);
            half3 redCol = half3(0.9, 0.35, 0.35);
            
            // Combine
            half3 finalColor = base;
            finalColor = mix(finalColor, blueLine, hLine * 0.6);
            finalColor = mix(finalColor, redCol, redLine * 0.7);
            
            return half4(finalColor, 1.0);
        }
    """

    val paperBrush = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val shader = RuntimeShader(paperShaderCode)
                ShaderBrush(shader)
            } else {
                SolidColor(Color(0xFFF8F6F0)) // Fallback for older devices
            }
        } catch (e: Exception) {
            SolidColor(Color(0xFFF8F6F0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(paperBrush) // GPU-accelerated paper background
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startTime = down.uptimeMillis
                    val newStroke = Stroke()
                    newStroke.addPoint(down.position.x, down.position.y, down.pressure, startTime)
                    currentStroke.value = newStroke
                    
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change != null) {
                            // DMI: Capture historical points for zero-latency stylus feel
                            val histCount = change.historicalCount
                            for (i in 0 until histCount) {
                                newStroke.addPoint(
                                    change.getHistoricalX(i),
                                    change.getHistoricalY(i),
                                    change.getHistoricalPressure(i),
                                    change.getHistoricalEventTime(i)
                                )
                            }
                            newStroke.addPoint(
                                change.position.x,
                                change.position.y,
                                change.pressure,
                                change.uptimeMillis
                            )
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    
                    strokes.add(newStroke)
                    currentStroke.value = null
                }
            }
            .drawWithCache {
                onDrawBehind {
                    strokes.forEach { stroke -> drawStroke(stroke) }
                    currentStroke.value?.let { drawStroke(it) }
                }
            }
    )
}

// Realistic Ink Rendering Engine
fun DrawScope.drawStroke(stroke: Stroke) {
    if (stroke.points.size < 2) return
    
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = Color(0xFF151A2E) // Dark realistic ink blue/black
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
            // THE SECRET SAUCE: Multiply blend mode makes ink bleed into the paper lines
            blendMode = BlendMode.Multiply 
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
            val maxWidth = 12f
            val minWidth = 2f
            val speedFactor = (1.0f - (velocity / 3.0f).coerceIn(0f, 1f))
            var width = minWidth + (maxWidth - minWidth) * speedFactor
            
            // Apply pressure sensitivity if using a stylus
            if (p1.pressure > 0) {
                width *= (0.5f + p1.pressure)
            }
            
            paint.strokeWidth = width
            canvas.drawLine(Offset(p1.x, p1.y), Offset(p2.x, p2.y), paint)
        }
    }
}