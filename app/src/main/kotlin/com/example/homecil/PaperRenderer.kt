package com.example.homecil

import android.graphics.Bitmap

object PaperRenderer {
    external fun renderPaper(bitmap: Bitmap, width: Int, height: Int)
    external fun simulateInk(paperBitmap: Bitmap, inkBitmap: Bitmap, offsetX: Int, offsetY: Int)
    external fun distortBitmap(bitmap: Bitmap, strength: Float)   // <-- new

    init {
        System.loadLibrary("native-lib")
    }

    fun createPaperBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderPaper(bitmap, width, height)
        return bitmap
    }
}