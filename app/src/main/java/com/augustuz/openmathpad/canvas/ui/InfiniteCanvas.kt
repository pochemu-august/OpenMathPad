package com.augustuz.openmathpad.canvas.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.augustuz.openmathpad.canvas.media.TaskImageManager
import com.augustuz.openmathpad.canvas.model.AdaptiveInkColor
import com.augustuz.openmathpad.canvas.model.CanvasImage
import com.augustuz.openmathpad.canvas.model.CanvasShape
import com.augustuz.openmathpad.canvas.model.CanvasShapeType
import com.augustuz.openmathpad.canvas.model.CanvasTextAnnotation
import com.augustuz.openmathpad.canvas.model.CanvasTool
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.GeometricShape
import com.augustuz.openmathpad.canvas.state.CanvasViewModel
import com.augustuz.openmathpad.canvas.state.InputDiagnostics
import com.augustuz.openmathpad.ui.theme.OpenMathPadTheme
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * GeoGebra-Inspired Infinite Canvas Math & Physics Scratchpad supporting:
 * - Infinite pan & zoom coordinate transformations (0.2x to 5.0x).
 * - Touch vs Stylus separation with 100% palm rejection.
 * - Crisp, uniform Ballpoint Pen inking with Midpoint Quadratic Bezier smoothing.
 * - Draw & Hold line straightening and magnetic vertex/grid snapping (GeoGebra-Lite).
 * - Classic Squared Notebook Grid (тетрадь в клеточку) & Cartesian Coordinate Axes.
 * - Interactive draggable and resizable problem screenshot cards with proportional 4-corner scaling.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InfiniteCanvas(
    modifier: Modifier = Modifier,
    viewModel: CanvasViewModel = viewModel(),
    showToolbar: Boolean = true,
    showDiagnostics: Boolean = true,
    backgroundColor: Color = Color(0xFFFFFFFF)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    androidx.compose.runtime.LaunchedEffect(uiState.hapticFeedbackTrigger) {
        if (uiState.hapticFeedbackTrigger > 0L) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                viewModel.importImageFromUri(
                    context = context.applicationContext,
                    uri = it,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx
                )
            }
        }

        val panX = uiState.panOffsetX
        val panY = uiState.panOffsetY
        val zoom = uiState.zoomScale
        val isDark = uiState.config.isDarkMode
        // Gitpod Developer Theme:
        // Dark mode: Canvas bg #121417, subtle grid #24272E, axes #38BDF8
        // Light mode: Canvas bg #FFFFFF, grid #E2E8F0, axes #1D4ED8
        val gridLineColor = if (isDark) Color(0xFF24272E) else Color(0xFFE2E8F0)
        val axesColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF1D4ED8)
        val effectiveBgColor = if (isDark) Color(0xFF121417) else backgroundColor

        // 1. Hardware-Accelerated Drawing Surface (Squared Grid + Coordinate Axes + Vector Inking)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .background(effectiveBgColor)
                .pointerInteropFilter { motionEvent ->
                    viewModel.processMotionEvent(motionEvent)
                }
        ) {
            // Classic Notebook Grid (Тетрадь в клеточку)
            if (uiState.config.showGrid) {
                drawInfiniteSquaredGrid(
                    panOffsetX = panX,
                    panOffsetY = panY,
                    zoomScale = zoom,
                    gridSizePx = with(density) { 36.dp.toPx() },
                    lineColor = gridLineColor
                )
            }

            // Cartesian Coordinate System (X & Y axes with numerical ticks, arrows, and origin)
            if (uiState.config.showCoordinateAxes) {
                drawCoordinateAxes(
                    panOffsetX = panX,
                    panOffsetY = panY,
                    zoomScale = zoom,
                    gridSizePx = with(density) { 36.dp.toPx() },
                    textMeasurer = textMeasurer,
                    axesColor = axesColor
                )
            }

            // Render vector strokes, shapes, and annotations in transformed scene space with 144Hz viewport culling
            withTransform({
                translate(left = panX, top = panY)
                scale(scaleX = zoom, scaleY = zoom, pivot = Offset.Zero)
            }) {
                val visibleRect = Rect(
                    left = -panX / zoom,
                    top = -panY / zoom,
                    right = (-panX + size.width) / zoom,
                    bottom = (-panY + size.height) / zoom
                )

                val strokes = uiState.strokes
                val activeStroke = uiState.currentActiveStroke

                // LAYER 1 (Underneath): Render ALL Highlighter strokes intersecting visibleRect
                for (i in strokes.indices) {
                    val s = strokes[i]
                    if (s.tool == CanvasTool.HIGHLIGHTER && s.bounds.overlaps(visibleRect)) {
                        drawStroke(s, isDark)
                    }
                }
                if (activeStroke?.tool == CanvasTool.HIGHLIGHTER) {
                    drawStroke(activeStroke, isDark)
                }

                // LAYER 2: Render ALL Ballpoint Pen strokes intersecting visibleRect
                for (i in strokes.indices) {
                    val s = strokes[i]
                    if (s.tool == CanvasTool.PEN && s.bounds.overlaps(visibleRect)) {
                        drawStroke(s, isDark)
                    }
                }
                if (activeStroke?.tool == CanvasTool.PEN) {
                    drawStroke(activeStroke, isDark)
                }

                // LAYER 3: Render Geometric Shapes intersecting visibleRect
                val shapes = uiState.shapes
                for (i in shapes.indices) {
                    val sh = shapes[i]
                    if (sh.bounds.overlaps(visibleRect)) {
                        sh.draw(this, isDark)
                    }
                }
                uiState.currentActiveShapePreview?.let { preview ->
                    preview.draw(this, isDark)
                }

                // Render text annotations
                val annotations = uiState.canvasTextAnnotations
                for (i in annotations.indices) {
                    drawTextAnnotation(textMeasurer, annotations[i])
                }
            }
        }

        // 2. Interactive Media / Problem Screenshot Cards (Draggable & 4-Corner Resizable)
        uiState.images.forEach { canvasImage ->
            CanvasImageCard(
                image = canvasImage,
                panOffsetX = panX,
                panOffsetY = panY,
                zoomScale = zoom,
                onPositionChange = { newX, newY ->
                    viewModel.updateImagePosition(canvasImage.id, newX, newY)
                },
                onGeometryChange = { newX, newY, newW, newH ->
                    viewModel.updateImageGeometry(canvasImage.id, newX, newY, newW, newH)
                },
                onCommit = { committedImage ->
                    viewModel.updateTaskImage(committedImage)
                },
                onDelete = {
                    viewModel.removeImage(canvasImage.id)
                },
                onSelect = {
                    viewModel.selectImage(canvasImage.id)
                }
            )
        }

        // 3. Interactive Geometric Shape Selection Overlay (Bounding Frame, Handles, Delete)
        val selectedShape = uiState.shapes.find { it.id == uiState.selectedShapeId }
        if (selectedShape != null) {
            CanvasShapeOverlay(
                shape = selectedShape,
                panOffsetX = panX,
                panOffsetY = panY,
                zoomScale = zoom,
                onGeometryChange = { newStartX, newStartY, newEndX, newEndY ->
                    viewModel.updateShapeGeometry(selectedShape.id, newStartX, newStartY, newEndX, newEndY)
                },
                onCommit = { committedShape ->
                    viewModel.commitShape(committedShape)
                },
                onDelete = {
                    viewModel.removeShape(selectedShape.id)
                }
            )
        }

        if (showToolbar) {
            CanvasToolbar(
                uiState = uiState,
                onToolSelected = viewModel::setTool,
                onActiveShapeSelected = viewModel::setActiveShape,
                onColorSelected = viewModel::setColor,
                onStrokeWidthChanged = viewModel::setStrokeWidth,
                onTouchModeToggled = viewModel::toggleTouchMode,
                onToggleRulerMode = viewModel::toggleRulerMode,
                onToggleGrid = viewModel::toggleGrid,
                onToggleCoordinateAxes = viewModel::toggleCoordinateAxes,
                onToggleDarkMode = viewModel::toggleDarkMode,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onClear = viewModel::clear,
                onAddImageClicked = { imagePickerLauncher.launch("image/*") },
                onResetZoom = viewModel::resetZoom,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // 4. Input Telemetry & Palm Rejection Diagnostics
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
 * Positioned in scene space, draggable from center, proportionally resizable via 4 corner handles.
 */
@Composable
private fun CanvasImageCard(
    image: CanvasImage,
    panOffsetX: Float,
    panOffsetY: Float,
    zoomScale: Float,
    onPositionChange: (Float, Float) -> Unit,
    onGeometryChange: (Float, Float, Float, Float) -> Unit,
    onCommit: (CanvasImage) -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    val density = LocalDensity.current

    // Live local state for smooth, un-interrupted 144Hz dragging and resizing
    var liveX by remember(image.id) { mutableFloatStateOf(image.x) }
    var liveY by remember(image.id) { mutableFloatStateOf(image.y) }
    var liveWidth by remember(image.id) { mutableFloatStateOf(image.width) }
    var liveHeight by remember(image.id) { mutableFloatStateOf(image.height) }

    // Keep live state in sync with external image state updates (undo, redo, etc.)
    LaunchedEffect(image.x, image.y, image.width, image.height) {
        liveX = image.x
        liveY = image.y
        liveWidth = image.width
        liveHeight = image.height
    }

    // Convert scene coordinates to screen pixels
    val screenX = liveX * zoomScale + panOffsetX
    val screenY = liveY * zoomScale + panOffsetY
    val screenWidthPx = liveWidth * zoomScale
    val screenHeightPx = liveHeight * zoomScale

    val widthDp = with(density) { screenWidthPx.toDp() }
    val heightDp = with(density) { screenHeightPx.toDp() }

    val aspectRatio = max(0.2f, liveWidth / max(1f, liveHeight))

    Box(
        modifier = Modifier
            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
            .size(width = widthDp, height = heightDp)
    ) {
        // Main Image Container
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (image.isSelected) 8.dp else 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    detectDragGestures(
                        onDragStart = { onSelect() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val sceneDeltaX = dragAmount.x / zoomScale
                            val sceneDeltaY = dragAmount.y / zoomScale
                            liveX += sceneDeltaX
                            liveY += sceneDeltaY
                            onPositionChange(liveX, liveY)
                        },
                        onDragEnd = {
                            val committed = image.copy(
                                x = liveX,
                                y = liveY,
                                width = liveWidth,
                                height = liveHeight
                            )
                            onCommit(committed)
                        },
                        onDragCancel = {
                            val committed = image.copy(
                                x = liveX,
                                y = liveY,
                                width = liveWidth,
                                height = liveHeight
                            )
                            onCommit(committed)
                        }
                    )
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = File(image.filePath),
                    contentDescription = "Inserted Task Screenshot",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Selection Controls: Delete button and 4 proportional corner resize handles
        if (image.isSelected) {
            // Delete button at top-right
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp)
                    .size(32.dp)
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete Image",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 1. Top-Left Corner Resize Handle (Anchored at Bottom-Right: liveX + liveWidth, liveY + liveHeight)
            ResizeCornerHandle(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-10).dp, y = (-10).dp),
                onDrag = { dragAmount ->
                    val sceneDeltaX = dragAmount.x / zoomScale
                    val sceneDeltaY = dragAmount.y / zoomScale
                    val deltaScale = (-sceneDeltaX - sceneDeltaY * aspectRatio) / 2f
                    val newWidth = max(64f, liveWidth + deltaScale)
                    val newHeight = newWidth / aspectRatio
                    liveX = liveX + (liveWidth - newWidth)
                    liveY = liveY + (liveHeight - newHeight)
                    liveWidth = newWidth
                    liveHeight = newHeight
                    onGeometryChange(liveX, liveY, liveWidth, liveHeight)
                },
                onDragEnd = {
                    val committed = image.copy(x = liveX, y = liveY, width = liveWidth, height = liveHeight)
                    onCommit(committed)
                }
            )

            // 2. Top-Right Corner Resize Handle (Anchored at Bottom-Left: liveX, liveY + liveHeight)
            ResizeCornerHandle(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp),
                onDrag = { dragAmount ->
                    val sceneDeltaX = dragAmount.x / zoomScale
                    val sceneDeltaY = dragAmount.y / zoomScale
                    val deltaScale = (sceneDeltaX - sceneDeltaY * aspectRatio) / 2f
                    val newWidth = max(64f, liveWidth + deltaScale)
                    val newHeight = newWidth / aspectRatio
                    liveY = liveY + (liveHeight - newHeight)
                    liveWidth = newWidth
                    liveHeight = newHeight
                    onGeometryChange(liveX, liveY, liveWidth, liveHeight)
                },
                onDragEnd = {
                    val committed = image.copy(x = liveX, y = liveY, width = liveWidth, height = liveHeight)
                    onCommit(committed)
                }
            )

            // 3. Bottom-Left Corner Resize Handle (Anchored at Top-Right: liveX + liveWidth, liveY)
            ResizeCornerHandle(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-10).dp, y = 10.dp),
                onDrag = { dragAmount ->
                    val sceneDeltaX = dragAmount.x / zoomScale
                    val sceneDeltaY = dragAmount.y / zoomScale
                    val deltaScale = (-sceneDeltaX + sceneDeltaY * aspectRatio) / 2f
                    val newWidth = max(64f, liveWidth + deltaScale)
                    val newHeight = newWidth / aspectRatio
                    liveX = liveX + (liveWidth - newWidth)
                    liveWidth = newWidth
                    liveHeight = newHeight
                    onGeometryChange(liveX, liveY, liveWidth, liveHeight)
                },
                onDragEnd = {
                    val committed = image.copy(x = liveX, y = liveY, width = liveWidth, height = liveHeight)
                    onCommit(committed)
                }
            )

            // 4. Bottom-Right Corner Resize Handle (Anchored at Top-Left: liveX, liveY)
            ResizeCornerHandle(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp),
                onDrag = { dragAmount ->
                    val sceneDeltaX = dragAmount.x / zoomScale
                    val sceneDeltaY = dragAmount.y / zoomScale
                    val deltaScale = (sceneDeltaX + sceneDeltaY * aspectRatio) / 2f
                    val newWidth = max(64f, liveWidth + deltaScale)
                    val newHeight = newWidth / aspectRatio
                    liveWidth = newWidth
                    liveHeight = newHeight
                    onGeometryChange(liveX, liveY, liveWidth, liveHeight)
                },
                onDragEnd = {
                    val committed = image.copy(x = liveX, y = liveY, width = liveWidth, height = liveHeight)
                    onCommit(committed)
                }
            )
        }
    }
}

