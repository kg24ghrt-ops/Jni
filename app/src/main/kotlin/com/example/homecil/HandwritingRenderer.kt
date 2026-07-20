package com.example.homecil

import android.graphics.*
import android.icu.text.BreakIterator
import android.text.TextPaint
import java.util.*
import kotlin.math.ceil

object HandwritingRenderer {
    val INK_COLOR = Color.rgb(0x1A, 0x4D, 0x8C)

    /**
     * Creates realistic ink strokes for a line of text.
     * The whole line is rendered monolithically for correct shaping,
     * then sliced into grapheme clusters for independent distortion.
     */
    fun createClusterStrokes(
        text: String,
        startX: Float,
        baselineY: Float,
        paint: Paint
    ): List<InkStroke> {
        if (text.isEmpty()) return emptyList()

        val textPaint = TextPaint(paint).apply {
            color = INK_COLOR
            isAntiAlias = true
        }
        val fm = textPaint.fontMetrics

        // 1. Monolithic bitmap of the full shaped line
        val fullWidth = ceil(textPaint.measureText(text)).toInt() + 4
        val fullHeight = ceil(fm.descent - fm.ascent).toInt() + 4
        if (fullWidth <= 0 || fullHeight <= 0) return emptyList()

        val fullBmp = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888)
        val fullCanvas = Canvas(fullBmp)
        fullCanvas.drawText(text, 0f, -fm.ascent + 2f, textPaint)

        // 2. Split into grapheme clusters
        val clusters = breakIntoClusters(text)

        val strokes = mutableListOf<InkStroke>()
        val random = Random(42)

        // Cumulative positions measured exactly (not accumulated from widths)
        var startIndex = 0   // current position in the original text

        for ((clusterIdx, cluster) in clusters.withIndex()) {
            val endIndex = startIndex + cluster.length

            // exact offset of the cluster's left edge in the full bitmap
            val left = textPaint.measureText(text, 0, startIndex)
            // exact offset of the right edge
            val right = textPaint.measureText(text, 0, endIndex)
            val clusterWidth = right - left
            if (clusterWidth <= 0f) {
                startIndex = endIndex
                continue
            }

            // 3. Extract cluster sub‑bitmap from the monolithic render
            val subWidth = ceil(clusterWidth).toInt() + 2
            val subHeight = fullHeight
            val subBmp = Bitmap.createBitmap(subWidth, subHeight, Bitmap.Config.ARGB_8888)
            val subCanvas = Canvas(subBmp)
            val srcRect = Rect(
                left.toInt(), 0,
                right.toInt(), fullHeight
            )
            val dstRect = Rect(0, 0, subWidth, subHeight)
            subCanvas.drawBitmap(fullBmp, srcRect, dstRect, null)

            // 4. Apply distortion with unique seed
            PaperRenderer.distortBitmap(subBmp, 2.0f, clusterIdx * 131 + 17)

            // 5. Jitter and paper placement
            val jitterX = (random.nextFloat() * 4f - 2f)
            val jitterY = (random.nextFloat() * 6f - 3f)
            val paperX = startX + left + jitterX
            val paperY = baselineY + fm.ascent + jitterY - 2f

            strokes.add(
                InkStroke(
                    char = cluster,
                    inkBitmap = subBmp,
                    x = paperX.toInt(),
                    y = paperY.toInt()
                )
            )

            startIndex = endIndex
        }

        return strokes
    }

    /** Splits text into grapheme clusters using ICU. */
    private fun breakIntoClusters(text: String): List<String> {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val iterator = BreakIterator.getCharacterInstance(Locale.getDefault())
            iterator.setText(text)
            val clusters = mutableListOf<String>()
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                clusters.add(text.substring(start, end))
                start = end
                end = iterator.next()
            }
            return clusters
        }
        return text.map { it.toString() }
    }
}