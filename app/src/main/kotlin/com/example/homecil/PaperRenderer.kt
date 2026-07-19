package com.example.homecil

import android.graphics.Bitmap

object PaperRenderer {
    // Paper texture generation
    external fun renderPaper(bitmap: Bitmap, width: Int, height: Int)

    // Ink simulation – blends an ink bitmap onto the paper
    external fun simulateInk(paperBitmap: Bitmap, inkBitmap: Bitmap, offsetX: Int, offsetY: Int)

    init {
        System.loadLibrary("native-lib")
    }

    fun createPaperBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderPaper(bitmap, width, height)
        return bitmap
    }
}