/**
 * Circular resize handle positioned on bounding box corners.
 */
@Composable
private fun ResizeCornerHandle(
    modifier: Modifier = Modifier,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = modifier
            .size(24.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = {
                        onDragEnd()
                    },
                    onDragCancel = {
                        onDragEnd()
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .background(Color.White, CircleShape)
        )
    }
}

/**
 * Interactive Selection Overlay for CanvasShape (Vector, Line, Box, Circle).
 * Shows bounding frame, delete button, and handles for real-time manipulation.
 */
@Composable
private fun CanvasShapeOverlay(
    shape: CanvasShape,
    panOffsetX: Float,
    panOffsetY: Float,
    zoomScale: Float,
    onGeometryChange: (Float, Float, Float, Float) -> Unit,
    onCommit: (CanvasShape) -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current

    when (shape.type) {
        CanvasShapeType.VECTOR, CanvasShapeType.LINE -> {
            val startScreenX = shape.startX * zoomScale + panOffsetX
            val startScreenY = shape.startY * zoomScale + panOffsetY
            val endScreenX = shape.endX * zoomScale + panOffsetX
            val endScreenY = shape.endY * zoomScale + panOffsetY
            val midScreenX = (startScreenX + endScreenX) / 2f
            val midScreenY = (startScreenY + endScreenY) / 2f

            var liveStartX by remember(shape.id, shape.startX) { mutableFloatStateOf(shape.startX) }
            var liveStartY by remember(shape.id, shape.startY) { mutableFloatStateOf(shape.startY) }
            var liveEndX by remember(shape.id, shape.endX) { mutableFloatStateOf(shape.endX) }
            var liveEndY by remember(shape.id, shape.endY) { mutableFloatStateOf(shape.endY) }

            // Delete button at midpoint of line / vector
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .offset { IntOffset((midScreenX - 16f).roundToInt(), (midScreenY - 36f).roundToInt()) }
                    .size(28.dp)
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete Shape",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Start endpoint handle
            ResizeCornerHandle(
                modifier = Modifier.offset {
                    IntOffset(
                        (liveStartX * zoomScale + panOffsetX - 12f).roundToInt(),
                        (liveStartY * zoomScale + panOffsetY - 12f).roundToInt()
                    )
                },
                onDrag = { dragAmount ->
                    liveStartX += dragAmount.x / zoomScale
                    liveStartY += dragAmount.y / zoomScale
                    onGeometryChange(liveStartX, liveStartY, liveEndX, liveEndY)
                },
                onDragEnd = {
                    onCommit(shape.copy(startX = liveStartX, startY = liveStartY, endX = liveEndX, endY = liveEndY))
                }
            )

            // End endpoint handle
            ResizeCornerHandle(
                modifier = Modifier.offset {
                    IntOffset(
                        (liveEndX * zoomScale + panOffsetX - 12f).roundToInt(),
                        (liveEndY * zoomScale + panOffsetY - 12f).roundToInt()
                    )
                },
                onDrag = { dragAmount ->
                    liveEndX += dragAmount.x / zoomScale
                    liveEndY += dragAmount.y / zoomScale
                    onGeometryChange(liveStartX, liveStartY, liveEndX, liveEndY)
                },
                onDragEnd = {
                    onCommit(shape.copy(startX = liveStartX, startY = liveStartY, endX = liveEndX, endY = liveEndY))
                }
            )
        }
        CanvasShapeType.BOX, CanvasShapeType.CIRCLE -> {
            val minX = shape.minX
            val minY = shape.minY
            val maxX = shape.maxX
            val maxY = shape.maxY
            val width = max(24f, maxX - minX)
            val height = max(24f, maxY - minY)

            var liveStartX by remember(shape.id, shape.startX) { mutableFloatStateOf(shape.startX) }
            var liveStartY by remember(shape.id, shape.startY) { mutableFloatStateOf(shape.startY) }
            var liveEndX by remember(shape.id, shape.endX) { mutableFloatStateOf(shape.endX) }
            var liveEndY by remember(shape.id, shape.endY) { mutableFloatStateOf(shape.endY) }

            val screenX = minX * zoomScale + panOffsetX
            val screenY = minY * zoomScale + panOffsetY
            val screenWidthPx = width * zoomScale
            val screenHeightPx = height * zoomScale

            val widthDp = with(density) { screenWidthPx.toDp() }
            val heightDp = with(density) { screenHeightPx.toDp() }

            Box(
                modifier = Modifier
                    .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                    .size(width = widthDp, height = heightDp)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(if (shape.type == CanvasShapeType.CIRCLE) 50.dp else 4.dp)
                    )
            ) {
                // Delete button at top-right
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-12).dp)
                        .size(28.dp)
                ) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete Shape",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Top-Left Resize Handle
                ResizeCornerHandle(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-10).dp, y = (-10).dp),
                    onDrag = { dragAmount ->
                        val deltaX = dragAmount.x / zoomScale
                        val deltaY = dragAmount.y / zoomScale
                        liveStartX += deltaX
                        liveStartY += deltaY
                        onGeometryChange(liveStartX, liveStartY, liveEndX, liveEndY)
                    },
                    onDragEnd = {
                        onCommit(shape.copy(startX = liveStartX, startY = liveStartY, endX = liveEndX, endY = liveEndY))
                    }
                )

                // Top-Right Resize Handle
                ResizeCornerHandle(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-10).dp),
                    onDrag = { dragAmount ->
                        val deltaX = dragAmount.x / zoomScale
                        val deltaY = dragAmount.y / zoomScale
                        liveEndX += deltaX
                        liveStartY += deltaY
                        onGeometryChange(liveStartX, liveStartY, liveEndX, liveEndY)
                    },
                    onDragEnd = {
                        onCommit(shape.copy(startX = liveStartX, startY = liveStartY, endX = liveEndX, endY = liveEndY))
                    }
                )

                // Bottom-Left Resize Handle
                ResizeCornerHandle(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-10).dp, y = 10.dp),
                    onDrag = { dragAmount ->
                        val deltaX = dragAmount.x / zoomScale
                        val deltaY = dragAmount.y / zoomScale
                        liveStartX += deltaX
                        liveEndY += deltaY
                        onGeometryChange(liveStartX, liveStartY, liveEndX, liveEndY)
                    },
                    onDragEnd = {
                        onCommit(shape.copy(startX = liveStartX, startY = liveStartY, endX = liveEndX, endY = liveEndY))
                    }
                )

                // Bottom-Right Resize Handle
                ResizeCornerHandle(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 10.dp, y = 10.dp),
                    onDrag = { dragAmount ->
                        val deltaX = dragAmount.x / zoomScale
                        val deltaY = dragAmount.y / zoomScale
                        liveEndX += deltaX
                        liveEndY += deltaY
                        onGeometryChange(liveStartX, liveStartY, liveEndX, liveEndY)
                    },
                    onDragEnd = {
                        onCommit(shape.copy(startX = liveStartX, startY = liveStartY, endX = liveEndX, endY = liveEndY))
                    }
                )
            }
        }
    }
}

