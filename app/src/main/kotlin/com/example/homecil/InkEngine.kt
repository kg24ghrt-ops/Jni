package com.example.homecil

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow // FIXED: Was TextShadow
import androidx.compose.ui.graphics.TileMode

enum class PenType(val label: String, val baseColor: Color, val typefaceStyle: Int) {
    BALLPOINT("Ballpoint", Color(0xDD1A237E), Typeface.NORMAL),
    GEL("Gel Pen", Color(0xDD000000), Typeface.BOLD),
    FOUNTAIN("Fountain", Color(0xDD1A1A3A), Typeface.ITALIC)
}

object InkEngine {
    fun createInkBrush(baseColor: Color): Brush {
        val darkInk = baseColor.copy(alpha = 0.95f)
        val sheenInk = baseColor.copy(alpha = 0.70f)
        val poolInk = baseColor.copy(alpha = 1.0f)

        return Brush.linearGradient(
            colors = listOf(darkInk, sheenInk, darkInk, poolInk, darkInk),
            start = Offset(0f, 0f),
            end = Offset(80f, 80f),
            tileMode = TileMode.Mirror
        )
    }

    fun getInkShadow(baseColor: Color): Shadow {
        return Shadow(
            color = baseColor.copy(alpha = 0.25f),
            offset = Offset(0.5f, 0.5f),
            blurRadius = 1.2f
        )
    }
}