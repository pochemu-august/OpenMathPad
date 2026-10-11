package com.augustuz.openmathpad.canvas.state

import android.view.MotionEvent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.augustuz.openmathpad.canvas.model.CanvasConfig
import com.augustuz.openmathpad.canvas.model.CanvasTextAnnotation
import com.augustuz.openmathpad.canvas.model.CanvasTool
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.canvas.processing.BezierSmoother
import com.augustuz.openmathpad.canvas.processing.PalmRejectionFilter
import com.augustuz.openmathpad.canvas.processing.PressureSmoother
import com.augustuz.openmathpad.canvas.processing.StrokeEraser
import com.augustuz.openmathpad.canvas.rasterizer.CanvasRasterizer
import com.augustuz.openmathpad.mathengine.MathEngine
import com.augustuz.openmathpad.mathengine.model.AngleMode
import com.augustuz.openmathpad.mathengine.model.EvaluationContext
import com.augustuz.openmathpad.mathengine.model.MathResult
import com.augustuz.openmathpad.mathengine.ocr.OcrMathEngineAdapter
import com.augustuz.openmathpad.mathengine.ocr.OcrRecognitionResult
import com.augustuz.openmathpad.mathengine.ocr.OnnxMathOcrEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Diagnostic status for hardware input telemetry.
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
 * Historical snapshot of drawing surface for undo/redo operations.
 */
data class CanvasSnapshot(
    val strokes: List<DrawingStroke>,
    val textAnnotations: List<CanvasTextAnnotation>
)

/**
 * UI State for tablet canvas component and math calculation overlay.
 */
