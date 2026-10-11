package com.augustuz.openmathpad.canvas.state

import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.augustuz.openmathpad.canvas.media.TaskImageManager
import com.augustuz.openmathpad.canvas.model.CanvasConfig
import com.augustuz.openmathpad.canvas.model.CanvasImage
import com.augustuz.openmathpad.canvas.model.CanvasTextAnnotation
import com.augustuz.openmathpad.canvas.model.CanvasTool
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.GeometricShape
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.canvas.processing.BezierSmoother
import com.augustuz.openmathpad.canvas.processing.GeometrySnapper
import com.augustuz.openmathpad.canvas.processing.PalmRejectionFilter
import com.augustuz.openmathpad.canvas.processing.PressureSmoother
import com.augustuz.openmathpad.canvas.processing.StrokeEraser
import com.augustuz.openmathpad.canvas.processing.StrokeStabilizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * Diagnostic status for hardware input telemetry and palm rejection tracking.
 */
data class InputDiagnostics(
    val lastToolType: Int = MotionEvent.TOOL_TYPE_UNKNOWN,
    val toolTypeName: String = "UNKNOWN",
    val lastPressure: Float = 0f,
    val historicalPointsCount: Int = 0,
    val rejectedEventsCount: Int = 0,
    val acceptedEventsCount: Int = 0
)

/**
 * Historical snapshot of drawing surface and media images for undo/redo.
 */
data class CanvasSnapshot(
    val strokes: List<DrawingStroke>,
    val textAnnotations: List<CanvasTextAnnotation> = emptyList(),
    val images: List<CanvasImage> = emptyList()
)

/**
 * UI State for Infinite Canvas, Calligraphic Inking, and Media Task Insertion.
 */
