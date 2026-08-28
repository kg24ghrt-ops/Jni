package com.example.homecil.notebook

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

internal object NotebookViewportMath {

    private const val FIT_PADDING = 0.92f

    fun calculateFitScale(
        paperWidth: Float,
        paperHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Float {

        if (
            paperWidth <= 0f ||
            paperHeight <= 0f ||
            viewportWidth <= 0f ||
            viewportHeight <= 0f
        ) {
            return 1f
        }

        return min(
            (viewportWidth * FIT_PADDING) / paperWidth,
            (viewportHeight * FIT_PADDING) / paperHeight
        ).coerceIn(
            0.05f,
            10f
        )
    }

    fun calculateMaxPan(
        scaledWidth: Float,
        scaledHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Offset {
        return Offset(
            x = max(
                viewportWidth * 0.45f,
                scaledWidth * 0.5f
            ),
            y = max(
                viewportHeight * 0.45f,
                scaledHeight * 0.5f
            )
        )
    }

    fun clampOffset(
        offset: Offset,
        maxPan: Offset
    ): Offset {
        return Offset(
            x = offset.x.coerceIn(
                -maxPan.x,
                maxPan.x
            ),
            y = offset.y.coerceIn(
                -maxPan.y,
                maxPan.y
            )
        )
    }

    fun applyPan(
        current: Offset,
        pan: Offset,
        maxPan: Offset
    ): Offset {
        return clampOffset(
            current + pan,
            maxPan
        )
    }

    fun applyZoom(
        current: Float,
        gestureZoom: Float
    ): Float {

        if (
            !gestureZoom.isFinite() ||
            gestureZoom <= 0f
        ) {
            return current
        }

        return (
            current * gestureZoom
        ).coerceIn(
            NotebookUiState.MIN_ZOOM,
            NotebookUiState.MAX_ZOOM
        )
    }
}