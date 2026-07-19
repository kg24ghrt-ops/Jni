package com.example.homecil

object NativeRenderer {
    init {
        System.loadLibrary("paperrender")
    }

    // Returns a Bitmap (ARGB_8888) of size 800x1000
    external fun renderPaper(paperType: Int): android.graphics.Bitmap
}