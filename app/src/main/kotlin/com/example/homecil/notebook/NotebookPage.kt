package com.example.homecil.notebook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding

@Composable
internal fun NotebookPage(
    modifier: Modifier,

    texture: ImageBitmap,

    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,

    brush: Brush,
    shadow: Shadow,

    marginMode: Boolean,
    panMode: Boolean,

    lineSpacing: Dp,
    marginX: Dp
) {
    val scrollState =
        rememberScrollState()

    var layoutResult by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val activeLine =
        remember(
            layoutResult,
            text.selection.start,
            text.text.length
        ) {
            val layout =
                layoutResult

            if (
                layout == null ||
                text.text.isEmpty()
            ) {
                -1
            } else {
                val offset =
                    text.selection.start.coerceIn(
                        0,
                        text.text.length
                    )

                layout.getLineForOffset(
                    offset
                )
            }
        }

    val paperColor =
        Color(0xFFFBF9F2)

    val textStartPadding =
        if (marginMode) {
            12.dp
        } else {
            marginX + 12.dp
        }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(
                paperColor
            )
    ) {

        /*
         * Generated paper texture.
         */
        Image(
            bitmap = texture,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds
        )

        /*
         * Notebook ruling.
         */
        NotebookGrid(
            modifier =
                Modifier.matchParentSize(),

            lineSpacing =
                lineSpacing,

            marginX =
                marginX,

            layoutResult =
                layoutResult,

            activeLine =
                activeLine,

            showMargin =
                !marginMode
        )

        /*
         * Actual text editor.
         */
        BasicTextField(
            value = text,

            onValueChange = { newValue ->

                if (panMode) {
                    return@BasicTextField
                }

                onTextChange(
                    newValue
                )
            },

            onTextLayout = { result ->
                layoutResult =
                    result
            },

            enabled = !panMode,

            textStyle = TextStyle(
                fontFamily =
                    FontFamily.Default,

                brush =
                    brush,

                shadow =
                    shadow,

                fontSize =
                    36.sp,

                lineHeight =
                    50.sp
            ),

            modifier = Modifier
                .matchParentSize()
                .verticalScroll(
                    scrollState
                )
                .then(
                    Modifier
                )
                .padding(
                    start =
                        textStartPadding,

                    top =
                        12.dp,

                    end =
                        24.dp,

                    bottom =
                        24.dp
                )
        )
    }
}