package com.example.homecil.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun NotebookGrid(
    modifier: Modifier,
    lineSpacing: Dp,
    marginX: Dp,
    layoutResult: TextLayoutResult?,
    activeLine: Int,
    showMargin: Boolean
) {
    val lineColor =
        Color(0xFFA9C2D9)

    val marginColor =
        Color(0xFFE57373)

    val highlightColor =
        Color(0x40FFEB3B)

    /*
     * Pre-compute the cursor-line highlight bounds outside the
     * Canvas lambda. This avoids calling getLineTop() /
     * getLineBottom() on every single draw call when only the
     * highlight position changes.
     *
     * Note: Dp.toPx() requires a Density context receiver which
     * is only available in @Composable scope. The padding value
     * is computed here and passed into the remember block.
     */
    val topPaddingPx = with(LocalDensity.current) { 12.dp.toPx() }

    val highlightBounds: Pair<Float, Float>? = remember(
        layoutResult,
        activeLine,
        topPaddingPx
    ) {
        if (
            layoutResult != null &&
            activeLine >= 0 &&
            activeLine < layoutResult.lineCount
        ) {
            val rawTop =
                layoutResult.getLineTop(
                    activeLine
                ) + topPaddingPx

            val rawBottom =
                layoutResult.getLineBottom(
                    activeLine
                ) + topPaddingPx

            Pair(rawTop, rawBottom)
        } else {
            null
        }
    }

    Canvas(
        modifier = modifier
    ) {
        val spacingPx =
            lineSpacing.toPx()

        val marginPx =
            marginX.toPx()

        /*
         * Cursor line highlight.
         */
        highlightBounds?.let { (top, bottom) ->
            val safeTop =
                top.coerceIn(
                    0f,
                    size.height
                )

            val safeBottom =
                bottom.coerceIn(
                    safeTop,
                    size.height
                )

            drawRect(
                color = highlightColor,
                topLeft = Offset(
                    0f,
                    safeTop
                ),
                size = Size(
                    size.width,
                    safeBottom - safeTop
                )
            )
        }

        /*
         * Horizontal ruling lines.
         */
        val lineCount =
            (size.height / spacingPx).toInt()

        for (i in 0..lineCount) {
            val y =
                i * spacingPx

            drawLine(
                color = lineColor,
                start = Offset(
                    0f,
                    y
                ),
                end = Offset(
                    size.width,
                    y
                ),
                strokeWidth = 0.5f
            )
        }

        /*
         * Vertical margin line.
         */
        if (showMargin) {
            drawLine(
                color = marginColor,
                start = Offset(
                    marginPx,
                    0f
                ),
                end = Offset(
                    marginPx,
                    size.height
                ),
                strokeWidth = 0.5f
            )
        }
    }
}
