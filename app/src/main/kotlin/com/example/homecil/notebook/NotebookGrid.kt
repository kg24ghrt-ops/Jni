package com.example.homecil.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val topPaddingPx = 12.dp.toPx()

    val highlightBounds = remember(
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

            if (
                safeBottom >
                safeTop
            ) {
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
        }

        /*
         * Horizontal notebook ruling.
         *
         * Pre-calculate the number of lines to avoid repeated
         * comparison overhead in the while-loop.
         */
        if (spacingPx > 0f) {

            val lineCount =
                (size.height / spacingPx).toInt()

            for (i in 1..lineCount) {
                val y = spacingPx * i

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
                    strokeWidth = 2f
                )
            }
        }

        /*
         * Margin.
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
                strokeWidth = 3f
            )
        }
    }
}