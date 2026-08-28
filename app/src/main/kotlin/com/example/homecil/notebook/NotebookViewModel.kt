package com.example.homecil.notebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.example.homecil.PaperSize
import com.example.homecil.PenType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for notebook state.
 *
 * UI-only objects such as TextLayoutResult remain in Compose.
 * Editor/document/viewport state lives here.
 */
data class NotebookUiState(
    val paperSize: PaperSize = PaperSize.A4,
    val pen: PenType = PenType.BALLPOINT,
    val text: TextFieldValue = TextFieldValue(""),

    val marginMode: Boolean = false,
    val panMode: Boolean = false,

    val zoom: Float = 1f,
    val offset: Offset = Offset.Zero,

    val exporting: Boolean = false
) {
    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 6f
    }
}

class NotebookViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        NotebookUiState()
    )

    val uiState: StateFlow<NotebookUiState> =
        _uiState.asStateFlow()

    fun setPaperSize(
        value: PaperSize
    ) {
        _uiState.update {
            it.copy(
                paperSize = value,
                zoom = 1f,
                offset = Offset.Zero
            )
        }
    }

    fun setPen(
        value: PenType
    ) {
        _uiState.update {
            it.copy(
                pen = value
            )
        }
    }

    fun setText(
        value: TextFieldValue
    ) {
        _uiState.update {
            it.copy(
                text = value
            )
        }
    }

    fun toggleMarginMode() {
        _uiState.update {
            it.copy(
                marginMode = !it.marginMode
            )
        }
    }

    fun togglePanMode() {
        _uiState.update {
            it.copy(
                panMode = !it.panMode
            )
        }
    }

    fun setPanMode(
        enabled: Boolean
    ) {
        _uiState.update {
            it.copy(
                panMode = enabled
            )
        }
    }

    fun applyZoom(
        gestureZoom: Float
    ) {
        if (
            !gestureZoom.isFinite() ||
            gestureZoom <= 0f
        ) {
            return
        }

        _uiState.update {
            it.copy(
                zoom = (
                    it.zoom * gestureZoom
                ).coerceIn(
                    NotebookUiState.MIN_ZOOM,
                    NotebookUiState.MAX_ZOOM
                )
            )
        }
    }

    fun applyPan(
        pan: Offset,
        maxPan: Offset
    ) {
        if (
            !pan.x.isFinite() ||
            !pan.y.isFinite()
        ) {
            return
        }

        _uiState.update { state ->

            val next =
                state.offset + pan

            state.copy(
                offset = Offset(
                    x = next.x.coerceIn(
                        -maxPan.x,
                        maxPan.x
                    ),
                    y = next.y.coerceIn(
                        -maxPan.y,
                        maxPan.y
                    )
                )
            )
        }
    }

    fun setOffset(
        offset: Offset
    ) {
        if (
            !offset.x.isFinite() ||
            !offset.y.isFinite()
        ) {
            return
        }

        _uiState.update {
            it.copy(
                offset = offset
            )
        }
    }

    fun resetViewport() {
        _uiState.update {
            it.copy(
                zoom = 1f,
                offset = Offset.Zero
            )
        }
    }

    fun setExporting(
        exporting: Boolean
    ) {
        _uiState.update {
            it.copy(
                exporting = exporting
            )
        }
    }
}