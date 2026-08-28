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
 * Immutable state consumed by the notebook UI.
 *
 * The ViewModel is the single owner of document/editor state.
 * Compose-specific layout objects are deliberately kept outside this state.
 *
 * Design goals:
 * - predictable state transitions
 * - defensive numeric handling
 * - no invalid zoom/pan values
 * - cheap StateFlow updates
 * - API compatibility with NotebookCanvas
 */
data class NotebookUiState(
    val paperSize: PaperSize = PaperSize.A4,
    val pen: PenType = PenType.BALLPOINT,
    val text: TextFieldValue = TextFieldValue(""),

    val marginMode: Boolean = false,
    val panMode: Boolean = false,

    val zoom: Float = 1f,
    val offset: Offset = Offset.Zero,

    /**
     * True while an export operation is running.
     *
     * This is UI state only. The actual export operation remains owned
     * by ExportEngine.
     */
    val exporting: Boolean = false
) {

    companion object {

        /**
         * Minimum user-visible zoom.
         *
         * A lower value makes the page effectively disappear and provides
         * little practical value on a notebook canvas.
         */
        const val MIN_ZOOM = 0.25f

        /**
         * Maximum zoom allowed through gesture input.
         */
        const val MAX_ZOOM = 6f

        /**
         * Default zoom used whenever the viewport is reset.
         */
        const val DEFAULT_ZOOM = 1f
    }
}

/**
 * ViewModel for the notebook editor.
 *
 * This class intentionally contains no Android View references and no
 * Compose UI operations beyond immutable value types used by the state.
 *
 * That makes the state machine:
 * - lifecycle-safe
 * - straightforward to test
 * - independent of the rendering implementation
 * - resistant to malformed gesture values
 */
class NotebookViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        NotebookUiState()
    )

    /**
     * Read-only state exposed to the UI.
     */
    val uiState: StateFlow<NotebookUiState> =
        _uiState.asStateFlow()

    /**
     * Changes the current paper format.
     *
     * Changing paper dimensions invalidates the previous viewport because
     * the old pan/zoom values may no longer be appropriate.
     *
     * The document itself is intentionally preserved.
     */
    fun setPaperSize(
        value: PaperSize
    ) {
        _uiState.update { state ->
            if (state.paperSize == value) {
                state
            } else {
                state.copy(
                    paperSize = value,
                    zoom = NotebookUiState.DEFAULT_ZOOM,
                    offset = Offset.Zero
                )
            }
        }
    }

    /**
     * Changes the active pen.
     */
    fun setPen(
        value: PenType
    ) {
        _uiState.update { state ->
            if (state.pen == value) {
                state
            } else {
                state.copy(
                    pen = value
                )
            }
        }
    }

    /**
     * Replaces the current editor value.
     *
     * TextFieldValue is used instead of a raw String so selection and
     * composition information remain intact.
     */
    fun setText(
        value: TextFieldValue
    ) {
        _uiState.update { state ->
            if (state.text == value) {
                state
            } else {
                state.copy(
                    text = value
                )
            }
        }
    }

    /**
     * Clears the current document text.
     *
     * Selection is reset together with the document.
     */
    fun clearText() {
        _uiState.update { state ->
            if (state.text.text.isEmpty()) {
                state
            } else {
                state.copy(
                    text = TextFieldValue("")
                )
            }
        }
    }

    /**
     * Enables or disables the margin visualization.
     */
    fun toggleMarginMode() {
        _uiState.update { state ->
            state.copy(
                marginMode = !state.marginMode
            )
        }
    }

    /**
     * Explicitly sets margin visualization.
     *
     * Useful for restoring UI state without relying on the previous value.
     */
    fun setMarginMode(
        enabled: Boolean
    ) {
        _uiState.update { state ->
            if (state.marginMode == enabled) {
                state
            } else {
                state.copy(
                    marginMode = enabled
                )
            }
        }
    }

    /**
     * Toggles viewport/pan interaction mode.
     *
     * Turning pan mode off does not destroy the current viewport. This is
     * intentional: returning to pan mode should restore the user's previous
     * position rather than unexpectedly resetting the page.
     */
    fun togglePanMode() {
        _uiState.update { state ->
            state.copy(
                panMode = !state.panMode
            )
        }
    }

    /**
     * Explicitly enables or disables pan mode.
     */
    fun setPanMode(
        enabled: Boolean
    ) {
        _uiState.update { state ->
            if (state.panMode == enabled) {
                state
            } else {
                state.copy(
                    panMode = enabled
                )
            }
        }
    }

    /**
     * Applies a pinch-zoom multiplier.
     *
     * Invalid, zero, negative, NaN, and infinite gesture values are ignored.
     *
     * The result is always clamped to the documented zoom range.
     */
    fun applyZoom(
        gestureZoom: Float
    ) {
        if (
            !gestureZoom.isFinite() ||
            gestureZoom <= 0f
        ) {
            return
        }

        _uiState.update { state ->

            val currentZoom =
                sanitizeZoom(
                    state.zoom
                )

            val nextZoom =
                currentZoom * gestureZoom

            state.copy(
                zoom = sanitizeZoom(
                    nextZoom
                )
            )
        }
    }

    /**
     * Sets zoom directly.
     *
     * This is useful for future toolbar controls such as:
     * - 100%
     * - 200%
     * - fit to page
     */
    fun setZoom(
        value: Float
    ) {
        if (!value.isFinite()) {
            return
        }

        val safeZoom =
            sanitizeZoom(
                value
            )

        _uiState.update { state ->
            if (state.zoom == safeZoom) {
                state
            } else {
                state.copy(
                    zoom = safeZoom
                )
            }
        }
    }

    /**
     * Applies a pan gesture while respecting the current legal viewport
     * bounds.
     *
     * maxPan represents the maximum positive displacement on each axis.
     * The same absolute limit is applied in the negative direction.
     */
    fun applyPan(
        pan: Offset,
        maxPan: Offset
    ) {
        if (
            !pan.x.isFinite() ||
            !pan.y.isFinite() ||
            !maxPan.x.isFinite() ||
            !maxPan.y.isFinite()
        ) {
            return
        }

        val safeMaxPan = Offset(
            x = maxPan.x
                .coerceAtLeast(0f),
            y = maxPan.y
                .coerceAtLeast(0f)
        )

        _uiState.update { state ->

            val currentOffset =
                sanitizeOffset(
                    state.offset
                )

            val next =
                currentOffset + pan

            val safeOffset =
                Offset(
                    x = next.x.coerceIn(
                        -safeMaxPan.x,
                        safeMaxPan.x
                    ),
                    y = next.y.coerceIn(
                        -safeMaxPan.y,
                        safeMaxPan.y
                    )
                )

            if (currentOffset == safeOffset) {
                state
            } else {
                state.copy(
                    offset = safeOffset
                )
            }
        }
    }

    /**
     * Replaces the viewport offset.
     *
     * This method does not know the current page bounds, so it only
     * sanitizes finite numeric values. NotebookCanvas remains responsible
     * for calculating the legal bounds.
     */
    fun setOffset(
        offset: Offset
    ) {
        if (
            !offset.x.isFinite() ||
            !offset.y.isFinite()
        ) {
            return
        }

        val safeOffset =
            sanitizeOffset(
                offset
            )

        _uiState.update { state ->
            if (state.offset == safeOffset) {
                state
            } else {
                state.copy(
                    offset = safeOffset
                )
            }
        }
    }

    /**
     * Moves the viewport by an explicit amount.
     *
     * Unlike applyPan(), this operation does not require bounds. It is
     * intended for programmatic movement before the UI calculates bounds.
     */
    fun moveViewport(
        delta: Offset
    ) {
        if (
            !delta.x.isFinite() ||
            !delta.y.isFinite()
        ) {
            return
        }

        _uiState.update { state ->

            val current =
                sanitizeOffset(
                    state.offset
                )

            val next =
                Offset(
                    x = current.x + delta.x,
                    y = current.y + delta.y
                )

            state.copy(
                offset = sanitizeOffset(
                    next
                )
            )
        }
    }

    /**
     * Resets zoom and pan while preserving:
     * - document text
     * - paper format
     * - pen
     * - margin setting
     * - pan mode
     */
    fun resetViewport() {
        _uiState.update { state ->
            if (
                state.zoom ==
                    NotebookUiState.DEFAULT_ZOOM &&
                state.offset ==
                    Offset.Zero
            ) {
                state
            } else {
                state.copy(
                    zoom =
                        NotebookUiState.DEFAULT_ZOOM,
                    offset =
                        Offset.Zero
                )
            }
        }
    }

    /**
     * Resets the editor document while keeping notebook presentation
     * settings intact.
     */
    fun resetDocument() {
        _uiState.update { state ->
            if (state.text.text.isEmpty()) {
                state
            } else {
                state.copy(
                    text = TextFieldValue("")
                )
            }
        }
    }

    /**
     * Restores the entire notebook state to its defaults.
     *
     * This is deliberately separate from resetViewport() and
     * resetDocument() so destructive actions can be explicit.
     */
    fun resetAll() {
        _uiState.value =
            NotebookUiState()
    }

    /**
     * Updates the export lock.
     *
     * Duplicate export requests are prevented by NotebookCanvas.
     */
    fun setExporting(
        exporting: Boolean
    ) {
        _uiState.update { state ->
            if (state.exporting == exporting) {
                state
            } else {
                state.copy(
                    exporting = exporting
                )
            }
        }
    }

    /**
     * Attempts to begin an export operation atomically from the ViewModel's
     * state perspective.
     *
     * Returns true only when an export was not already active.
     *
     * This method is useful for future callers that want to avoid the
     * check-then-set pattern:
     *
     *     if (viewModel.tryBeginExport()) {
     *         ...
     *     }
     */
    fun tryBeginExport(): Boolean {
        var started = false

        _uiState.update { state ->
            if (state.exporting) {
                state
            } else {
                started = true

                state.copy(
                    exporting = true
                )
            }
        }

        return started
    }

    /**
     * Finishes an export operation.
     */
    fun finishExport() {
        setExporting(
            false
        )
    }

    /**
     * Returns a sanitized zoom value.
     *
     * This boundary prevents malformed floating-point values from entering
     * the rendering pipeline.
     */
    private fun sanitizeZoom(
        value: Float
    ): Float {
        if (!value.isFinite()) {
            return NotebookUiState.DEFAULT_ZOOM
        }

        return value.coerceIn(
            NotebookUiState.MIN_ZOOM,
            NotebookUiState.MAX_ZOOM
        )
    }

    /**
     * Returns a finite viewport offset.
     *
     * Infinite/NaN offsets are converted to zero rather than being allowed
     * to propagate into graphicsLayer transformations.
     */
    private fun sanitizeOffset(
        offset: Offset
    ): Offset {
        return Offset(
            x = if (offset.x.isFinite()) {
                offset.x
            } else {
                0f
            },
            y = if (offset.y.isFinite()) {
                offset.y
            } else {
                0f
            }
        )
    }
}