/**
 * Draws classic squared notebook grid (тетрадь в клеточку) dynamically scaled with infinite canvas.
 * Draws lines only within the visible viewport bounds for O(1) 144Hz performance.
 */
private fun DrawScope.drawInfiniteSquaredGrid(
    panOffsetX: Float,
    panOffsetY: Float,
    zoomScale: Float,
    gridSizePx: Float = 36f,
    lineColor: Color = Color(0xFFE0E3EA)
) {
    val scale = if (zoomScale <= 0f) 1.0f else zoomScale
    val effectiveGridSize = gridSizePx * scale

    val stepMultiplier = when {
        effectiveGridSize < 12f -> 4
        effectiveGridSize < 20f -> 2
        else -> 1
    }
    val gridStep = gridSizePx * stepMultiplier

    val sceneLeft = -panOffsetX / scale
    val sceneTop = -panOffsetY / scale
    val sceneRight = (size.width - panOffsetX) / scale
    val sceneBottom = (size.height - panOffsetY) / scale

    val startX = floor(sceneLeft / gridStep) * gridStep
    val endX = ceil(sceneRight / gridStep) * gridStep
    val startY = floor(sceneTop / gridStep) * gridStep
    val endY = ceil(sceneBottom / gridStep) * gridStep

    // Draw vertical grid lines
    var x = startX
    while (x <= endX) {
        val screenX = x * scale + panOffsetX
        drawLine(
            color = lineColor,
            start = Offset(screenX, 0f),
            end = Offset(screenX, size.height),
            strokeWidth = 1f
        )
        x += gridStep
    }

    // Draw horizontal grid lines
    var y = startY
    while (y <= endY) {
        val screenY = y * scale + panOffsetY
        drawLine(
            color = lineColor,
            start = Offset(0f, screenY),
            end = Offset(size.width, screenY),
            strokeWidth = 1f
        )
        y += gridStep
    }
}

