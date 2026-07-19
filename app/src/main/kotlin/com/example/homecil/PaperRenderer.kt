package com.example.homecil

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteBuffer

object PaperRenderer {
    // JNI native method – generates paper texture and fills a Bitmap.
    // The Bitmap must be mutable and in ARGB_8888 config.
    external fun renderPaper(bitmap: Bitmap, width: Int, height: Int)

    init {
        System.loadLibrary("native-lib")
    }

    /**
     * Creates a realistic paper bitmap of the given size.
     */
    fun createPaperBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderPaper(bitmap, width, height)
        return bitmap
    }
}