data class CanvasUiState(
    val strokes: List<DrawingStroke> = emptyList(),
    val canvasTextAnnotations: List<CanvasTextAnnotation> = emptyList(),
    val images: List<CanvasImage> = emptyList(),
    val selectedImageId: String? = null,
    val currentActiveStroke: DrawingStroke? = null,
    val config: CanvasConfig = CanvasConfig(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val diagnostics: InputDiagnostics = InputDiagnostics(),
    val hapticFeedbackTrigger: Long = 0L,

    // Infinite Canvas Navigation & Transformation State
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val zoomScale: Float = 1.0f
)

/**
 * ViewModel managing infinite canvas coordinate transformations, stylus vs touch separation,
 * low-latency WYSIWYG Midpoint Quadratic Bezier inking, and image card interactions.
 */
class CanvasViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<CanvasSnapshot>()
    private val redoStack = mutableListOf<CanvasSnapshot>()

    private val pressureSmoother = PressureSmoother()
    private val strokeStabilizer = StrokeStabilizer()
    private val activePoints = mutableListOf<StrokePoint>()
    private var currentStrokeId: String? = null
    private var activePointerId: Int? = null
    private var activePointerIsStylus: Boolean = false

    // Geometry Draw & Hold Line Straightening
    private var holdStraightenJob: Job? = null
    private var isStrokeStraightened: Boolean = false
    private var lastMoveScreenX: Float = 0f
    private var lastMoveScreenY: Float = 0f

    // Two-Finger Gesture Navigation Tracking
    private var isGestureNavigating: Boolean = false
    private var lastGestureMidX: Float = 0f
    private var lastGestureMidY: Float = 0f
    private var lastGestureDistance: Float = 0f

    // Stroke Duration Tracking (Micro-tick / Stray Stroke Filtering)
    private var strokeStartTimeMs: Long = 0L

    // Interactive Media / Task Image Gesture Tracking
    enum class ImageDragHandle {
        NONE,
        BODY,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private var activeImageId: String? = null
    private var activeImageHandle: ImageDragHandle = ImageDragHandle.NONE
    private var imageDragStartSceneX: Float = 0f
    private var imageDragStartSceneY: Float = 0f
    private var imageInitialX: Float = 0f
    private var imageInitialY: Float = 0f
    private var imageInitialWidth: Float = 0f
    private var imageInitialHeight: Float = 0f
    private var imageMovedOrResized: Boolean = false

    /**
     * Clamps zoom scale to specified range [MIN_ZOOM, MAX_ZOOM].
     */
    fun clampZoom(scale: Float): Float = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * Resets canvas transformation to 100% zoom and centers viewport.
     */
    fun resetZoom() {
        _uiState.update { it.copy(zoomScale = 1.0f, panOffsetX = 0f, panOffsetY = 0f) }
    }

    /**
     * Sets pan offset coordinates.
     */
    fun setPan(panX: Float, panY: Float) {
        _uiState.update { it.copy(panOffsetX = panX, panOffsetY = panY) }
    }

    /**
     * Sets zoom scale, clamped between 0.2x and 5.0x.
     */
    fun setZoom(zoom: Float) {
        val clamped = clampZoom(zoom)
        _uiState.update { it.copy(zoomScale = clamped) }
    }

    /**
     * Atomically updates pan and zoom scale.
     */
    fun updatePanAndZoom(panX: Float, panY: Float, zoom: Float) {
        val clamped = clampZoom(zoom)
        _uiState.update { it.copy(panOffsetX = panX, panOffsetY = panY, zoomScale = clamped) }
    }

    /**
     * Converts screen pixel coordinate to canvas scene coordinate.
     */
    fun screenToSceneX(screenX: Float): Float {
        val panX = _uiState.value.panOffsetX
        val zoom = _uiState.value.zoomScale
        return (screenX - panX) / zoom
    }

    /**
     * Converts screen pixel coordinate to canvas scene coordinate.
     */
    fun screenToSceneY(screenY: Float): Float {
        val panY = _uiState.value.panOffsetY
        val zoom = _uiState.value.zoomScale
        return (screenY - panY) / zoom
    }

    /**
     * Processes incoming MotionEvent with:
     * - Stylus: draws strokes with real-time streamline stabilization and scene mapping.
     * - Two-finger gestures: pans and pinch-to-zooms the infinite canvas.
     * - Single finger: ignored by default (100% palm rejection) unless touchModeEnabled.
     */
    fun processMotionEvent(event: MotionEvent): Boolean {
        val config = _uiState.value.config
        val touchMode = config.touchModeEnabled
        val pointerCount = event.pointerCount
        val panX = _uiState.value.panOffsetX
        val panY = _uiState.value.panOffsetY
        val zoom = _uiState.value.zoomScale
        val sceneX = (event.x - panX) / zoom
        val sceneY = (event.y - panY) / zoom

        // 0. Interactive Task Image Manipulation (Hit-test, Drag, Resize, Delete)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (checkAndStartImageInteraction(event.x, event.y, sceneX, sceneY, zoom, panX, panY)) {
                    return true
                }
                // Tapping outside on empty canvas deselects any selected image
                if (_uiState.value.selectedImageId != null) {
                    selectImage(null)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeImageId != null) {
                    handleImageDrag(sceneX, sceneY)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeImageId != null) {
                    finishImageInteraction()
                    return true
                }
            }
        }

        // 1. Check if any pointer is a hardware stylus
        var stylusPointerIndex = -1
        for (i in 0 until pointerCount) {
            val tool = event.getToolType(i)
            if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                stylusPointerIndex = i
                break
            }
        }
        val hasStylus = stylusPointerIndex != -1

        // 2. Two-Finger Navigation Handling (Pan & Pinch-to-Zoom)
        if (!hasStylus && pointerCount >= 2) {
            if (activePointerId != null) {
                discardActiveStroke()
            }

            val p0X = event.getX(0)
            val p0Y = event.getY(0)
            val p1X = event.getX(1)
            val p1Y = event.getY(1)

            val midX = (p0X + p1X) / 2f
            val midY = (p0Y + p1Y) / 2f
            val dist = hypot(p0X - p1X, p0Y - p1Y)

            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                    isGestureNavigating = true
                    lastGestureMidX = midX
                    lastGestureMidY = midY
                    lastGestureDistance = max(10f, dist)
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isGestureNavigating && lastGestureDistance > 10f && dist > 10f) {
                        val scaleFactor = dist / lastGestureDistance
                        val deltaMidX = midX - lastGestureMidX
                        val deltaMidY = midY - lastGestureMidY

                        val currentZoom = _uiState.value.zoomScale
                        val newZoom = clampZoom(currentZoom * scaleFactor)

                        val currentPanX = _uiState.value.panOffsetX
                        val currentPanY = _uiState.value.panOffsetY

                        val sceneFocusX = (midX - currentPanX) / currentZoom
                        val sceneFocusY = (midY - currentPanY) / currentZoom

                        val newPanX = midX + deltaMidX - sceneFocusX * newZoom
                        val newPanY = midY + deltaMidY - sceneFocusY * newZoom

                        _uiState.update { it.copy(panOffsetX = newPanX, panOffsetY = newPanY, zoomScale = newZoom) }

                        lastGestureMidX = midX
                        lastGestureMidY = midY
                        lastGestureDistance = dist
                    } else {
                        isGestureNavigating = true
                        lastGestureMidX = midX
                        lastGestureMidY = midY
                        lastGestureDistance = max(10f, dist)
                    }
                }

                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isGestureNavigating = false
                }
            }
            return true
        }

        if (pointerCount < 2) {
            isGestureNavigating = false
        }

        // 3. Single-pointer drawing / palm rejection
        val candidateIndex = if (hasStylus) {
            stylusPointerIndex
        } else if (touchMode && pointerCount == 1) {
            0
        } else {
            -1
        }

        var activeIndex = if (activePointerId != null) event.findPointerIndex(activePointerId!!) else -1

        if (hasStylus && !activePointerIsStylus && stylusPointerIndex != -1) {
            discardActiveStroke()
            activePointerId = null
            activePointerIsStylus = false
            activeIndex = -1
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val actionIndex = event.actionIndex
                val actionTool = event.getToolType(actionIndex)
                val actionPointerId = event.getPointerId(actionIndex)

                val isStylusTouch = actionTool == MotionEvent.TOOL_TYPE_STYLUS || actionTool == MotionEvent.TOOL_TYPE_ERASER
                val isAllowedTouch = isStylusTouch || (touchMode && activePointerId == null)

                if (isAllowedTouch && (activePointerId == null || (isStylusTouch && !activePointerIsStylus))) {
                    activePointerId = actionPointerId
                    activePointerIsStylus = isStylusTouch
                    activeIndex = actionIndex

                    pressureSmoother.reset()
                    strokeStabilizer.reset()
                    activePoints.clear()
                    holdStraightenJob?.cancel()
                    isStrokeStraightened = false
                    strokeStartTimeMs = System.currentTimeMillis()
                    lastMoveScreenX = event.getX(actionIndex)
                    lastMoveScreenY = event.getY(actionIndex)

                    val strokeId = java.util.UUID.randomUUID().toString()
                    currentStrokeId = strokeId

                    val activeTool = if (PalmRejectionFilter.isHardwareEraser(actionTool)) {
                        CanvasTool.ERASER
                    } else {
                        config.activeTool
                    }

                    val extracted = BezierSmoother.extractPointsFromEvent(
                        event = event,
                        baseWidth = getBaseWidthForTool(activeTool, config),
                        pressureSmoother = pressureSmoother,
                        pointerIndex = actionIndex,
                        panOffsetX = panX,
                        panOffsetY = panY,
                        zoomScale = zoom,
                        strokeStabilizer = null
                    )

                    if (activeTool == CanvasTool.ERASER) {
                        saveUndoState()
                        eraseAtPoints(extracted, config.eraserRadius / zoom)
                        _uiState.update { state ->
                            state.copy(
                                canUndo = undoStack.isNotEmpty(),
                                canRedo = redoStack.isNotEmpty(),
                                diagnostics = state.diagnostics.copy(
                                    lastToolType = actionTool,
                                    toolTypeName = getToolTypeName(actionTool),
                                    lastPressure = event.getPressure(actionIndex),
                                    historicalPointsCount = event.historySize,
                                    acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                                )
                            )
                        }
                    } else {
                        // Magnetic vertex & grid intersection snapping for start point
                        val pointsWithStartSnap = if (extracted.isNotEmpty()) {
                            val p0 = extracted.first()
                            val (sx, sy) = GeometrySnapper.snapPoint(
                                x = p0.x,
                                y = p0.y,
                                existingStrokes = _uiState.value.strokes,
                                gridSize = 36f,
                                isGridEnabled = config.showGrid,
                                snapThreshold = 24f
                            )
                            listOf(p0.copy(x = sx, y = sy)) + extracted.drop(1)
                        } else extracted

                        activePoints.addAll(pointsWithStartSnap)

                        if (config.rulerModeEnabled) {
                            isStrokeStraightened = true
                        } else {
                            scheduleHoldStraighten()
                        }

                        val newStroke = createStrokeFromPoints(
                            points = activePoints.toList(),
                            tool = activeTool,
                            config = config,
                            id = strokeId,
                            shape = config.activeShape
                        )
                        _uiState.update { state ->
                            state.copy(
                                currentActiveStroke = newStroke,
                                diagnostics = state.diagnostics.copy(
                                    lastToolType = actionTool,
                                    toolTypeName = getToolTypeName(actionTool),
                                    lastPressure = event.getPressure(actionIndex),
                                    historicalPointsCount = event.historySize,
                                    acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                                )
                            )
                        }
                    }
                } else if (!isAllowedTouch) {
                    _uiState.update { state ->
                        state.copy(
                            diagnostics = state.diagnostics.copy(
                                lastToolType = actionTool,
                                toolTypeName = getToolTypeName(actionTool),
                                rejectedEventsCount = state.diagnostics.rejectedEventsCount + 1
                            )
                        )
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == null && candidateIndex != -1) {
                    val candidateTool = event.getToolType(candidateIndex)
                    activePointerId = event.getPointerId(candidateIndex)
                    activePointerIsStylus = candidateTool == MotionEvent.TOOL_TYPE_STYLUS || candidateTool == MotionEvent.TOOL_TYPE_ERASER
                    activeIndex = candidateIndex

                    pressureSmoother.reset()
                    strokeStabilizer.reset()
                    activePoints.clear()
                    holdStraightenJob?.cancel()
                    isStrokeStraightened = false
                    strokeStartTimeMs = System.currentTimeMillis()
                    lastMoveScreenX = event.getX(candidateIndex)
                    lastMoveScreenY = event.getY(candidateIndex)
                    currentStrokeId = java.util.UUID.randomUUID().toString()
                }

                if (activePointerId != null && activeIndex != -1) {
                    val pointerTool = event.getToolType(activeIndex)
                    val activeTool = if (PalmRejectionFilter.isHardwareEraser(pointerTool)) {
                        CanvasTool.ERASER
                    } else {
                        config.activeTool
                    }

                    val extracted = BezierSmoother.extractPointsFromEvent(
                        event = event,
                        baseWidth = getBaseWidthForTool(activeTool, config),
                        pressureSmoother = pressureSmoother,
                        pointerIndex = activeIndex,
                        panOffsetX = panX,
                        panOffsetY = panY,
                        zoomScale = zoom,
                        strokeStabilizer = null
                    )

                    if (activeTool == CanvasTool.ERASER) {
                        eraseAtPoints(extracted, config.eraserRadius / zoom)
                        _uiState.update { state ->
                            state.copy(
                                canUndo = undoStack.isNotEmpty(),
                                canRedo = redoStack.isNotEmpty(),
                                diagnostics = state.diagnostics.copy(
                                    lastToolType = pointerTool,
                                    toolTypeName = getToolTypeName(pointerTool),
                                    lastPressure = event.getPressure(activeIndex),
                                    historicalPointsCount = event.historySize,
                                    acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                                )
                            )
                        }
                    } else {
                        val curScreenX = event.getX(activeIndex)
                        val curScreenY = event.getY(activeIndex)
                        val distMoved = hypot(curScreenX - lastMoveScreenX, curScreenY - lastMoveScreenY)

                        if (distMoved > 8f) {
                            lastMoveScreenX = curScreenX
                            lastMoveScreenY = curScreenY
                            if (!isStrokeStraightened && !config.rulerModeEnabled && config.activeShape == GeometricShape.NONE) {
                                scheduleHoldStraighten()
                            }
                        }

                        if (config.activeShape != GeometricShape.NONE) {
                            // Geometric Shape drawing mode (Line, Vector Arrow, Rectangle, Circle)
                            if (activePoints.isNotEmpty() && extracted.isNotEmpty()) {
                                val startPt = activePoints.first()
                                val currentPt = extracted.last()
                                val shapePts = generateShapePoints(config.activeShape, startPt, currentPt, config)
                                val updatedStroke = createStrokeFromPoints(
                                    points = shapePts,
                                    tool = activeTool,
                                    config = config,
                                    id = currentStrokeId ?: java.util.UUID.randomUUID().toString(),
                                    shape = config.activeShape
                                )
                                _uiState.update { state ->
                                    state.copy(
                                        currentActiveStroke = updatedStroke,
                                        diagnostics = state.diagnostics.copy(
                                            lastToolType = pointerTool,
                                            toolTypeName = getToolTypeName(pointerTool),
                                            lastPressure = event.getPressure(activeIndex),
                                            historicalPointsCount = event.historySize,
                                            acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                                        )
                                    )
                                }
                            }
                        } else if (isStrokeStraightened || config.rulerModeEnabled) {
                            // Straightened mode: draws straight line connecting start and current point with snap
                            if (activePoints.isNotEmpty() && extracted.isNotEmpty()) {
                                val startPt = activePoints.first()
                                val currentPt = extracted.last()
                                val straightPts = GeometrySnapper.createStraightLinePoints(
                                    points = listOf(startPt, currentPt),
                                    existingStrokes = _uiState.value.strokes,
                                    gridSize = 36f,
                                    isGridEnabled = config.showGrid,
                                    snapThreshold = 20f
                                )
                                val updatedStroke = createStrokeFromPoints(
                                    points = straightPts,
                                    tool = activeTool,
                                    config = config,
                                    id = currentStrokeId ?: java.util.UUID.randomUUID().toString(),
                                    shape = GeometricShape.LINE
                                )
                                _uiState.update { state ->
                                    state.copy(
                                        currentActiveStroke = updatedStroke,
                                        diagnostics = state.diagnostics.copy(
                                            lastToolType = pointerTool,
                                            toolTypeName = getToolTypeName(pointerTool),
                                            lastPressure = event.getPressure(activeIndex),
                                            historicalPointsCount = event.historySize,
                                            acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                                        )
                                    )
                                }
                            }
                        } else {
                            // Normal Ballpoint Inking: Filter points with >= 3.0px distance to eliminate blobs
                            for (pt in extracted) {
                                if (activePoints.isEmpty()) {
                                    activePoints.add(pt)
                                } else {
                                    val lastPt = activePoints.last()
                                    val distPx = hypot(pt.x - lastPt.x, pt.y - lastPt.y) * zoom
                                    if (distPx >= 3.0f) {
                                        activePoints.add(pt)
                                    }
                                }
                            }

                            val updatedStroke = createStrokeFromPoints(
                                points = activePoints.toList(),
                                tool = activeTool,
                                config = config,
                                id = currentStrokeId ?: java.util.UUID.randomUUID().toString(),
                                shape = GeometricShape.NONE
                            )
                            _uiState.update { state ->
                                state.copy(
                                    currentActiveStroke = updatedStroke,
                                    diagnostics = state.diagnostics.copy(
                                        lastToolType = pointerTool,
                                        toolTypeName = getToolTypeName(pointerTool),
                                        lastPressure = event.getPressure(activeIndex),
                                        historicalPointsCount = event.historySize,
                                        acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val palmTool = if (pointerCount > 0) event.getToolType(0) else MotionEvent.TOOL_TYPE_UNKNOWN
                    _uiState.update { state ->
                        state.copy(
                            diagnostics = state.diagnostics.copy(
                                lastToolType = palmTool,
                                toolTypeName = getToolTypeName(palmTool),
                                rejectedEventsCount = state.diagnostics.rejectedEventsCount + 1
                            )
                        )
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = event.actionIndex
                val liftingPointerId = event.getPointerId(actionIndex)
                if (liftingPointerId == activePointerId) {
                    finalizeActiveStroke(config)
                    activePointerId = null
                    activePointerIsStylus = false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finalizeActiveStroke(config)
                activePointerId = null
                activePointerIsStylus = false
            }
        }

        return true
    }

    /**
     * Generates points for the selected geometric shape mode.
     */
    private fun generateShapePoints(
        shape: GeometricShape,
        start: StrokePoint,
        end: StrokePoint,
        config: CanvasConfig
    ): List<StrokePoint> {
        return when (shape) {
            GeometricShape.LINE -> GeometrySnapper.createStraightLinePoints(
                points = listOf(start, end),
                existingStrokes = _uiState.value.strokes,
                gridSize = 36f,
                isGridEnabled = config.showGrid,
                snapThreshold = 20f
            )
            GeometricShape.VECTOR_ARROW -> GeometrySnapper.createVectorArrowPoints(
                start = start,
                end = end,
                existingStrokes = _uiState.value.strokes,
                gridSize = 36f,
                isGridEnabled = config.showGrid,
                snapThreshold = 20f
            )
            GeometricShape.RECTANGLE -> GeometrySnapper.createRectanglePoints(
                start = start,
                end = end,
                existingStrokes = _uiState.value.strokes,
                gridSize = 36f,
                isGridEnabled = config.showGrid,
                snapThreshold = 20f
            )
            GeometricShape.CIRCLE -> GeometrySnapper.createCirclePoints(
                start = start,
                end = end,
                existingStrokes = _uiState.value.strokes,
                gridSize = 36f,
                isGridEnabled = config.showGrid,
                snapThreshold = 20f
            )
            GeometricShape.NONE -> listOf(start, end)
        }
    }

    /**
     * Finalizes current in-progress stroke and preserves WYSIWYG fidelity.
     * If the user held the pen on a straight stroke or ruler mode is active, snaps into a straight line.
     */
    private fun finalizeActiveStroke(config: CanvasConfig) {
        holdStraightenJob?.cancel()
        val activeStroke = _uiState.value.currentActiveStroke
        if (activeStroke != null && activeStroke.points.isNotEmpty()) {
            val pts = activeStroke.points
            val durationMs = System.currentTimeMillis() - strokeStartTimeMs
            val zoom = _uiState.value.zoomScale

            // Accidental stray stroke / micro-tick filter:
            // Discard strokes with <= 2 points and bounding length < 16px (approx 6dp) if duration < 150ms.
            val minX = pts.minOf { it.x }
            val maxX = pts.maxOf { it.x }
            val minY = pts.minOf { it.y }
            val maxY = pts.maxOf { it.y }
            val boundingLengthScreenPx = hypot(maxX - minX, maxY - minY) * zoom

            if (config.activeShape == GeometricShape.NONE && pts.size <= 2 && boundingLengthScreenPx < 16f) {
                if (durationMs < 150L) {
                    discardActiveStroke()
                    return
                } else {
                    // Deliberate static tap (duration >= 150ms with zero movement):
                    // Render as a clean circular dot
                    val dotStroke = activeStroke.copy(
                        points = listOf(pts.first()),
                        shape = GeometricShape.NONE
                    )
                    saveUndoState()
                    _uiState.update { state ->
                        state.copy(
                            strokes = state.strokes + dotStroke,
                            currentActiveStroke = null,
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty()
                        )
                    }
                    discardActiveStroke()
                    return
                }
            }

            val finalStroke = if (config.activeShape != GeometricShape.NONE) {
                activeStroke.copy(shape = config.activeShape)
            } else if ((isStrokeStraightened || config.rulerModeEnabled) && activeStroke.points.size >= 2) {
                val straightPts = GeometrySnapper.createStraightLinePoints(
                    points = listOf(activeStroke.points.first(), activeStroke.points.last()),
                    existingStrokes = _uiState.value.strokes,
                    gridSize = 36f,
                    isGridEnabled = config.showGrid,
                    snapThreshold = 20f
                )
                activeStroke.copy(points = straightPts, shape = GeometricShape.LINE)
            } else {
                activeStroke
            }
            saveUndoState()

            _uiState.update { state ->
                state.copy(
                    strokes = state.strokes + finalStroke,
                    currentActiveStroke = null,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
        }
        discardActiveStroke()
    }

    /**
     * Schedules automatic line straightening if the stylus remains paused for >= 450ms.
     */
    private fun scheduleHoldStraighten() {
        holdStraightenJob?.cancel()
        holdStraightenJob = viewModelScope.launch {
            delay(450)
            straightenActiveStroke()
        }
    }

    /**
     * Straightens the active stroke in real-time when held still, STRICTLY IF straightness >= 0.88.
     * Curved handwriting and loops will never be straightened accidentally.
     */
    private fun straightenActiveStroke() {
        val config = _uiState.value.config
        if (config.activeTool == CanvasTool.ERASER || config.activeShape != GeometricShape.NONE) return
        val currentStroke = _uiState.value.currentActiveStroke ?: return
        if (activePoints.size >= 2) {
            val straightness = GeometrySnapper.calculateStraightness(activePoints)
            if (straightness < 0.88f) {
                // User was writing letters or drawing curves - do not straighten!
                return
            }
            isStrokeStraightened = true
            val straightPts = GeometrySnapper.createStraightLinePoints(
                points = listOf(activePoints.first(), activePoints.last()),
                existingStrokes = _uiState.value.strokes,
                gridSize = 36f,
                isGridEnabled = config.showGrid,
                snapThreshold = 20f
            )
            activePoints.clear()
            activePoints.addAll(straightPts)
            val updatedStroke = currentStroke.copy(points = straightPts)
            _uiState.update { 
                it.copy(
                    currentActiveStroke = updatedStroke,
                    hapticFeedbackTrigger = it.hapticFeedbackTrigger + 1
                ) 
            }
        }
    }

    private fun discardActiveStroke() {
        activePoints.clear()
        currentStrokeId = null
        activePointerId = null
        activePointerIsStylus = false
        _uiState.update { it.copy(currentActiveStroke = null) }
    }

    private fun eraseAtPoints(points: List<StrokePoint>, eraserRadius: Float) {
        val currentStrokes = _uiState.value.strokes
        var modified = false
        var remainingStrokes = currentStrokes

        for (p in points) {
            val erased = StrokeEraser.findStrokesIntersecting(remainingStrokes, p.x, p.y, eraserRadius)
            if (erased.isNotEmpty()) {
                val erasedIds = erased.map { it.id }.toSet()
                remainingStrokes = remainingStrokes.filterNot { it.id in erasedIds }
                modified = true
            }
        }

        if (modified) {
            _uiState.update { it.copy(strokes = remainingStrokes) }
        }
    }

    private fun createStrokeFromPoints(
        points: List<StrokePoint>,
        tool: CanvasTool,
        config: CanvasConfig,
        id: String,
        shape: GeometricShape = config.activeShape
    ): DrawingStroke {
        return DrawingStroke(
            id = id,
            points = points,
            color = when (tool) {
                CanvasTool.PEN -> config.penColor
                CanvasTool.HIGHLIGHTER -> config.highlighterColor
                CanvasTool.ERASER -> Color.Transparent
            },
            baseWidth = getBaseWidthForTool(tool, config),
            tool = tool,
            shape = if (isStrokeStraightened || config.rulerModeEnabled) {
                if (shape == GeometricShape.NONE) GeometricShape.LINE else shape
            } else {
                shape
            }
        )
    }

    private fun getBaseWidthForTool(tool: CanvasTool, config: CanvasConfig): Float {
        return when (tool) {
            CanvasTool.PEN -> config.penWidth
            CanvasTool.HIGHLIGHTER -> config.highlighterWidth
            CanvasTool.ERASER -> config.eraserRadius
        }
    }

    // --- Interactive Media / Task Image Management ---

    /**
     * Safely copies an external image Uri stream in background Dispatchers.IO,
     * computes center spawn coordinates according to current viewport, and adds it to the canvas.
     */
    fun importImageFromUri(
        context: Context,
        uri: Uri,
        screenWidth: Float,
        screenHeight: Float
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val persistentImage = TaskImageManager.importAndDownscaleImage(context, uri)
            if (persistentImage != null) {
                val aspectRatio = max(0.2f, persistentImage.width / max(1f, persistentImage.height))
                val targetWidth = 360f
                val targetHeight = targetWidth / aspectRatio

                val currentPanX = _uiState.value.panOffsetX
                val currentPanY = _uiState.value.panOffsetY
                val currentZoom = _uiState.value.zoomScale

                val spawnX = (-currentPanX + screenWidth / 2f) / currentZoom - (targetWidth / 2f)
                val spawnY = (-currentPanY + screenHeight / 2f) / currentZoom - (targetHeight / 2f)

                addImage(
                    filePath = persistentImage.filePath,
                    x = spawnX,
                    y = spawnY,
                    width = targetWidth,
                    height = targetHeight,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )
            }
        }
    }

    /**
     * Inserts an image card onto the infinite canvas scene, centered in current visible viewport by default.
     */
    fun addImage(
        filePath: String,
        x: Float? = null,
        y: Float? = null,
        width: Float = 360f,
        height: Float = 260f,
        screenWidth: Float = 1200f,
        screenHeight: Float = 800f
    ) {
        saveUndoState()

        val currentPanX = _uiState.value.panOffsetX
        val currentPanY = _uiState.value.panOffsetY
        val currentZoom = _uiState.value.zoomScale

        val sceneX = x ?: run {
            (-currentPanX + screenWidth / 2f) / currentZoom - width / 2f
        }

        val sceneY = y ?: run {
            (-currentPanY + screenHeight / 2f) / currentZoom - height / 2f
        }

        val newImage = CanvasImage(
            filePath = filePath,
            x = sceneX,
            y = sceneY,
            width = width,
            height = height,
            isSelected = true
        )

        _uiState.update { state ->
            val updatedImages = state.images.map { it.copy(isSelected = false) } + newImage
            state.copy(
                images = updatedImages,
                selectedImageId = newImage.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    /**
     * Updates an image card's scene position during dragging.
     */
    fun updateImagePosition(id: String, x: Float, y: Float) {
        _uiState.update { state ->
            val updated = state.images.map { img ->
                if (img.id == id) img.copy(x = x, y = y) else img
            }
            state.copy(images = updated)
        }
    }

    /**
     * Resizes an image card proportionally in scene coordinates.
     */
    fun resizeImage(id: String, width: Float, height: Float) {
        val clampedWidth = max(64f, width)
        val clampedHeight = max(64f, height)
        _uiState.update { state ->
            val updated = state.images.map { img ->
                if (img.id == id) img.copy(width = clampedWidth, height = clampedHeight) else img
            }
            state.copy(images = updated)
        }
    }

    /**
     * Updates an image card's full geometry (position + dimensions) during corner dragging.
     */
    fun updateImageGeometry(id: String, x: Float, y: Float, width: Float, height: Float) {
        val clampedWidth = max(64f, width)
        val clampedHeight = max(64f, height)
        _uiState.update { state ->
            val updated = state.images.map { img ->
                if (img.id == id) img.copy(x = x, y = y, width = clampedWidth, height = clampedHeight) else img
            }
            state.copy(images = updated)
        }
    }

    private fun checkAndStartImageInteraction(
        screenX: Float,
        screenY: Float,
        sceneX: Float,
        sceneY: Float,
        zoom: Float,
        panX: Float,
        panY: Float
    ): Boolean {
        val selectedId = _uiState.value.selectedImageId
        val images = _uiState.value.images
        val selectedImg = images.find { it.id == selectedId }

        if (selectedImg != null) {
            val cornerTR_X = (selectedImg.x + selectedImg.width) * zoom + panX
            val cornerTR_Y = selectedImg.y * zoom + panY
            val deleteCenterX = cornerTR_X + 24f
            val deleteCenterY = cornerTR_Y - 24f

            // 1. Check Delete button
            if (hypot(screenX - deleteCenterX, screenY - deleteCenterY) <= 36f ||
                (screenX in cornerTR_X..(cornerTR_X + 40f) && screenY in (cornerTR_Y - 40f)..cornerTR_Y)
            ) {
                removeImage(selectedImg.id)
                activeImageId = null
                activeImageHandle = ImageDragHandle.NONE
                return true
            }

            // 2. Check 4 corner resize handles
            val cornerTL_X = selectedImg.x * zoom + panX
            val cornerTL_Y = selectedImg.y * zoom + panY
            val cornerBL_X = selectedImg.x * zoom + panX
            val cornerBL_Y = (selectedImg.y + selectedImg.height) * zoom + panY
            val cornerBR_X = (selectedImg.x + selectedImg.width) * zoom + panX
            val cornerBR_Y = (selectedImg.y + selectedImg.height) * zoom + panY

            val handleHitRadius = 38f // generous touch radius for handles

            val handle = when {
                hypot(screenX - cornerTL_X, screenY - cornerTL_Y) <= handleHitRadius -> ImageDragHandle.TOP_LEFT
                hypot(screenX - cornerTR_X, screenY - cornerTR_Y) <= handleHitRadius -> ImageDragHandle.TOP_RIGHT
                hypot(screenX - cornerBL_X, screenY - cornerBL_Y) <= handleHitRadius -> ImageDragHandle.BOTTOM_LEFT
                hypot(screenX - cornerBR_X, screenY - cornerBR_Y) <= handleHitRadius -> ImageDragHandle.BOTTOM_RIGHT
                else -> ImageDragHandle.NONE
            }

            if (handle != ImageDragHandle.NONE) {
                activeImageId = selectedImg.id
                activeImageHandle = handle
                imageDragStartSceneX = sceneX
                imageDragStartSceneY = sceneY
                imageInitialX = selectedImg.x
                imageInitialY = selectedImg.y
                imageInitialWidth = selectedImg.width
                imageInitialHeight = selectedImg.height
                imageMovedOrResized = false
                return true
            }

            // 3. Check inside selected image body
            if (sceneX in selectedImg.x..(selectedImg.x + selectedImg.width) &&
                sceneY in selectedImg.y..(selectedImg.y + selectedImg.height)
            ) {
                activeImageId = selectedImg.id
                activeImageHandle = ImageDragHandle.BODY
                imageDragStartSceneX = sceneX
                imageDragStartSceneY = sceneY
                imageInitialX = selectedImg.x
                imageInitialY = selectedImg.y
                imageInitialWidth = selectedImg.width
                imageInitialHeight = selectedImg.height
                imageMovedOrResized = false
                return true
            }
        }

        // 4. Check other images (topmost first)
        for (i in images.indices.reversed()) {
            val img = images[i]
            if (img.id == selectedId) continue
            if (sceneX in img.x..(img.x + img.width) && sceneY in img.y..(img.y + img.height)) {
                selectImage(img.id)
                activeImageId = img.id
                activeImageHandle = ImageDragHandle.BODY
                imageDragStartSceneX = sceneX
                imageDragStartSceneY = sceneY
                imageInitialX = img.x
                imageInitialY = img.y
                imageInitialWidth = img.width
                imageInitialHeight = img.height
                imageMovedOrResized = false
                return true
            }
        }

        return false
    }

    private fun handleImageDrag(sceneX: Float, sceneY: Float) {
        val targetId = activeImageId ?: return
        val deltaSceneX = sceneX - imageDragStartSceneX
        val deltaSceneY = sceneY - imageDragStartSceneY
        imageMovedOrResized = true

        val aspectRatio = max(0.1f, imageInitialWidth / max(1f, imageInitialHeight))

        when (activeImageHandle) {
            ImageDragHandle.BODY -> {
                updateImagePosition(targetId, imageInitialX + deltaSceneX, imageInitialY + deltaSceneY)
            }
            ImageDragHandle.BOTTOM_RIGHT -> {
                val deltaScale = (deltaSceneX + deltaSceneY * aspectRatio) / 2f
                val newWidth = max(64f, imageInitialWidth + deltaScale)
                val newHeight = newWidth / aspectRatio
                updateImageGeometry(targetId, imageInitialX, imageInitialY, newWidth, newHeight)
            }
            ImageDragHandle.TOP_LEFT -> {
                val deltaScale = (-deltaSceneX - deltaSceneY * aspectRatio) / 2f
                val newWidth = max(64f, imageInitialWidth + deltaScale)
                val newHeight = newWidth / aspectRatio
                val newX = imageInitialX + (imageInitialWidth - newWidth)
                val newY = imageInitialY + (imageInitialHeight - newHeight)
                updateImageGeometry(targetId, newX, newY, newWidth, newHeight)
            }
            ImageDragHandle.TOP_RIGHT -> {
                val deltaScale = (deltaSceneX - deltaSceneY * aspectRatio) / 2f
                val newWidth = max(64f, imageInitialWidth + deltaScale)
                val newHeight = newWidth / aspectRatio
                val newY = imageInitialY + (imageInitialHeight - newHeight)
                updateImageGeometry(targetId, imageInitialX, newY, newWidth, newHeight)
            }
            ImageDragHandle.BOTTOM_LEFT -> {
                val deltaScale = (-deltaSceneX + deltaSceneY * aspectRatio) / 2f
                val newWidth = max(64f, imageInitialWidth + deltaScale)
                val newHeight = newWidth / aspectRatio
                val newX = imageInitialX + (imageInitialWidth - newWidth)
                updateImageGeometry(targetId, newX, imageInitialY, newWidth, newHeight)
            }
            ImageDragHandle.NONE -> {}
        }
    }

    private fun finishImageInteraction() {
        if (imageMovedOrResized) {
            saveUndoState()
        }
        activeImageId = null
        activeImageHandle = ImageDragHandle.NONE
        imageMovedOrResized = false
    }

    /**
     * Removes an image from the canvas.
     */
    fun removeImage(id: String) {
        saveUndoState()
        _uiState.update { state ->
            state.copy(
                images = state.images.filterNot { it.id == id },
                selectedImageId = if (state.selectedImageId == id) null else state.selectedImageId,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    /**
     * Selects or deselects an image card.
     */
    fun selectImage(id: String?) {
        _uiState.update { state ->
            val updated = state.images.map { img ->
                img.copy(isSelected = (img.id == id))
            }
            state.copy(images = updated, selectedImageId = id)
        }
    }

    // --- Tool Configuration ---

    fun setTool(tool: CanvasTool) {
        _uiState.update { it.copy(config = it.config.copy(activeTool = tool, activeShape = GeometricShape.NONE)) }
    }

    fun setActiveShape(shape: GeometricShape) {
        _uiState.update { it.copy(config = it.config.copy(activeShape = shape)) }
    }

    fun toggleDarkMode() {
        _uiState.update { state ->
            val newDarkMode = !state.config.isDarkMode
            val currentPenColor = state.config.penColor
            val newPenColor = if (newDarkMode) {
                if (currentPenColor == Color(0xFF111827) || currentPenColor == Color(0xFF1A1A1A)) Color(0xFFFFFFFF) else currentPenColor
            } else {
                if (currentPenColor == Color(0xFFFFFFFF)) Color(0xFF111827) else currentPenColor
            }
            state.copy(config = state.config.copy(isDarkMode = newDarkMode, penColor = newPenColor))
        }
    }

    fun setPenColor(color: Color) {
        _uiState.update { it.copy(config = it.config.copy(penColor = color)) }
    }

    fun setHighlighterColor(color: Color) {
        _uiState.update { it.copy(config = it.config.copy(highlighterColor = color)) }
    }

    fun setColor(color: Color) {
        _uiState.update {
            if (it.config.activeTool == CanvasTool.HIGHLIGHTER) {
                it.copy(config = it.config.copy(highlighterColor = color))
            } else {
                it.copy(config = it.config.copy(penColor = color))
            }
        }
    }

    fun setPenWidth(width: Float) {
        _uiState.update { it.copy(config = it.config.copy(penWidth = width)) }
    }

    fun setHighlighterWidth(width: Float) {
        _uiState.update { it.copy(config = it.config.copy(highlighterWidth = width)) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update {
            if (it.config.activeTool == CanvasTool.HIGHLIGHTER) {
                it.copy(config = it.config.copy(highlighterWidth = width))
            } else {
                it.copy(config = it.config.copy(penWidth = width))
            }
        }
    }

    fun toggleTouchMode() {
        _uiState.update {
            it.copy(config = it.config.copy(touchModeEnabled = !it.config.touchModeEnabled))
        }
    }

    fun toggleRulerMode() {
        _uiState.update {
            it.copy(config = it.config.copy(rulerModeEnabled = !it.config.rulerModeEnabled))
        }
    }

    fun toggleGrid() {
        _uiState.update {
            it.copy(config = it.config.copy(showGrid = !it.config.showGrid))
        }
    }

    fun toggleCoordinateAxes() {
        _uiState.update {
            it.copy(config = it.config.copy(showCoordinateAxes = !it.config.showCoordinateAxes))
        }
    }

    // --- Undo / Redo & Clear Surface ---

    fun saveUndoState() {
        val snapshot = CanvasSnapshot(
            strokes = _uiState.value.strokes,
            textAnnotations = _uiState.value.canvasTextAnnotations,
            images = _uiState.value.images
        )
        undoStack.add(snapshot)
        if (undoStack.size > MAX_UNDO_STACK_SIZE) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return

        val currentSnapshot = CanvasSnapshot(
            strokes = _uiState.value.strokes,
            textAnnotations = _uiState.value.canvasTextAnnotations,
            images = _uiState.value.images
        )
        redoStack.add(currentSnapshot)

        val previousSnapshot = undoStack.removeAt(undoStack.size - 1)
        _uiState.update { state ->
            state.copy(
                strokes = previousSnapshot.strokes,
                canvasTextAnnotations = previousSnapshot.textAnnotations,
                images = previousSnapshot.images,
                selectedImageId = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return

        val currentSnapshot = CanvasSnapshot(
            strokes = _uiState.value.strokes,
            textAnnotations = _uiState.value.canvasTextAnnotations,
            images = _uiState.value.images
        )
        undoStack.add(currentSnapshot)

        val nextSnapshot = redoStack.removeAt(redoStack.size - 1)
        _uiState.update { state ->
            state.copy(
                strokes = nextSnapshot.strokes,
                canvasTextAnnotations = nextSnapshot.textAnnotations,
                images = nextSnapshot.images,
                selectedImageId = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun clear() {
        saveUndoState()
        _uiState.update { state ->
            state.copy(
                strokes = emptyList(),
                canvasTextAnnotations = emptyList(),
                images = emptyList(),
                selectedImageId = null,
                currentActiveStroke = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun addStroke(stroke: DrawingStroke) {
        saveUndoState()
        _uiState.update { state ->
            state.copy(
                strokes = state.strokes + stroke,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        holdStraightenJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        activePoints.clear()
    }

    private fun getToolTypeName(toolType: Int): String {
        return when (toolType) {
            MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
            MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
            MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
            MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
            MotionEvent.TOOL_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "OTHER($toolType)"
        }
    }

    companion object {
        const val MIN_ZOOM = 0.2f
        const val MAX_ZOOM = 5.0f
        private const val MAX_UNDO_STACK_SIZE = 50
    }
}
