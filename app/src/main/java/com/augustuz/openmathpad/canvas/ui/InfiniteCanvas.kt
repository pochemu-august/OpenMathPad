package com.augustuz.openmathpad.canvas.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.augustuz.openmathpad.canvas.model.CanvasImage
import com.augustuz.openmathpad.canvas.model.CanvasTextAnnotation
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.state.CanvasViewModel
import com.augustuz.openmathpad.canvas.state.InputDiagnostics
import com.augustuz.openmathpad.ui.theme.OpenMathPadTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Infinite Canvas Math & Physics Scratchpad supporting:
 * - Infinite pan & zoom coordinate transformations (0.2x to 5.0x).
 * - Touch vs Stylus separation with 100% palm rejection:
 *     Stylus writes strokes mapped to scene coordinates `(screen - pan) / zoom`.
 *     Two-finger gestures pan and pinch-to-zoom.
 *     Single finger ignored by default for zero-palm-interference writing.
 * - Draggable and resizable STEM problem screenshot cards inserted from device storage.
 * - Hardware-accelerated stroke rendering and infinite coordinate dot grid.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InfiniteCanvas(
    modifier: Modifier = Modifier,
    viewModel: CanvasViewModel = viewModel(),
    showToolbar: Boolean = true,
    showDiagnostics: Boolean = true,
    backgroundColor: Color = Color(0xFFFAFAFA)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Media & Problem Screenshot Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.addImage(uri = it.toString())
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLargeTabletWidth = maxWidth >= 840.dp
        val panX = uiState.panOffsetX
        val panY = uiState.panOffsetY
        val zoom = uiState.zoomScale

        // 1. Hardware-Accelerated Drawing Surface (Infinite Dot Grid + Vector Strokes)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .pointerInteropFilter { motionEvent ->
                    viewModel.processMotionEvent(motionEvent)
                }
        ) {
            // Infinite Grid rendered efficiently for current viewport bounds
            drawInfiniteDotGrid(
                panOffsetX = panX,
                panOffsetY = panY,
                zoomScale = zoom,
                baseGridSizePx = with(density) { 36.dp.toPx() }
            )

            // Render vector strokes and annotations in transformed scene space
            withTransform({
                translate(left = panX, top = panY)
                scale(scaleX = zoom, scaleY = zoom, pivot = Offset.Zero)
            }) {
                // Render completed strokes
                val strokes = uiState.strokes
                for (i in strokes.indices) {
                    drawStroke(strokes[i])
                }

                // Render current in-progress active stroke
                uiState.currentActiveStroke?.let { activeStroke ->
                    drawStroke(activeStroke)
                }

                // Render text annotations
                val annotations = uiState.canvasTextAnnotations
                for (i in annotations.indices) {
                    drawTextAnnotation(textMeasurer, annotations[i])
                }
            }
        }

        // 2. Interactive Media / Problem Screenshot Cards (Draggable & Resizable in Scene Space)
        uiState.images.forEach { canvasImage ->
            CanvasImageCard(
                image = canvasImage,
                panOffsetX = panX,
                panOffsetY = panY,
                zoomScale = zoom,
                onPositionChange = { newX, newY ->
                    viewModel.updateImagePosition(canvasImage.id, newX, newY)
                },
                onResize = { newW, newH ->
                    viewModel.resizeImage(canvasImage.id, newW, newH)
                },
                onDelete = {
                    viewModel.removeImage(canvasImage.id)
                },
                onSelect = {
                    viewModel.selectImage(canvasImage.id)
                }
            )
        }

        // 3. Floating Minimalist Toolbar
        if (showToolbar) {
            CanvasToolbar(
                uiState = uiState,
                onToolSelected = viewModel::setTool,
                onColorSelected = viewModel::setPenColor,
                onStrokeWidthChanged = viewModel::setPenWidth,
                onTouchModeToggled = viewModel::toggleTouchMode,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onClear = viewModel::clear,
                onAddImageClicked = { imagePickerLauncher.launch("image/*") },
                onResetZoom = viewModel::resetZoom,
                onCalculateClicked = {
                    viewModel.setOverlayVisible(!uiState.isOverlayVisible)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // 4. STEM Math & Physics Calculator Overlay Card
        AnimatedVisibility(
            visible = uiState.isOverlayVisible,
            enter = if (isLargeTabletWidth) {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            } else {
                slideInVertically(initialOffsetY = { it }) + fadeIn()
            },
            exit = if (isLargeTabletWidth) {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            } else {
                slideOutVertically(targetOffsetY = { it }) + fadeOut()
            },
            modifier = if (isLargeTabletWidth) {
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 16.dp, bottom = 16.dp)
                    .width(420.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            }
        ) {
            FormulaOverlayCard(
                uiState = uiState,
                onFormulaChange = viewModel::setFormulaText,
                onEvaluateFormula = { viewModel.evaluateFormula(it) },
                onToggleExpanded = viewModel::toggleOverlayExpanded,
                onCloseOverlay = { viewModel.setOverlayVisible(false) },
                onSetAngleMode = viewModel::setAngleMode,
                onSetVariable = viewModel::setVariable,
                onRemoveVariable = viewModel::removeVariable,
                onInsertResultToCanvas = { text -> viewModel.insertResultToCanvas(text) },
                onClearHistory = viewModel::clearCalculationHistory
            )
        }

        // 5. Input Telemetry & Palm Rejection Diagnostics
        if (showDiagnostics) {
            InfiniteCanvasDiagnosticsBadge(
                diagnostics = uiState.diagnostics,
                touchModeEnabled = uiState.config.touchModeEnabled,
                zoomScale = uiState.zoomScale,
                panOffsetX = uiState.panOffsetX,
                panOffsetY = uiState.panOffsetY,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Interactive Media Card representing an inserted problem screenshot or task.
 * Positioned and scaled directly in infinite canvas coordinate space.
 */
@Composable
private fun CanvasImageCard(
    image: CanvasImage,
    panOffsetX: Float,
    panOffsetY: Float,
    zoomScale: Float,
    onPositionChange: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    val density = LocalDensity.current

    // Convert scene coordinates to current screen pixels
    val screenX = image.x * zoomScale + panOffsetX
    val screenY = image.y * zoomScale + panOffsetY
    val screenWidthPx = image.width * zoomScale
    val screenHeightPx = image.height * zoomScale

    val widthDp = with(density) { screenWidthPx.toDp() }
    val heightDp = with(density) { screenHeightPx.toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
            .size(width = widthDp, height = heightDp)
            .padding(4.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (image.isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    }
                )
                .clickable { onSelect() }
                .pointerInput(image.id, zoomScale) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaSceneX = dragAmount.x / zoomScale
                        val deltaSceneY = dragAmount.y / zoomScale
                        onPositionChange(image.x + deltaSceneX, image.y + deltaSceneY)
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = image.uri,
                    contentDescription = "Inserted Problem Screenshot",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )

                // Top-Right Delete Action Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Problem Image",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Bottom-Right Corner Resize Handle
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .shadow(2.dp, CircleShape)
                        .pointerInput(image.id, zoomScale) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaW = dragAmount.x / zoomScale
                                val deltaH = dragAmount.y / zoomScale
                                onResize(image.width + deltaW, image.height + deltaH)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.OpenWith,
                            contentDescription = "Resize Image Card",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws an infinite dot grid aligned with coordinate scene transformations.
 * Only iterates over visible viewport bounding box for $O(1)$ constant-time 144Hz performance.
 */
private fun DrawScope.drawInfiniteDotGrid(
    panOffsetX: Float,
    panOffsetY: Float,
    zoomScale: Float,
    baseGridSizePx: Float
) {
    val scale = if (zoomScale <= 0f) 1.0f else zoomScale
    val effectiveGridSize = baseGridSizePx * scale

    // Adjust grid step when deeply zoomed out to avoid dot saturation
    val stepMultiplier = when {
        effectiveGridSize < 16f -> 4
        effectiveGridSize < 24f -> 2
        else -> 1
    }
    val gridStep = baseGridSizePx * stepMultiplier
    val dotRadius = (1.5f * scale.coerceIn(0.6f, 1.4f)).coerceIn(1.0f, 3.0f)
    val dotColor = Color.LightGray.copy(alpha = 0.45f)

    // Viewport bounds in scene coordinates
    val sceneLeft = -panOffsetX / scale
    val sceneTop = -panOffsetY / scale
    val sceneRight = (size.width - panOffsetX) / scale
    val sceneBottom = (size.height - panOffsetY) / scale

    val startX = (floor(sceneLeft / gridStep) * gridStep)
    val endX = (ceil(sceneRight / gridStep) * gridStep)
    val startY = (floor(sceneTop / gridStep) * gridStep)
    val endY = (ceil(sceneBottom / gridStep) * gridStep)

    var x = startX
    while (x <= endX) {
        val screenX = x * scale + panOffsetX
        var y = startY
        while (y <= endY) {
            val screenY = y * scale + panOffsetY
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(screenX, screenY)
            )
            y += gridStep
        }
        x += gridStep
    }
}

/**
 * Renders a single vector stroke on DrawScope.
 */
private fun DrawScope.drawStroke(stroke: DrawingStroke) {
    if (stroke.points.isEmpty()) return

    val color = stroke.color
    val alpha = stroke.alpha

    if (stroke.points.size == 1) {
        val p = stroke.points[0]
        drawCircle(
            color = color,
            radius = p.width / 2f,
            center = Offset(p.x, p.y),
            alpha = alpha
        )
        return
    }

    if (stroke.isUniformWidth) {
        drawPath(
            path = stroke.path,
            color = color,
            alpha = alpha,
            style = stroke.strokeStyle
        )
    } else {
        // Variable-width ink segment rendering
        val points = stroke.points
        var prevX = points[0].x
        var prevY = points[0].y
        var prevW = points[0].width

        for (i in 1 until points.size) {
            val curr = points[i]
            val avgWidth = (prevW + curr.width) / 2f

            drawLine(
                color = color,
                start = Offset(prevX, prevY),
                end = Offset(curr.x, curr.y),
                strokeWidth = avgWidth,
                cap = StrokeCap.Round,
                alpha = alpha
            )

            prevX = curr.x
            prevY = curr.y
            prevW = curr.width
        }
    }
}

/**
 * Renders text annotations in scene space.
 */
private fun DrawScope.drawTextAnnotation(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    annotation: CanvasTextAnnotation
) {
    drawText(
        textMeasurer = textMeasurer,
        text = annotation.text,
        topLeft = Offset(annotation.x, annotation.y),
        style = annotation.textStyle
    )
}

/**
 * Telemetry badge showing zoom, pan, active tool, and hardware palm rejection status.
 */
@Composable
fun InfiniteCanvasDiagnosticsBadge(
    diagnostics: InputDiagnostics,
    touchModeEnabled: Boolean,
    zoomScale: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tool: ${diagnostics.toolTypeName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (touchModeEnabled) " [Touch On]" else " [Palm Rejection Active]",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (touchModeEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text = "Zoom: %.1fx | Pan: (%.0f, %.0f)".format(zoomScale, panOffsetX, panOffsetY),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Pressure: %.2f | Accepted: %d | Palm Rej: %d".format(
                    diagnostics.lastPressure,
                    diagnostics.acceptedEventsCount,
                    diagnostics.rejectedEventsCount
                ),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (diagnostics.rejectedEventsCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun InfiniteCanvasPreview() {
    OpenMathPadTheme {
        InfiniteCanvas()
    }
}
