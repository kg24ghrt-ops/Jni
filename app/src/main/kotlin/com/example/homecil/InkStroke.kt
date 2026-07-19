package com.example.homecil

import android.graphics.Bitmap

data class InkStroke(
    val char: String,
    val inkBitmap: Bitmap,
    val x: Int,
    val y: Int       // baseline y coordinate
)