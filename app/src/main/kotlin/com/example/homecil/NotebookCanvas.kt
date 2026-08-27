package com.example.homecil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Composable
fun NotebookCanvas() {
    var currentPaperSize by remember {
        mutableStateOf(PaperSize.A4)
    }

    var currentPen by remember {
        mutableStateOf(PenType.BALLPOINT)
    }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(""))
    }

    var layoutResult by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    var currentLineIndex by remember {
        mutableIntStateOf(-1)
    }

    var isMarginMode by remember {
        mutableStateOf(false)
    }

    var isPanMode by remember {
        mutableStateOf(false)
    }

    /*
     * User-controlled viewport state.
     *
     * fitScale is calculated from the available window.
     * userScale is only the user's additional zoom factor.
     */
    var userScale by remember {
        mutableFloatStateOf(1f)
    }

    var userOffset by remember {
        mutableStateOf(Offset.Zero)
    }

    var isExporting by remember {
        mutableStateOf(false)
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val scrollState = rememberScrollState()

    /*
     * Notebook visual constants.
     */
    val lineSpacing = 50.dp
    val marginX = 220.dp

    val paperColor = Color(0xFFFBF9F2)
    val lineColor = Color(0xFFA9C2D9)
    val marginColor = Color(0xFFE57373)
    val highlightColor = Color(0x40FFEB3B)

    /*
     * Texture generation is expensive enough that it should not happen
     * during ordinary recomposition.
     */
    val paperTexture = remember(
        currentPaperSize,
        density,
        paperColor
    ) {
        PaperEngine.generateTexture(
            currentPaperSize,
            density,
            paperColor
        )
    }

    /*
     * Ink rendering is also stable until the selected pen changes.
     */
    val inkBrush = remember(currentPen) {
        InkEngine.createInkBrush(
            currentPen.baseColor
        )
    }

    val inkShadow = remember(currentPen) {
        InkEngine.getInkShadow(
            currentPen.baseColor
        )
    }

    /*
     * Keep the active-line state synchronized with the cursor.
     *
     * Only the cursor position and layout result participate in this
     * effect; editing unrelated state won't restart it.
     */
    LaunchedEffect(
        textFieldValue.selection.start,
        layoutResult
    ) {
        val result = layoutResult

        if (result == null || textFieldValue.text.isEmpty()) {
            currentLineIndex = -1
            return@LaunchedEffect
        }

        val offset = textFieldValue.selection.start.coerceIn(
            0,
            textFieldValue.text.length
        )

        currentLineIndex = result.getLineForOffset(offset)
    }

    /*
     * A new paper format represents a new viewport.
     *
     * Resetting the viewport here prevents an A4 -> A5 transition from
     * leaving the new page several hundred pixels off-screen.
     */
    LaunchedEffect(currentPaperSize) {
        userScale = 1f
        userOffset = Offset.Zero
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD7CCC8))
    ) {
        /*
         * Compose constraints are pixels. Paper dimensions are Dp,
         * therefore conversion must happen exactly once here.
         */
        val paperWidthPx = with(density) {
            currentPaperSize.widthDp.toPx()
        }

        val paperHeightPx = with(density) {
            currentPaperSize.heightDp.toPx()
        }

        val availableWidthPx = constraints.maxWidth
            .toFloat()
            .coerceAtLeast(1f)

        val availableHeightPx = constraints.maxHeight
            .toFloat()
            .coerceAtLeast(1f)

        /*
         * Automatically fit the page inside the available viewport.
         *
         * The 92% factor leaves a small visual border around the paper.
         */
        val fitScale = min(
            (availableWidthPx * 0.92f) / paperWidthPx,
            (availableHeightPx * 0.92f) / paperHeightPx
        ).coerceIn(
            0.05f,
            10f
        )

        val totalScale = (
            fitScale * userScale
        ).coerceIn(
            0.05f,
            10f
        )

        val scaledWidth = paperWidthPx * totalScale
        val scaledHeight = paperHeightPx * totalScale

        /*
         * Panning is bounded.
         *
         * The page is allowed to move enough to expose its edges, but it
         * cannot disappear permanently from the viewport.
         */
        val maxHorizontalPan = max(
            availableWidthPx * 0.45f,
            scaledWidth * 0.5f
        )

        val maxVerticalPan = max(
            availableHeightPx * 0.45f,
            scaledHeight * 0.5f
        )

        val boundedOffset = Offset(
            x = userOffset.x.coerceIn(
                -maxHorizontalPan,
                maxHorizontalPan
            ),
            y = userOffset.y.coerceIn(
                -maxVerticalPan,
                maxVerticalPan
            )
        )

        /*
         * Keep the state itself synchronized with the clamped viewport.
         *
         * This prevents an enormous accumulated offset from remaining
         * stored after the user releases the gesture.
         */
        if (boundedOffset != userOffset) {
            userOffset = boundedOffset
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isPanMode) {
                    if (!isPanMode) {
                        return@pointerInput
                    }

                    detectTransformGestures(
                        panZoomLock = false
                    ) { _, pan, zoom, _ ->

                        /*
                         * Zoom around the gesture continuously, but keep
                         * the zoom range sane for a notebook page.
                         */
                        val newScale = (
                            userScale * zoom
                        ).coerceIn(
                            0.25f,
                            6f
                        )

                        userScale = newScale

                        /*
                         * Gesture pan is accumulated and bounded on every
                         * update so it can never grow without limit.
                         */
                        val proposedOffset =
                            userOffset + pan

                        userOffset = Offset(
                            x = proposedOffset.x.coerceIn(
                                -maxHorizontalPan,
                                maxHorizontalPan
                            ),
                            y = proposedOffset.y.coerceIn(
                                -maxVerticalPan,
                                maxVerticalPan
                            )
                        )
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = totalScale
                        scaleY = totalScale
                        translationX = boundedOffset.x
                        translationY = boundedOffset.y
                    }
                    .requiredWidth(
                        currentPaperSize.widthDp
                    )
                    .requiredHeight(
                        currentPaperSize.heightDp
                    )
                    .background(paperColor)
            ) {

                /*
                 * Paper texture layer.
                 */
                Image(
                    bitmap = paperTexture,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                /*
                 * Notebook ruling and active-line highlight.
                 *
                 * All coordinates are calculated in the paper's native
                 * Compose coordinate system, so the transform above scales
                 * the complete page consistently.
                 */
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val lineSpacingPx =
                        lineSpacing.toPx()

                    val marginXPx =
                        marginX.toPx()

                    /*
                     * Highlight the line containing the cursor.
                     *
                     * TextLayoutResult coordinates are relative to the
                     * BasicTextField content, so include the same top
                     * padding used by the text field.
                     */
                    val result = layoutResult

                    if (
                        currentLineIndex >= 0 &&
                        result != null
                    ) {
                        val textTopPadding =
                            12.dp.toPx()

                        val lineTop =
                            result.getLineTop(
                                currentLineIndex
                            ) + textTopPadding

                        val lineBottom =
                            result.getLineBottom(
                                currentLineIndex
                            ) + textTopPadding

                        val safeTop = lineTop.coerceIn(
                            0f,
                            size.height
                        )

                        val safeBottom = lineBottom.coerceIn(
                            safeTop,
                            size.height
                        )

                        if (safeBottom > safeTop) {
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
                     * Horizontal notebook lines.
                     */
                    if (lineSpacingPx > 0f) {
                        var y = lineSpacingPx

                        while (y < size.height) {
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

                            y += lineSpacingPx
                        }
                    }

                    /*
                     * Vertical red margin.
                     */
                    drawLine(
                        color = marginColor,
                        start = Offset(
                            marginXPx,
                            0f
                        ),
                        end = Offset(
                            marginXPx,
                            size.height
                        ),
                        strokeWidth = 3f
                    )
                }

                /*
                 * The text starts either at the physical margin or at the
                 * left edge when Margin mode is active.
                 */
                val textStartPadding =
                    if (isMarginMode) {
                        12.dp
                    } else {
                        marginX + 12.dp
                    }

                BasicTextField(
                    value = textFieldValue,

                    onValueChange = { newValue ->
                        /*
                         * Typing is completely disabled while panning.
                         * This prevents accidental text modification during
                         * a two-finger/page navigation gesture.
                         */
                        if (isPanMode) {
                            return@BasicTextField
                        }

                        textFieldValue = newValue

                        /*
                         * When a newline is inserted in margin mode, return
                         * to the normal indentation mode. This preserves the
                         * original notebook behavior.
                         */
                        if (
                            isMarginMode &&
                            newValue.text.contains('\n')
                        ) {
                            isMarginMode = false
                        }
                    },

                    onTextLayout = { result ->
                        layoutResult = result
                    },

                    enabled = !isPanMode,

                    textStyle = TextStyle(
                        fontFamily = FontFamily.Default,
                        brush = inkBrush,
                        shadow = inkShadow,
                        fontSize = 36.sp,
                        lineHeight = 50.sp
                    ),

                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = textStartPadding,
                            top = 12.dp,
                            end = 24.dp,
                            bottom = 24.dp
                        )
                        .then(
                            if (!isPanMode) {
                                Modifier.verticalScroll(
                                    scrollState
                                )
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }

        /*
         * Bottom toolbar.
         *
         * LazyRow prevents the controls from overflowing horizontally on
         * smaller devices.
         */
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(
                        alpha = 0.80f
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            items(
                items = PaperSize.values(),
                key = { it.name }
            ) { size ->

                Button(
                    onClick = {
                        if (
                            currentPaperSize != size
                        ) {
                            currentPaperSize = size
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (
                                currentPaperSize == size
                            ) {
                                Color(0xFF1A237E)
                            } else {
                                Color(0xFF424242)
                            }
                    ),
                    modifier = Modifier.padding(
                        end = 8.dp
                    )
                ) {
                    Text(
                        text = size.label,
                        color = Color.White
                    )
                }
            }

            items(
                items = PenType.values(),
                key = { it.name }
            ) { pen ->

                Button(
                    onClick = {
                        currentPen = pen
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (
                                currentPen == pen
                            ) {
                                Color(0xFF00695C)
                            } else {
                                Color(0xFF424242)
                            }
                    ),
                    modifier = Modifier.padding(
                        end = 8.dp
                    )
                ) {
                    Text(
                        text = pen.label,
                        color = Color.White
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        isMarginMode = !isMarginMode
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (isMarginMode) {
                                Color(0xFF6A1B9A)
                            } else {
                                Color(0xFF424242)
                            }
                    ),
                    modifier = Modifier.padding(
                        end = 8.dp
                    )
                ) {
                    Text(
                        text =
                            if (isMarginMode) {
                                "Margin"
                            } else {
                                "Indent"
                            },
                        color = Color.White
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        isPanMode = !isPanMode
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (isPanMode) {
                                Color(0xFFD84315)
                            } else {
                                Color(0xFF1565C0)
                            }
                    ),
                    modifier = Modifier.padding(
                        end = 8.dp
                    )
                ) {
                    Text(
                        text =
                            if (isPanMode) {
                                "Pan ON"
                            } else {
                                "Type"
                            },
                        color = Color.White
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        /*
                         * Avoid launching multiple exports at once.
                         *
                         * This is particularly important because PNG
                         * generation can allocate a large bitmap.
                         */
                        if (isExporting) {
                            return@Button
                        }

                        isExporting = true

                        coroutineScope.launch {
                            try {
                                ExportEngine.exportToPng(
                                    context = context,
                                    density = density,
                                    paperSize = currentPaperSize,
                                    paperTexture = paperTexture,
                                    text = textFieldValue.text,
                                    penType = currentPen,
                                    lineSpacingDp = lineSpacing,
                                    marginXDp = marginX
                                )
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (isExporting) {
                                Color(0xFF616161)
                            } else {
                                Color(0xFF2E7D32)
                            },
                        disabledContainerColor =
                            Color(0xFF616161)
                    )
                ) {
                    Text(
                        text =
                            if (isExporting) {
                                "Exporting..."
                            } else {
                                "Export PNG"
                            },
                        color = Color.White
                    )
                }
            }
        }
    }
}