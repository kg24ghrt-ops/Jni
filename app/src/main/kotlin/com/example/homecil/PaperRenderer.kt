package com.example.homecil

import android.graphics.Bitmap

object PaperRenderer {

    /**
     * JNI – generates lined notebook paper.
     * @param lineThickness thickness of lines in pixels (≥1)
     */
    private external fun renderPaper(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        lineSpacing: Int,
        marginLeft: Int,
        lineColor: Int,
        marginColor: Int,
        lineThickness: Int
    )

    init {
        System.loadLibrary("native-lib")
    }

    /**
     * Creates a school‑notebook‑style bitmap.
     *
     * @param widthPx       target width in pixels
     * @param heightPx      target height in pixels
     * @param lineSpacingPx distance between blue lines (pixels)
     * @param marginLeftPx  distance of red margin from the left edge (pixels)
     * @param lineThicknessPx thickness of lines in pixels (e.g., 3 for 2dp on mdpi)
     * @param lineColor     ARGB for horizontal lines
     * @param marginColor   ARGB for margin line
     */
    fun createNotebookPaper(
        widthPx: Int,
        heightPx: Int,
        lineSpacingPx: Int,
        marginLeftPx: Int,
        lineThicknessPx: Int,
        lineColor: Int = android.graphics.Color.rgb(0, 0x66, 0xCC),
        marginColor: Int = android.graphics.Color.RED
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        renderPaper(bitmap, widthPx, heightPx, lineSpacingPx, marginLeftPx, lineColor, marginColor, lineThicknessPx)
        return bitmap
    }
}