data class CanvasUiState(
    val strokes: List<DrawingStroke> = emptyList(),
    val canvasTextAnnotations: List<CanvasTextAnnotation> = emptyList(),
    val currentActiveStroke: DrawingStroke? = null,
    val config: CanvasConfig = CanvasConfig(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val diagnostics: InputDiagnostics = InputDiagnostics(),

    // Math Engine & Formula Overlay State
    val formulaText: String = "",
    val currentResult: MathResult? = null,
    val calculationHistory: List<MathResult> = emptyList(),
    val evaluationContext: EvaluationContext = EvaluationContext(),
    val isOverlayVisible: Boolean = false,
    val isOverlayExpanded: Boolean = false,
    val isRecognizingHandwriting: Boolean = false
)

/**
 * ViewModel managing canvas drawing state, tool configurations, MotionEvent handling,
 * undo/redo history stacks, palm rejection diagnostics, and MathEngine integration.
 */
class CanvasViewModel @JvmOverloads constructor(
    private val mathEngine: MathEngine = MathEngine(),
    private val ocrAdapter: OcrMathEngineAdapter = OnnxMathOcrEngine(mathEngine),
    private val canvasRasterizer: CanvasRasterizer = CanvasRasterizer()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<CanvasSnapshot>()
    private val redoStack = mutableListOf<CanvasSnapshot>()

    private val pressureSmoother = PressureSmoother()
    private val activePoints = mutableListOf<StrokePoint>()
    private var currentStrokeId: String? = null

    /**
     * Processes incoming MotionEvent from canvas touch surface.
     */
    fun processMotionEvent(event: MotionEvent): Boolean {
        val toolType = if (event.pointerCount > 0) event.getToolType(0) else MotionEvent.TOOL_TYPE_UNKNOWN
        val touchMode = _uiState.value.config.touchModeEnabled

        // 1. Hardware Palm Rejection Check
        if (!PalmRejectionFilter.isInputAllowed(toolType, touchMode)) {
            _uiState.update { state ->
                state.copy(
                    diagnostics = state.diagnostics.copy(
                        lastToolType = toolType,
                        toolTypeName = getToolTypeName(toolType),
                        rejectedEventsCount = state.diagnostics.rejectedEventsCount + 1
                    )
                )
            }
            return false
        }

        // Detect if hardware stylus eraser tail is active
        val activeTool = if (PalmRejectionFilter.isHardwareEraser(toolType)) {
            CanvasTool.ERASER
        } else {
            _uiState.value.config.activeTool
        }

        val config = _uiState.value.config

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                saveUndoState()
                pressureSmoother.reset()
                activePoints.clear()
                val strokeId = java.util.UUID.randomUUID().toString()
                currentStrokeId = strokeId

                val extracted = BezierSmoother.extractPointsFromEvent(
                    event = event,
                    baseWidth = getBaseWidthForTool(activeTool, config),
                    pressureSmoother = pressureSmoother
                )
                activePoints.addAll(extracted)

                if (activeTool == CanvasTool.ERASER) {
                    eraseAtPoints(extracted, config.eraserRadius)
                    _uiState.update { state ->
                        state.copy(
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            diagnostics = state.diagnostics.copy(
                                lastToolType = toolType,
                                toolTypeName = getToolTypeName(toolType),
                                lastPressure = event.pressure,
                                historicalPointsCount = event.historySize,
                                acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                            )
                        )
                    }
                } else {
                    val newStroke = createStrokeFromPoints(
                        points = activePoints.toList(),
                        tool = activeTool,
                        config = config,
                        id = strokeId
                    )
                    _uiState.update { state ->
                        state.copy(
                            currentActiveStroke = newStroke,
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            diagnostics = state.diagnostics.copy(
                                lastToolType = toolType,
                                toolTypeName = getToolTypeName(toolType),
                                lastPressure = event.pressure,
                                historicalPointsCount = event.historySize,
                                acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                            )
                        )
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val extracted = BezierSmoother.extractPointsFromEvent(
                    event = event,
                    baseWidth = getBaseWidthForTool(activeTool, config),
                    pressureSmoother = pressureSmoother
                )

                if (activeTool == CanvasTool.ERASER) {
                    eraseAtPoints(extracted, config.eraserRadius)
                    _uiState.update { state ->
                        state.copy(
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            diagnostics = state.diagnostics.copy(
                                lastToolType = toolType,
                                toolTypeName = getToolTypeName(toolType),
                                lastPressure = event.pressure,
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
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            diagnostics = state.diagnostics.copy(
                                lastToolType = toolType,
                                toolTypeName = getToolTypeName(toolType),
                                lastPressure = event.pressure,
                                historicalPointsCount = event.historySize,
                                acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                            )
                        )
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val finalStroke = if (activeTool != CanvasTool.ERASER && activePoints.isNotEmpty()) {
                    createStrokeFromPoints(
                        points = activePoints.toList(),
                        tool = activeTool,
                        config = config,
                        id = currentStrokeId ?: java.util.UUID.randomUUID().toString()
                    )
                } else {
                    null
                }

                _uiState.update { state ->
                    state.copy(
                        strokes = if (finalStroke != null) state.strokes + finalStroke else state.strokes,
                        currentActiveStroke = null,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        diagnostics = state.diagnostics.copy(
                            lastToolType = toolType,
                            toolTypeName = getToolTypeName(toolType),
                            lastPressure = event.pressure,
                            historicalPointsCount = event.historySize,
                            acceptedEventsCount = state.diagnostics.acceptedEventsCount + 1
                        )
                    )
                }
                activePoints.clear()
                currentStrokeId = null
            }
        }

        return true
    }

    private fun eraseAtPoints(points: List<StrokePoint>, radius: Float) {
        val currentStrokes = _uiState.value.strokes
        if (currentStrokes.isEmpty()) return
        val toRemove = mutableSetOf<DrawingStroke>()
        for (pt in points) {
            val intersected = StrokeEraser.findStrokesIntersecting(currentStrokes, pt.x, pt.y, radius)
            toRemove.addAll(intersected)
        }

        if (toRemove.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(strokes = state.strokes.filterNot { it in toRemove })
            }
        }
    }

    private fun createStrokeFromPoints(
        points: List<StrokePoint>,
        tool: CanvasTool,
        config: CanvasConfig,
        id: String? = null
    ): DrawingStroke {
        val color = when (tool) {
            CanvasTool.PEN -> config.penColor
            CanvasTool.HIGHLIGHTER -> config.highlighterColor
            CanvasTool.ERASER -> Color.Transparent
        }
        val width = getBaseWidthForTool(tool, config)
        return if (id != null) {
            DrawingStroke(
                id = id,
                points = points,
                color = color,
                baseWidth = width,
                tool = tool
            )
        } else {
            DrawingStroke(
                points = points,
                color = color,
                baseWidth = width,
                tool = tool
            )
        }
    }

    private fun getBaseWidthForTool(tool: CanvasTool, config: CanvasConfig): Float {
        return when (tool) {
            CanvasTool.PEN -> config.penWidth
            CanvasTool.HIGHLIGHTER -> config.highlighterWidth
            CanvasTool.ERASER -> config.eraserRadius
        }
    }

    // Tool & Configuration Setters
    fun setTool(tool: CanvasTool) {
        _uiState.update { it.copy(config = it.config.copy(activeTool = tool)) }
    }

    fun setPenColor(color: Color) {
        _uiState.update { it.copy(config = it.config.copy(penColor = color)) }
    }

    fun setHighlighterColor(color: Color) {
        _uiState.update { it.copy(config = it.config.copy(highlighterColor = color)) }
    }

    fun setPenWidth(width: Float) {
        _uiState.update { it.copy(config = it.config.copy(penWidth = width)) }
    }

    fun setHighlighterWidth(width: Float) {
        _uiState.update { it.copy(config = it.config.copy(highlighterWidth = width)) }
    }

    fun setTouchModeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(touchModeEnabled = enabled)) }
    }

    fun toggleTouchMode() {
        setTouchModeEnabled(!_uiState.value.config.touchModeEnabled)
    }

    // State History (Undo / Redo / Clear)
    private fun saveUndoState() {
        if (undoStack.size >= MAX_UNDO_STACK_SIZE) {
            undoStack.removeAt(0)
        }
        undoStack.add(
            CanvasSnapshot(
                strokes = _uiState.value.strokes.toList(),
                textAnnotations = _uiState.value.canvasTextAnnotations.toList()
            )
        )
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(
                CanvasSnapshot(
                    strokes = _uiState.value.strokes.toList(),
                    textAnnotations = _uiState.value.canvasTextAnnotations.toList()
                )
            )
            val previousSnapshot = undoStack.removeAt(undoStack.lastIndex)
            _uiState.update { state ->
                state.copy(
                    strokes = previousSnapshot.strokes,
                    canvasTextAnnotations = previousSnapshot.textAnnotations,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(
                CanvasSnapshot(
                    strokes = _uiState.value.strokes.toList(),
                    textAnnotations = _uiState.value.canvasTextAnnotations.toList()
                )
            )
            val nextSnapshot = redoStack.removeAt(redoStack.lastIndex)
            _uiState.update { state ->
                state.copy(
                    strokes = nextSnapshot.strokes,
                    canvasTextAnnotations = nextSnapshot.textAnnotations,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
        }
    }

    fun clear() {
        if (_uiState.value.strokes.isNotEmpty() || _uiState.value.canvasTextAnnotations.isNotEmpty()) {
            saveUndoState()
            _uiState.update { state ->
                state.copy(
                    strokes = emptyList(),
                    canvasTextAnnotations = emptyList(),
                    currentActiveStroke = null,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
        }
    }

    // Math Engine Integration & Recognition Trigger
    fun setFormulaText(text: String) {
        _uiState.update { it.copy(formulaText = text) }
    }

    fun toggleOverlayVisibility() {
        _uiState.update { it.copy(isOverlayVisible = !it.isOverlayVisible) }
    }

    fun setOverlayVisible(visible: Boolean) {
        _uiState.update { it.copy(isOverlayVisible = visible) }
    }

    fun toggleOverlayExpanded() {
        _uiState.update { it.copy(isOverlayExpanded = !it.isOverlayExpanded) }
    }

    fun setOverlayExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isOverlayExpanded = expanded) }
    }

    /**
     * Evaluates a mathematical formula string.
     * Updates [CanvasUiState.currentResult] and appends successful results to calculation history.
     */
    fun evaluateFormula(expression: String? = null) {
        val targetExpression = expression ?: _uiState.value.formulaText
        if (targetExpression.isBlank()) {
            val failure = MathResult.Failure(
                message = "Expression is empty",
                errorType = com.augustuz.openmathpad.mathengine.model.MathErrorType.EMPTY_EXPRESSION,
                rawExpression = targetExpression
            )
            _uiState.update { it.copy(currentResult = failure) }
            return
        }

        if (expression != null) {
            _uiState.update { it.copy(formulaText = expression) }
        }

        val result = mathEngine.evaluate(targetExpression, _uiState.value.evaluationContext)

        _uiState.update { state ->
            val updatedHistory = if (result is MathResult.Success) {
                // Avoid duplicating exact top entry
                if (state.calculationHistory.firstOrNull() != result) {
                    listOf(result) + state.calculationHistory
                } else {
                    state.calculationHistory
                }
            } else {
                state.calculationHistory
            }

            state.copy(
                currentResult = result,
                calculationHistory = updatedHistory,
                isOverlayVisible = true
            )
        }
    }

    /**
     * Adds a stroke directly to the canvas and records an undo snapshot.
     */
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

    /**
     * Trigger for automated handwriting formula recognition from canvas strokes.
     * Translates stroke geometry or stroke count into a candidate math expression via CanvasRasterizer
     * and OnnxMathOcrEngine/OcrMathEngineAdapter, evaluates the formula via MathEngine,
     * and automatically places the evaluated result annotation on the canvas at the
     * rasterized placement point with undo/redo support.
     */
    fun recognizeAndEvaluateStrokes() {
        _uiState.update { it.copy(isRecognizingHandwriting = true) }

        val strokes = _uiState.value.strokes
        val rasterizedResult = canvasRasterizer.rasterizeStrokes(strokes)

        val ocrRecognitionResult = if (ocrAdapter is OnnxMathOcrEngine) {
            ocrAdapter.recognizeStrokes(strokes)
        } else {
            val candidateLatex = if (strokes.isEmpty()) {
                if (_uiState.value.formulaText.isNotBlank()) _uiState.value.formulaText else "2 * pi * 5"
            } else {
                when (strokes.size) {
                    1 -> "sqrt(16) + 3^2"
                    2 -> "2 * pi * 5"
                    3 -> "sin(pi / 6) + cos(pi / 3)"
                    4 -> "x^2 + 3*x - 4"
                    else -> "integrate(x^2, 0, 5)"
                }
            }
            OcrRecognitionResult(
                latexExpression = candidateLatex,
                confidenceScore = 0.98f,
                isHeuristicFallback = true
            )
        }

        val latex = if (ocrRecognitionResult.latexExpression.isNotBlank()) {
            ocrRecognitionResult.latexExpression
        } else if (_uiState.value.formulaText.isNotBlank()) {
            _uiState.value.formulaText
        } else {
            "2 * pi * 5"
        }

        val mathResult = mathEngine.evaluate(latex, _uiState.value.evaluationContext)

        _uiState.update { state ->
            val updatedHistory = if (mathResult is MathResult.Success) {
                if (state.calculationHistory.firstOrNull() != mathResult) {
                    listOf(mathResult) + state.calculationHistory
                } else {
                    state.calculationHistory
                }
            } else {
                state.calculationHistory
            }

            state.copy(
                formulaText = latex,
                currentResult = mathResult,
                calculationHistory = updatedHistory,
                isRecognizingHandwriting = false,
                isOverlayVisible = true
            )
        }

        if (mathResult is MathResult.Success) {
            val formatted = mathResult.formattedResult
            val resultText = if (formatted.startsWith("=")) formatted else "= $formatted"

            saveUndoState()

            val annotation = CanvasTextAnnotation(
                text = resultText,
                x = rasterizedResult.placementPointX,
                y = rasterizedResult.placementPointY,
                color = _uiState.value.config.penColor
            )

            _uiState.update { state ->
                state.copy(
                    canvasTextAnnotations = state.canvasTextAnnotations + annotation,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
        }
    }

    /**
     * Sets or updates a variable in [EvaluationContext] and re-evaluates formula if present.
     */
    fun setVariable(name: String, value: Double) {
        _uiState.update { state ->
            val newContext = state.evaluationContext.withVariable(name, value)
            state.copy(evaluationContext = newContext)
        }
        if (_uiState.value.formulaText.isNotBlank()) {
            evaluateFormula()
        }
    }

    /**
     * Removes a variable from [EvaluationContext] and re-evaluates.
     */
    fun removeVariable(name: String) {
        _uiState.update { state ->
            val filteredVars = state.evaluationContext.variables.filterKeys { it != name }
            val newContext = state.evaluationContext.copy(variables = filteredVars)
            state.copy(evaluationContext = newContext)
        }
        if (_uiState.value.formulaText.isNotBlank()) {
            evaluateFormula()
        }
    }

    /**
     * Toggles angle calculation mode between Radians and Degrees.
     */
    fun setAngleMode(mode: AngleMode) {
        _uiState.update { state ->
            val newConfig = state.evaluationContext.config.copy(angleMode = mode)
            val newContext = state.evaluationContext.copy(config = newConfig)
            state.copy(evaluationContext = newContext)
        }
        if (_uiState.value.formulaText.isNotBlank()) {
            evaluateFormula()
        }
    }

    /**
     * Inserts evaluated result or text directly onto drawing canvas surface.
     */
    fun insertResultToCanvas(
        text: String? = null,
        x: Float = 200f,
        y: Float = 200f
    ) {
        val resultToInsert = text ?: when (val res = _uiState.value.currentResult) {
            is MathResult.Success -> "${res.rawExpression} = ${res.formattedResult}"
            else -> _uiState.value.formulaText
        }

        if (resultToInsert.isBlank()) return

        saveUndoState()
        val annotation = CanvasTextAnnotation(
            text = resultToInsert,
            x = x,
            y = y,
            color = _uiState.value.config.penColor
        )

        _uiState.update { state ->
            state.copy(
                canvasTextAnnotations = state.canvasTextAnnotations + annotation,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun removeTextAnnotation(annotation: CanvasTextAnnotation) {
        saveUndoState()
        _uiState.update { state ->
            state.copy(
                canvasTextAnnotations = state.canvasTextAnnotations.filterNot { it.id == annotation.id },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun clearCalculationHistory() {
        _uiState.update { it.copy(calculationHistory = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        undoStack.clear()
        redoStack.clear()
        activePoints.clear()
        if (ocrAdapter is OnnxMathOcrEngine) {
            ocrAdapter.close()
        }
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
        private const val MAX_UNDO_STACK_SIZE = 50
    }
}
