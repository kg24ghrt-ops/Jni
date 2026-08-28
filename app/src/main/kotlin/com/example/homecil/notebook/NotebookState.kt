package com.example.homecil.notebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.TextFieldValue
import com.example.homecil.PaperSize
import com.example.homecil.PenType

data class NotebookViewport(
    val zoom: Float = 1f,
    val offset: Offset = Offset.Zero
) {
    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 6f
    }

    fun withZoom(value: Float): NotebookViewport =
        copy(
            zoom = value.coerceIn(
                MIN_ZOOM,
                MAX_ZOOM
            )
        )

    fun withOffset(value: Offset): NotebookViewport =
        copy(offset = value)
}

data class NotebookEditorState(
    val paperSize: PaperSize = PaperSize.A4,
    val pen: PenType = PenType.BALLPOINT,
    val text: TextFieldValue = TextFieldValue(""),
    val activeLine: Int = -1,
    val marginMode: Boolean = false,
    val panMode: Boolean = false,
    val exporting: Boolean = false
)