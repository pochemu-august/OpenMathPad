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
import com.augustuz.openmathpad.mathengine.MathEngine
import com.augustuz.openmathpad.mathengine.model.AngleMode
import com.augustuz.openmathpad.mathengine.model.EvaluationContext
import com.augustuz.openmathpad.mathengine.model.MathResult
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
 * Historical snapshot of drawing surface, media images, and annotations for undo/redo.
 */
data class CanvasSnapshot(
    val strokes: List<DrawingStroke>,
    val textAnnotations: List<CanvasTextAnnotation>,
    val images: List<CanvasImage> = emptyList()
)

/**
 * UI State for Infinite Canvas, Stroke Beautification, Image insertion, and MathEngine scratchpad.
 */
data class CanvasUiState(
    val strokes: List<DrawingStroke> = emptyList(),
    val canvasTextAnnotations: List<CanvasTextAnnotation> = emptyList(),
    val images: List<CanvasImage> = emptyList(),
    val currentActiveStroke: DrawingStroke? = null,
    val config: CanvasConfig = CanvasConfig(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val diagnostics: InputDiagnostics = InputDiagnostics(),

    // Infinite Canvas Navigation & Transformation State
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val zoomScale: Float = 1.0f,

    // Math Engine / STEM Formula Scratchpad State
    val formulaText: String = "",
    val currentResult: MathResult? = null,
    val calculationHistory: List<MathResult> = emptyList(),
    val evaluationContext: EvaluationContext = EvaluationContext(),
    val isOverlayVisible: Boolean = false,
    val isOverlayExpanded: Boolean = false
)

/**
 * ViewModel managing infinite canvas coordinate transformations, stylus vs touch gesture separation,
 * stroke beautification pipeline, media insertion, and MathEngine integration.
 */
class CanvasViewModel @JvmOverloads constructor(
    private val mathEngine: MathEngine = MathEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<CanvasSnapshot>()
    private val redoStack = mutableListOf<CanvasSnapshot>()

    private val pressureSmoother = PressureSmoother()
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
     * - Stylus: draws strokes mapped to canvas scene coordinates `(screen - pan) / zoom`.
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

        // 1. Check if any pointer is a hardware stylus
        var stylusPointerIndex = -1
        var fingerCount = 0
        for (i in 0 until pointerCount) {
            val tool = event.getToolType(i)
            if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                stylusPointerIndex = i
                break
            } else if (tool == MotionEvent.TOOL_TYPE_FINGER) {
                fingerCount++
            }
        }
        val hasStylus = stylusPointerIndex != -1

        // 2. Two-Finger Navigation Handling (Pan & Pinch-to-Zoom)
        if (!hasStylus && pointerCount >= 2) {
            // Discard any accidental stroke that started before the second finger touched
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

                        // Focal zoom around two-finger midpoint:
                        // sceneFocus = (midX - panX) / currentZoom
                        // newPanX = midX - sceneFocus * newZoom + deltaMidX
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

        // If gesture navigating ended
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

        // If previously tracking a finger but a stylus arrives, prioritize the stylus
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
                        zoomScale = zoom
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
                    // 100% Palm Rejection: Single finger ignored by default
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
                        zoomScale = zoom
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
     * Finalizes current in-progress stroke and runs the Stroke Beautification pipeline:
     * - Moving average jitter reduction
     * - RDP curve simplification
     * - Dynamic velocity/pressure thickness interpolation & end tapering
     */
    private fun finalizeActiveStroke(config: CanvasConfig) {
        val activeStroke = _uiState.value.currentActiveStroke
        if (activeStroke != null && activeStroke.points.isNotEmpty()) {
            saveUndoState()

            // Run Stroke Beautification pipeline on finished stroke
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

    // --- Image / Media Insertion Engine ---

    /**
     * Inserts an image / STEM problem screenshot onto the canvas scene.
     * Places the image in the center of the current infinite canvas viewport if position is null.
     */
    fun addImage(
        uri: String,
        x: Float? = null,
        y: Float? = null,
        width: Float = 360f,
        height: Float = 260f
    ) {
        saveUndoState()

        val sceneX = x ?: run {
            // Place at default viewport offset
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
            uri = uri,
            x = sceneX,
            y = sceneY,
            width = width,
            height = height
        )

        _uiState.update { state ->
            state.copy(
                images = state.images + newImage,
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
     * Resizes an image card in scene coordinates.
     */
    fun resizeImage(id: String, width: Float, height: Float) {
        val clampedWidth = max(100f, width)
        val clampedHeight = max(80f, height)
        _uiState.update { state ->
            val updated = state.images.map { img ->
                if (img.id == id) img.copy(width = clampedWidth, height = clampedHeight) else img
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
            state.copy(images = updated)
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
                currentActiveStroke = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    // Helper for tests
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

    // --- MathEngine STEM Scratchpad Integration ---

    fun setFormulaText(text: String) {
        _uiState.update { it.copy(formulaText = text) }
    }

    fun setOverlayVisible(visible: Boolean) {
        _uiState.update { it.copy(isOverlayVisible = visible) }
    }

    fun toggleOverlayExpanded() {
        _uiState.update { it.copy(isOverlayExpanded = !it.isOverlayExpanded) }
    }

    fun evaluateFormula(expression: String? = null) {
        val expr = expression ?: _uiState.value.formulaText
        if (expr.isBlank()) return

        val context = _uiState.value.evaluationContext
        val result = mathEngine.evaluate(expr, context)

        _uiState.update { state ->
            state.copy(
                formulaText = expr,
                currentResult = result,
                calculationHistory = listOf(result) + state.calculationHistory,
                isOverlayVisible = true
            )
        }
    }

    fun setAngleMode(angleMode: AngleMode) {
        val currentContext = _uiState.value.evaluationContext
        val newContext = currentContext.copy(config = currentContext.config.copy(angleMode = angleMode))
        _uiState.update { it.copy(evaluationContext = newContext) }
        if (_uiState.value.formulaText.isNotBlank()) {
            evaluateFormula()
        }
    }

    fun setVariable(name: String, value: Double) {
        val newContext = _uiState.value.evaluationContext.withVariable(name, value)
        _uiState.update { it.copy(evaluationContext = newContext) }
    }

    fun removeVariable(name: String) {
        val currentContext = _uiState.value.evaluationContext
        val newContext = currentContext.copy(variables = currentContext.variables - name)
        _uiState.update { it.copy(evaluationContext = newContext) }
    }

    fun clearCalculationHistory() {
        _uiState.update { it.copy(calculationHistory = emptyList()) }
    }

    fun insertResultToCanvas(customText: String? = null) {
        val result = _uiState.value.currentResult
        val textToInsert = customText ?: when (result) {
            is MathResult.Success -> "= ${result.formattedResult}"
            else -> return
        }

        saveUndoState()
        val annotation = CanvasTextAnnotation(
            text = textToInsert,
            x = 100f,
            y = 100f
        )

        _uiState.update { state ->
            state.copy(
                canvasTextAnnotations = state.canvasTextAnnotations + annotation,
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
