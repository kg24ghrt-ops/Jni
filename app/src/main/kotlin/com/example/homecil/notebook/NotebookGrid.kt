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
        if (
            layoutResult != null &&
            activeLine >= 0 &&
            activeLine < layoutResult.lineCount
        ) {
            val topPadding =
                12.dp.toPx()

            val top =
                layoutResult.getLineTop(
                    activeLine
                ) + topPadding

            val bottom =
                layoutResult.getLineBottom(
                    activeLine
                ) + topPadding

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
         */
        if (spacingPx > 0f) {

            var y = spacingPx

            while (
                y < size.height
            ) {
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

                y += spacingPx
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