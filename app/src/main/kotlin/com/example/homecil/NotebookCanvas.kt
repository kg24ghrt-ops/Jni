package com.example.homecil

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.homecil.notebook.NotebookPage
import com.example.homecil.notebook.NotebookToolbar
import com.example.homecil.notebook.NotebookViewportMath
import com.example.homecil.notebook.NotebookViewModel
import kotlinx.coroutines.launch

/**
 * Main notebook canvas.
 *
 * Responsibilities:
 * - Compose the notebook UI
 * - Calculate the page's fit-to-screen scale
 * - Connect gestures to NotebookViewModel
 * - Connect the page to PaperEngine / InkEngine
 * - Connect export to ExportEngine
 *
 * Document/editor state belongs to NotebookViewModel.
 */
@Composable
fun NotebookCanvas(
    viewModel: NotebookViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    /*
     * Notebook layout constants.
     *
     * These are deliberately kept here because they describe the
     * presentation of the notebook rather than document state.
     */
    val lineSpacing = 50.dp
    val marginX = 220.dp

    val paperColor = Color(0xFFFBF9F2)
    val backgroundColor = Color(0xFFD7CCC8)

    /*
     * Generate/reuse the current paper texture.
     *
     * PaperEngine remains the owner of paper generation.
     */
    val paperTexture = remember(
        state.paperSize,
        density
    ) {
        PaperEngine.generateTexture(
            state.paperSize,
            density,
            paperColor
        )
    }

    /*
     * Generate/reuse the current ink configuration.
     *
     * InkEngine remains the owner of ink rendering.
     */
    val inkBrush = remember(
        state.pen
    ) {
        InkEngine.createInkBrush(
            state.pen.baseColor
        )
    }

    val inkShadow = remember(
        state.pen
    ) {
        InkEngine.getInkShadow(
            state.pen.baseColor
        )
    }

    /*
     * A paper-size change invalidates the previous viewport.
     *
     * The document itself is preserved.
     */
    LaunchedEffect(
        state.paperSize
    ) {
        viewModel.resetViewport()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                backgroundColor
            )
    ) {
        /*
         * Convert the physical paper dimensions to pixels.
         */
        val paperWidthPx =
            with(density) {
                state.paperSize.widthDp.toPx()
            }

        val paperHeightPx =
            with(density) {
                state.paperSize.heightDp.toPx()
            }

        /*
         * BoxWithConstraints can theoretically report zero during
         * initial measurement, so keep the values safe.
         */
        val viewportWidthPx =
            constraints.maxWidth
                .toFloat()
                .coerceAtLeast(1f)

        val viewportHeightPx =
            constraints.maxHeight
                .toFloat()
                .coerceAtLeast(1f)

        /*
         * Automatically fit the complete paper into the available
         * viewport. User zoom is applied on top of this value.
         */
        val fitScale =
            NotebookViewportMath.calculateFitScale(
                paperWidth = paperWidthPx,
                paperHeight = paperHeightPx,
                viewportWidth = viewportWidthPx,
                viewportHeight = viewportHeightPx
            )

        val totalScale =
            (
                fitScale * state.zoom
            ).coerceIn(
                0.05f,
                10f
            )

        /*
         * Calculate the largest legal translation for the current
         * scaled page.
         */
        val scaledWidth =
            paperWidthPx * totalScale

        val scaledHeight =
            paperHeightPx * totalScale

        val maxPan =
            NotebookViewportMath.calculateMaxPan(
                scaledWidth = scaledWidth,
                scaledHeight = scaledHeight,
                viewportWidth = viewportWidthPx,
                viewportHeight = viewportHeightPx
            )

        /*
         * Clamp state after a viewport resize/zoom operation.
         *
         * This prevents the page from becoming permanently lost outside
         * the visible area.
         */
        val safeOffset =
            NotebookViewportMath.clampOffset(
                offset = state.offset,
                maxPan = maxPan
            )

        /*
         * If the legal bounds changed because of rotation, resize, zoom,
         * or a different paper format, synchronize the corrected offset
         * back into the ViewModel.
         */
        LaunchedEffect(
            safeOffset,
            state.offset
        ) {
            if (safeOffset != state.offset) {
                viewModel.setOffset(
                    safeOffset
                )
            }
        }

        /*
         * Canvas interaction surface.
         *
         * Pan/zoom gestures are enabled only in Pan mode, leaving normal
         * typing completely separate from viewport manipulation.
         */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    state.panMode,
                    maxPan
                ) {
                    if (!state.panMode) {
                        return@pointerInput
                    }

                    detectTransformGestures(
                        panZoomLock = false
                    ) { _, pan, zoom, _ ->

                        if (
                            zoom.isFinite() &&
                            zoom > 0f
                        ) {
                            viewModel.applyZoom(
                                zoom
                            )
                        }

                        if (
                            pan.x.isFinite() &&
                            pan.y.isFinite()
                        ) {
                            viewModel.applyPan(
                                pan = pan,
                                maxPan = maxPan
                            )
                        }
                    }
                }
        ) {

            /*
             * The paper itself.
             *
             * The page uses the logical PaperSize dimensions while
             * graphicsLayer handles visual zoom and translation.
             */
            Box(
                modifier = Modifier
                    .align(
                        Alignment.Center
                    )
                    .graphicsLayer {
                        scaleX = totalScale
                        scaleY = totalScale

                        translationX =
                            safeOffset.x

                        translationY =
                            safeOffset.y
                    }
                    .requiredWidth(
                        state.paperSize.widthDp
                    )
                    .requiredHeight(
                        state.paperSize.heightDp
                    )
            ) {

                NotebookPage(
                    modifier =
                        Modifier.fillMaxSize(),

                    texture =
                        paperTexture,

                    text =
                        state.text,

                    onTextChange = { value ->
                        viewModel.setText(
                            value
                        )
                    },

                    brush =
                        inkBrush,

                    shadow =
                        inkShadow,

                    marginMode =
                        state.marginMode,

                    panMode =
                        state.panMode,

                    lineSpacing =
                        lineSpacing,

                    marginX =
                        marginX
                )
            }
        }

        /*
         * Floating notebook controls.
         *
         * The toolbar does not own state. Every action goes through
         * NotebookViewModel.
         */
        Box(
            modifier =
                Modifier.fillMaxSize(),

            contentAlignment =
                Alignment.BottomCenter
        ) {
            NotebookToolbar(
                paperSize =
                    state.paperSize,

                pen =
                    state.pen,

                marginMode =
                    state.marginMode,

                panMode =
                    state.panMode,

                exporting =
                    state.exporting,

                onPaperSizeSelected = { size ->
                    viewModel.setPaperSize(
                        size
                    )
                },

                onPenSelected = { pen ->
                    viewModel.setPen(
                        pen
                    )
                },

                onMarginToggle = {
                    viewModel.toggleMarginMode()
                },

                onPanToggle = {
                    viewModel.togglePanMode()
                },

                onResetViewport = {
                    viewModel.resetViewport()
                },

                onExport = {
                    /*
                     * Prevent duplicate export jobs.
                     */
                    if (state.exporting) {
                        return@NotebookToolbar
                    }

                    viewModel.setExporting(
                        true
                    )

                    /*
                     * rememberCoroutineScope is tied to this composition,
                     * unlike creating a raw CoroutineScope inside the UI.
                     */
                    coroutineScope.launch {
                        try {
                            ExportEngine.exportToPng(
                                context =
                                    context,

                                density =
                                    density,

                                paperSize =
                                    state.paperSize,

                                paperTexture =
                                    paperTexture,

                                text =
                                    state.text.text,

                                penType =
                                    state.pen,

                                lineSpacingDp =
                                    lineSpacing,

                                marginXDp =
                                    marginX
                            )
                        } finally {
                            /*
                             * Always release the export lock, including
                             * when the export engine throws.
                             */
                            viewModel.setExporting(
                                false
                            )
                        }
                    }
                }
            )
        }
    }
}