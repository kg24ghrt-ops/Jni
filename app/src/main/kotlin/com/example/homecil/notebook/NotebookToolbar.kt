package com.example.homecil.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.homecil.PaperSize
import com.example.homecil.PenType

@Composable
internal fun NotebookToolbar(
    paperSize: PaperSize,
    pen: PenType,

    marginMode: Boolean,
    panMode: Boolean,
    exporting: Boolean,

    onPaperSizeSelected:
        (PaperSize) -> Unit,

    onPenSelected:
        (PenType) -> Unit,

    onMarginToggle:
        () -> Unit,

    onPanToggle:
        () -> Unit,

    onResetViewport:
        () -> Unit,

    onExport:
        () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .padding(16.dp)
            .background(
                color =
                    Color.Black.copy(
                        alpha = 0.82f
                    ),
                shape =
                    RoundedCornerShape(
                        24.dp
                    )
            )
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        items(
            items =
                PaperSize.values(),

            key = {
                it.name
            }
        ) { size ->

            ToolbarButton(
                text =
                    size.label,

                selected =
                    paperSize == size,

                onClick = {
                    onPaperSizeSelected(
                        size
                    )
                }
            )
        }

        items(
            items =
                PenType.values(),

            key = {
                it.name
            }
        ) { selectedPen ->

            ToolbarButton(
                text =
                    selectedPen.label,

                selected =
                    pen == selectedPen,

                onClick = {
                    onPenSelected(
                        selectedPen
                    )
                }
            )
        }

        item {

            ToolbarButton(
                text =
                    if (marginMode) {
                        "Margin"
                    } else {
                        "Indent"
                    },

                selected =
                    marginMode,

                onClick =
                    onMarginToggle
            )
        }

        item {

            ToolbarButton(
                text =
                    if (panMode) {
                        "Pan ON"
                    } else {
                        "Type"
                    },

                selected =
                    panMode,

                onClick =
                    onPanToggle
            )
        }

        item {

            ToolbarButton(
                text =
                    "Reset",

                selected =
                    false,

                onClick =
                    onResetViewport
            )
        }

        item {

            Button(
                onClick =
                    onExport,

                enabled =
                    !exporting,

                modifier =
                    Modifier.padding(
                        end = 8.dp
                    ),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (exporting) {
                                Color(0xFF616161)
                            } else {
                                Color(0xFF2E7D32)
                            }
                    )
            ) {

                Text(
                    text =
                        if (exporting) {
                            "Exporting..."
                        } else {
                            "Export PNG"
                        },

                    color =
                        Color.White
                )
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,

        modifier =
            Modifier.padding(
                end = 8.dp
            ),

        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (selected) {
                        Color(0xFF1A237E)
                    } else {
                        Color(0xFF424242)
                    }
            )
    ) {
        Text(
            text =
                text,

            color =
                Color.White
        )
    }
}