package com.augustuz.openmathpad.canvas.state

import android.view.MotionEvent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.augustuz.openmathpad.canvas.model.CanvasConfig
import com.augustuz.openmathpad.canvas.model.CanvasImage
import com.augustuz.openmathpad.canvas.model.CanvasTextAnnotation
import com.augustuz.openmathpad.canvas.model.CanvasTool
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.canvas.processing.BezierSmoother
import com.augustuz.openmathpad.canvas.processing.PalmRejectionFilter
import com.augustuz.openmathpad.canvas.processing.PressureSmoother
import com.augustuz.openmathpad.canvas.processing.StrokeBeautifier
import com.augustuz.openmathpad.canvas.processing.StrokeEraser
import com.augustuz.openmathpad.canvas.processing.StrokeStabilizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    // Infinite Canvas Navigation & Transformation State
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val zoomScale: Float = 1.0f
)

/**
 * ViewModel managing infinite canvas coordinate transformations, stylus vs touch separation,
 * real-time stroke stabilization, calligraphic beautification, and image card interactions.
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

    // Two-Finger Gesture Navigation Tracking
    private var isGestureNavigating: Boolean = false
    private var lastGestureMidX: Float = 0f
    private var lastGestureMidY: Float = 0f
    private var lastGestureDistance: Float = 0f

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

        // Deselect any selected image if drawing begins on canvas
        if (event.actionMasked == MotionEvent.ACTION_DOWN && _uiState.value.selectedImageId != null) {
            selectImage(null)
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
                        strokeStabilizer = strokeStabilizer
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
                        activePoints.addAll(extracted)
                        val newStroke = createStrokeFromPoints(
                            points = activePoints.toList(),
                            tool = activeTool,
                            config = config,
                            id = strokeId
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
                        strokeStabilizer = strokeStabilizer
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
                        activePoints.addAll(extracted)
                        val updatedStroke = createStrokeFromPoints(
                            points = activePoints.toList(),
                            tool = activeTool,
                            config = config,
                            id = currentStrokeId ?: java.util.UUID.randomUUID().toString()
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
     * Finalizes current in-progress stroke and applies the full Stroke Beautification pipeline:
     * - Moving average jitter reduction
     * - RDP curve simplification
     * - Chaikin corner-cutting curve smoothing
     * - Calligraphic pressure and velocity tapering
     */
    private fun finalizeActiveStroke(config: CanvasConfig) {
        val activeStroke = _uiState.value.currentActiveStroke
        if (activeStroke != null && activeStroke.points.isNotEmpty()) {
            saveUndoState()

            val beautified = StrokeBeautifier.beautifyStroke(activeStroke)

            _uiState.update { state ->
                state.copy(
                    strokes = state.strokes + beautified,
                    currentActiveStroke = null,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
        } else {
            _uiState.update { it.copy(currentActiveStroke = null) }
        }
        activePoints.clear()
        currentStrokeId = null
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
        id: String
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
            tool = tool
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
     * Inserts an image card onto the infinite canvas scene.
     */
    fun addImage(
        filePath: String,
        x: Float? = null,
        y: Float? = null,
        width: Float = 360f,
        height: Float = 260f
    ) {
        saveUndoState()

        val sceneX = x ?: run {
            val currentPanX = _uiState.value.panOffsetX
            val currentZoom = _uiState.value.zoomScale
            (400f - currentPanX) / currentZoom - width / 2f
        }

        val sceneY = y ?: run {
            val currentPanY = _uiState.value.panOffsetY
            val currentZoom = _uiState.value.zoomScale
            (400f - currentPanY) / currentZoom - height / 2f
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
        val clampedWidth = max(80f, width)
        val clampedHeight = max(60f, height)
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
        val clampedWidth = max(80f, width)
        val clampedHeight = max(60f, height)
        _uiState.update { state ->
            val updated = state.images.map { img ->
                if (img.id == id) img.copy(x = x, y = y, width = clampedWidth, height = clampedHeight) else img
            }
            state.copy(images = updated)
        }
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
        _uiState.update { it.copy(config = it.config.copy(activeTool = tool)) }
    }

    fun setPenColor(color: Color) {
        _uiState.update { it.copy(config = it.config.copy(penColor = color)) }
    }

    fun setPenWidth(width: Float) {
        _uiState.update { it.copy(config = it.config.copy(penWidth = width)) }
    }

    fun toggleTouchMode() {
        _uiState.update {
            it.copy(config = it.config.copy(touchModeEnabled = !it.config.touchModeEnabled))
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
