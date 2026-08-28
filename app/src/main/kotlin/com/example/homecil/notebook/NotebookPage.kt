package com.example.homecil.notebook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
 * This separation keeps the page composable lightweight and prevents
 * editor state from becoming duplicated between the UI and ViewModel.
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
    /*
     * The scroll state belongs to this page instance rather than the
     * document. Changing the paper format creates a new visual page
     * through the parent composition and the state remains predictable.
     */
    val scrollState =
        rememberScrollState()

    /*
     * Used for cursor-following behavior.
     *
     * When the user types beyond the visible portion of the page,
     * NotebookPage automatically moves the viewport enough to keep
     * the active line visible.
     */
    val coroutineScope =
        rememberCoroutineScope()

    var layoutResult by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    /*
     * Cache the active line instead of recalculating it during every
     * draw pass.
     *
     * -1 means there is currently no valid active line.
     */
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
                val safeOffset =
                    text.selection.start.coerceIn(
                        0,
                        text.text.length
                    )

                layout.getLineForOffset(
                    safeOffset
                )
            }
        }

    /*
     * Keep the cursor location available separately so we can perform
     * scroll-to-cursor without asking the layout engine repeatedly.
     */
    val cursorRect =
        remember(
            layoutResult,
            text.selection.start
        ) {
            val layout =
                layoutResult

            if (layout == null) {
                null
            } else {
                val safeOffset =
                    text.selection.start.coerceIn(
                        0,
                        text.text.length
                    )

                layout.getCursorRect(
                    safeOffset
                )
            }
        }

    /*
     * Keep the paper colors centralized.
     *
     * The texture remains the primary visual surface; this color is the
     * fallback/background underneath it.
     */
    val paperColor =
        Color(0xFFFBF9F2)

    /*
     * Notebook text begins immediately after the margin when margins
     * are enabled.
     *
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
     * Follow the active cursor.
     *
     * TextLayoutResult coordinates are in pixels, which matches the
     * ScrollState coordinate system.
     *
     * A conservative viewport estimate is used because this composable
     * intentionally does not own a separate measurement/layout system.
     */
    LaunchedEffect(
        cursorRect,
        text.selection,
        panMode
    ) {
        /*
         * Never force-scroll while the user is manipulating the page.
         * Pan mode belongs to NotebookCanvas/NotebookViewModel.
         */
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

        /*
         * Approximate the visible editor region using the current
         * ScrollState maximum.
         *
         * The actual scroll container remains authoritative; this only
         * ensures that the active line isn't left far outside the view.
         */
        val currentScroll =
            scrollState.value

        val targetPadding =
            80

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
                        (
                            cursorBottom -
                                targetPadding
                        ).coerceAtLeast(0)
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
         * ----------------------------------------------------------------
         * PAPER TEXTURE
         * ----------------------------------------------------------------
         *
         * The generated bitmap is rendered beneath every other layer.
         *
         * ContentScale.FillBounds is intentional: PaperEngine already
         * generates the texture for the selected paper dimensions.
         */
        Image(
            bitmap = texture,

            contentDescription = null,

            modifier =
                Modifier.matchParentSize(),

            contentScale =
                ContentScale.FillBounds
        )

        /*
         * ----------------------------------------------------------------
         * NOTEBOOK GRID
         * ----------------------------------------------------------------
         *
         * The grid does not own text state. It receives the calculated
         * layout result so it can synchronize horizontal ruling with
         * actual text lines.
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
         * ----------------------------------------------------------------
         * TEXT EDITOR
         * ----------------------------------------------------------------
         *
         * BasicTextField is deliberately kept as the actual editor.
         * This avoids introducing a heavyweight text component into the
         * notebook rendering pipeline.
         */
        BasicTextField(
            value = text,

            onValueChange = { newValue ->

                /*
                 * Pan mode and text editing are mutually exclusive.
                 *
                 * NotebookCanvas handles gestures while this component
                 * simply refuses to mutate document state.
                 */
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

            /*
             * Disabling the editor while panning prevents accidental
             * keyboard/text interaction during viewport manipulation.
             */
            enabled = !panMode,

            /*
             * Keep the keyboard/editor semantics available when enabled.
             */
            readOnly = false,

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

            /*
             * A restrained cursor keeps the writing experience visually
             * consistent with the selected ink.
             */
            cursorBrush =
                brush,

            modifier = Modifier
                .matchParentSize()

                /*
                 * Vertical scrolling belongs to the text editor only.
                 * NotebookCanvas continues to own page pan/zoom.
                 */
                .verticalScroll(
                    scrollState,
                    enabled = !panMode
                )

                /*
                 * The text must remain inside the physical notebook page.
                 */
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