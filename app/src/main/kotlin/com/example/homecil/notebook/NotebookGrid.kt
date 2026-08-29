package com.example.homecil.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    showMargin: Boolean,
    rulingConfig: RulingConfig = NotebookRuling.COLLEGE
) {
    val lineColor = rulingConfig.lineColor
    val marginColor = rulingConfig.marginColor
    val highlightColor = Color(0x40FFEB3B)

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

    val showHeaderSpace = rulingConfig.showHeaderSpace
    val headerHeight = rulingConfig.headerHeight
    val showVerticalLines = rulingConfig.showVerticalLines
    val verticalLineSpacing = rulingConfig.verticalLineSpacing
    val lineWidth = rulingConfig.lineWidth

    Canvas(
        modifier = modifier
    ) {
        val spacingPx = lineSpacing.toPx()
        val marginPx = marginX.toPx()
        val headerHeightPx = headerHeight.toPx()
        val verticalSpacingPx = verticalLineSpacing.toPx()

        /*
         * Header space - blank area at the top of the page
         */
        if (showHeaderSpace && headerHeightPx > 0f) {
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, headerHeightPx)
            )
        }

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
         * Start below header space if enabled.
         */
        val startY = if (showHeaderSpace) headerHeightPx else 0f
        val availableHeight = size.height - startY
        val lineCount = (availableHeight / spacingPx).toInt()

        // Use Path for optimized batch drawing
        val path = Path()
        
        // Add horizontal lines
        for (i in 0..lineCount) {
            val y = startY + i * spacingPx
            path.moveTo(0f, y)
            path.lineTo(size.width, y)
        }
        
        // Add vertical graph lines if enabled
        if (showVerticalLines && verticalSpacingPx > 0f) {
            val vertLineCount = (size.width / verticalSpacingPx).toInt()
            for (i in 0..vertLineCount) {
                val x = i * verticalSpacingPx
                path.moveTo(x, 0f)
                path.lineTo(x, size.height)
            }
        }
        
        // Draw all lines at once
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = lineWidth)
        )

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
                strokeWidth = lineWidth
            )
        }
    }
}