/**
 * Draws a Cartesian Coordinate System (X and Y axes with directional arrows and origin point (0, 0))
 * aligned with the notebook grid in scene space.
 */
private fun DrawScope.drawCoordinateAxes(
    panOffsetX: Float,
    panOffsetY: Float,
    zoomScale: Float,
    gridSizePx: Float = 36f,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    axesColor: Color = Color(0xFF1565C0)
) {
    val scale = if (zoomScale <= 0f) 1.0f else zoomScale
    val step = gridSizePx * scale

    // Screen coordinates of axes
    val screenXAxisY = 0f * scale + panOffsetY // y = 0 line
    val screenYAxisX = 0f * scale + panOffsetX // x = 0 line

    // Numerical tick marks along X-axis
    if (screenXAxisY in -60f..(size.height + 60f) && step > 10f) {
        val startK = kotlin.math.floor((0f - screenYAxisX) / step).toInt() - 1
        val endK = kotlin.math.ceil((size.width - screenYAxisX) / step).toInt() + 1
        val tickInterval = if (step < 25f) 5 else if (step < 50f) 2 else 1

        for (k in startK..endK) {
            if (k == 0) continue
            if (k % tickInterval != 0) continue
            val tickX = screenYAxisX + k * step
            if (tickX in 0f..size.width) {
                drawLine(
                    color = axesColor.copy(alpha = 0.6f),
                    start = Offset(tickX, screenXAxisY - 4f),
                    end = Offset(tickX, screenXAxisY + 4f),
                    strokeWidth = 1.5f
                )
                if (step >= 25f || k % (tickInterval * 2) == 0) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "$k",
                        topLeft = Offset(tickX - 6f, screenXAxisY + 6f),
                        style = androidx.compose.ui.text.TextStyle(
                            color = axesColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // Draw X-axis
        drawLine(
            color = axesColor,
            start = Offset(0f, screenXAxisY),
            end = Offset(size.width, screenXAxisY),
            strokeWidth = 2.5f
        )
        val arrowSize = 14f
        drawLine(
            color = axesColor,
            start = Offset(size.width - arrowSize, screenXAxisY - arrowSize / 2f),
            end = Offset(size.width - 2f, screenXAxisY),
            strokeWidth = 2.5f
        )
        drawLine(
            color = axesColor,
            start = Offset(size.width - arrowSize, screenXAxisY + arrowSize / 2f),
            end = Offset(size.width - 2f, screenXAxisY),
            strokeWidth = 2.5f
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "x",
            topLeft = Offset(size.width - 24f, screenXAxisY + 4f),
            style = androidx.compose.ui.text.TextStyle(
                color = axesColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }

    // Numerical tick marks along Y-axis (positive Y points upwards towards negative sceneY)
    if (screenYAxisX in -60f..(size.width + 60f) && step > 10f) {
        val startK = kotlin.math.floor((screenXAxisY - size.height) / step).toInt() - 1
        val endK = kotlin.math.ceil((screenXAxisY - 0f) / step).toInt() + 1
        val tickInterval = if (step < 25f) 5 else if (step < 50f) 2 else 1

        for (k in startK..endK) {
            if (k == 0) continue
            if (k % tickInterval != 0) continue
            val tickY = screenXAxisY - k * step
            if (tickY in 0f..size.height) {
                drawLine(
                    color = axesColor.copy(alpha = 0.6f),
                    start = Offset(screenYAxisX - 4f, tickY),
                    end = Offset(screenYAxisX + 4f, tickY),
                    strokeWidth = 1.5f
                )
                if (step >= 25f || k % (tickInterval * 2) == 0) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "$k",
                        topLeft = Offset(screenYAxisX + 6f, tickY - 7f),
                        style = androidx.compose.ui.text.TextStyle(
                            color = axesColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // Draw Y-axis (positive Y points upwards towards negative sceneY)
        drawLine(
            color = axesColor,
            start = Offset(screenYAxisX, 0f),
            end = Offset(screenYAxisX, size.height),
            strokeWidth = 2.5f
        )
        val arrowSize = 14f
        drawLine(
            color = axesColor,
            start = Offset(screenYAxisX - arrowSize / 2f, arrowSize),
            end = Offset(screenYAxisX, 2f),
            strokeWidth = 2.5f
        )
        drawLine(
            color = axesColor,
            start = Offset(screenYAxisX + arrowSize / 2f, arrowSize),
            end = Offset(screenYAxisX, 2f),
            strokeWidth = 2.5f
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "y",
            topLeft = Offset(screenYAxisX + 6f, 4f),
            style = androidx.compose.ui.text.TextStyle(
                color = axesColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }

    // Origin marker (0, 0)
    if (screenYAxisX in 0f..size.width && screenXAxisY in 0f..size.height) {
        drawCircle(
            color = axesColor,
            radius = 3.5f,
            center = Offset(screenYAxisX, screenXAxisY)
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "(0,0)",
            topLeft = Offset(screenYAxisX + 5f, screenXAxisY + 3f),
            style = androidx.compose.ui.text.TextStyle(
                color = axesColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/**
 * Renders a vector stroke using cached Midpoint Quadratic Bezier path and pre-cached Stroke style.
 * Completely eliminates per-frame polygon generation, ribbon meshing, and object allocations inside DrawScope.
 */
private fun DrawScope.drawStroke(stroke: DrawingStroke, isDark: Boolean = false) {
    val points = stroke.points
    if (points.isEmpty()) return

    val effectiveColor = stroke.getEffectiveColor(isDark)

    if (points.size == 1) {
        val p = points[0]
        drawCircle(
            color = effectiveColor,
            radius = stroke.strokeWidth / 2f,
            center = Offset(p.x, p.y),
            alpha = stroke.alpha
        )
        return
    }

    if (stroke.shape == GeometricShape.CIRCLE && points.size >= 2) {
        val center = points[0]
        val edge = points[1]
        val radius = kotlin.math.hypot(edge.x - center.x, edge.y - center.y)
        drawCircle(
            color = effectiveColor,
            radius = radius,
            center = Offset(center.x, center.y),
            alpha = stroke.alpha,
            style = stroke.strokeStyle
        )
        return
    }

    drawPath(
        path = stroke.path,
        color = effectiveColor,
        alpha = stroke.alpha,
        style = stroke.strokeStyle
    )
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
