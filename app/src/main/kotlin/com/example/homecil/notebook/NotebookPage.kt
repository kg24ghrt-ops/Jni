package com.example.homecil.notebook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The actual editable notebook page.
 *
 * NotebookPage deliberately owns presentation concerns only:
 *
 *  - paper background/texture
 *  - notebook ruling
 *  - text editing
 *  - cursor visibility
 *  - editor scrolling
 *
 * Document state is owned by NotebookViewModel.
 * Rendering configuration is supplied by InkEngine/PaperEngine.
 *
 * This implementation is defensive around TextLayoutResult because
 * Compose can temporarily expose a layout belonging to the previous
 * text value while TextFieldValue already contains the new selection.
 */
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
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var layoutResult by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    /*
     * Capture these independently so the remembered calculations are
     * invalidated whenever the actual text or selection changes.
     */
    val currentText = text.text
    val selectionStart = text.selection.start

    /*
     * IMPORTANT:
     *
     * TextLayoutResult may temporarily represent the previous text.
     * Therefore we must validate the selection against the text length
     * belonging to THAT layout, not only against text.text.length.
     */
    val activeLine = remember(
        layoutResult,
        currentText,
        selectionStart
    ) {
        val layout = layoutResult
            ?: return@remember -1

        val layoutLength =
            layout.layoutInput.text.length

        /*
         * An empty MultiParagraph has no useful line information.
         */
        if (layoutLength <= 0) {
            return@remember -1
        }

        val safeOffset =
            selectionStart.coerceIn(
                0,
                layoutLength
            )

        runCatching {
            layout.getLineForOffset(
                safeOffset
            )
        }.getOrDefault(-1)
    }

    /*
     * Cursor rectangle used for automatic scrolling.
     *
     * This is intentionally calculated defensively. A layout can be
     * momentarily out of sync with TextFieldValue during recomposition.
     */
    val cursorRect = remember(
        layoutResult,
        currentText,
        selectionStart
    ) {
        val layout = layoutResult
            ?: return@remember null

        val layoutLength =
            layout.layoutInput.text.length

        /*
         * There is no valid non-zero cursor offset when the paragraph
         * itself is empty.
         */
        if (layoutLength <= 0) {
            return@remember null
        }

        val safeOffset =
            selectionStart.coerceIn(
                0,
                layoutLength
            )

        /*
         * Compose's text layout can still theoretically change between
         * validation and getCursorRect(). Treat that as a transient
         * layout condition instead of allowing it to crash the app.
         */
        runCatching {
            layout.getCursorRect(
                safeOffset
            )
        }.getOrNull()
    }

    val paperColor =
        Color(0xFFFBF9F2)

    /*
     * In margin mode the margin is hidden, so text moves toward the
     * normal page edge.
     */
    val textStartPadding =
        if (marginMode) {
            12.dp
        } else {
            marginX + 12.dp
        }

    /*
     * Keep the active cursor visible.
     *
     * Cursor-following is disabled while the user is panning the page.
     */
    LaunchedEffect(
        cursorRect,
        text.selection,
        panMode
    ) {
        if (panMode) {
            return@LaunchedEffect
        }

        val cursor =
            cursorRect
                ?: return@LaunchedEffect

        val cursorTop =
            cursor.top.roundToInt()

        val cursorBottom =
            cursor.bottom.roundToInt()

        val currentScroll =
            scrollState.value

        val targetPadding =
            80

        /*
         * This remains intentionally conservative because the page
         * itself owns the scroll container.
         */
        val visibleTop =
            currentScroll + targetPadding

        val visibleBottom =
            currentScroll +
                targetPadding +
                900

        when {
            cursorTop < visibleTop -> {
                coroutineScope.launch {
                    scrollState.animateScrollTo(
                        (cursorTop - targetPadding)
                            .coerceAtLeast(0)
                    )
                }
            }

            cursorBottom > visibleBottom -> {
                coroutineScope.launch {
                    scrollState.animateScrollTo(
                        (cursorBottom - targetPadding)
                            .coerceAtLeast(0)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(
                paperColor
            )
    ) {

        /*
         * ---------------------------------------------------------------
         * PAPER TEXTURE
         * ---------------------------------------------------------------
         */
        Image(
            bitmap = texture,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds
        )

        /*
         * ---------------------------------------------------------------
         * NOTEBOOK GRID
         * ---------------------------------------------------------------
         */
        NotebookGrid(
            modifier = Modifier.matchParentSize(),
            lineSpacing = lineSpacing,
            marginX = marginX,
            layoutResult = layoutResult,
            activeLine = activeLine,
            showMargin = !marginMode
        )

        /*
         * ---------------------------------------------------------------
         * TEXT EDITOR
         * ---------------------------------------------------------------
         */
        BasicTextField(
            value = text,

            onValueChange = { newValue ->
                /*
                 * Pan mode and text editing are mutually exclusive.
                 */
                if (panMode) {
                    return@BasicTextField
                }

                onTextChange(
                    newValue
                )
            },

            onTextLayout = { result ->
                /*
                 * Always replace the layout with the newest result.
                 * Cursor calculations above independently validate it
                 * against result.layoutInput.text.
                 */
                layoutResult = result
            },

            enabled = !panMode,

            readOnly = false,

            textStyle = TextStyle(
                fontFamily = FontFamily.Default,
                brush = brush,
                shadow = shadow,
                fontSize = 36.sp,
                lineHeight = 50.sp
            ),

            cursorBrush = brush,

            modifier = Modifier
                .matchParentSize()
                .verticalScroll(
                    scrollState,
                    enabled = !panMode
                )
                .padding(
                    start = textStartPadding,
                    top = 12.dp,
                    end = 24.dp,
                    bottom = 24.dp
                )
        )
    }
}