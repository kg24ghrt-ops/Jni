package com.example.homecil

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextShadow
import androidx.compose.ui.unit.dp

enum class PenType(val label: String, val baseColor: Color, val typefaceStyle: Int) {
    BALLPOINT("Ballpoint", Color(0xDD1A237E), Typeface.NORMAL),
    GEL("Gel Pen", Color(0xDD000000), Typeface.BOLD),
    FOUNTAIN("Fountain", Color(0xDD1A1A3A), Typeface.ITALIC)
}

object InkEngine {
    // Creates a streaky brush to simulate ballpoint ink sheen and uneven flow
    fun createInkBrush(baseColor: Color): Brush {
        val darkInk = baseColor.copy(alpha = 0.95f)
        val sheenInk = baseColor.copy(alpha = 0.70f) // Lighter where the ball slips
        val poolInk = baseColor.copy(alpha = 1.0f)   // Darker where ink pools

        return ShaderBrush(
            LinearGradientShader(
                colors = listOf(darkInk, sheenInk, darkInk, poolInk, darkInk),
                from = Offset(0f, 0f),
                to = Offset(80f, 80f), // Diagonal streaks
                tileMode = TileMode.Mirror
            )
        )
    }

    // Simulates ink bleeding into paper fibers
    fun getInkShadow(baseColor: Color): TextShadow {
        return TextShadow(
            color = baseColor.copy(alpha = 0.25f),
            offset = Offset(0.5f, 0.5f),
            blurRadius = 1.2f
        )
    }
}