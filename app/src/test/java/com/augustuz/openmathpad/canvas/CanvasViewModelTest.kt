package com.augustuz.openmathpad.canvas

import androidx.compose.ui.graphics.Color
import com.augustuz.openmathpad.canvas.model.CanvasTool
import com.augustuz.openmathpad.canvas.state.CanvasViewModel
import com.augustuz.openmathpad.mathengine.model.AngleMode
import com.augustuz.openmathpad.mathengine.model.MathErrorType
import com.augustuz.openmathpad.mathengine.model.MathResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CanvasViewModelTest {

    private lateinit var viewModel: CanvasViewModel

    @Before
    fun setUp() {
        viewModel = CanvasViewModel()
    }

    @Test
    fun initialConfig_isCorrectDefaults() {
        val uiState = viewModel.uiState.value
        assertEquals(CanvasTool.PEN, uiState.config.activeTool)
        assertFalse("Touch mode must be disabled by default for palm rejection", uiState.config.touchModeEnabled)
        assertTrue("Initial stroke list must be empty", uiState.strokes.isEmpty())
        assertTrue("Initial text annotation list must be empty", uiState.canvasTextAnnotations.isEmpty())
        assertFalse("Undo should be disabled initially", uiState.canUndo)
        assertFalse("Redo should be disabled initially", uiState.canRedo)
        assertFalse("Overlay should be hidden initially", uiState.isOverlayVisible)
    }

    @Test
    fun setTool_updatesActiveTool() {
        viewModel.setTool(CanvasTool.HIGHLIGHTER)
        assertEquals(CanvasTool.HIGHLIGHTER, viewModel.uiState.value.config.activeTool)

        viewModel.setTool(CanvasTool.ERASER)
        assertEquals(CanvasTool.ERASER, viewModel.uiState.value.config.activeTool)
    }

    @Test
    fun setPenColor_updatesColorConfig() {
        val red = Color.Red
        viewModel.setPenColor(red)
        assertEquals(red, viewModel.uiState.value.config.penColor)
    }

    @Test
    fun setPenWidth_updatesWidthConfig() {
        viewModel.setPenWidth(12.0f)
        assertEquals(12.0f, viewModel.uiState.value.config.penWidth, 0.01f)
    }

    @Test
    fun toggleTouchMode_switchesState() {
        assertFalse(viewModel.uiState.value.config.touchModeEnabled)

        viewModel.toggleTouchMode()
        assertTrue(viewModel.uiState.value.config.touchModeEnabled)

        viewModel.toggleTouchMode()
        assertFalse(viewModel.uiState.value.config.touchModeEnabled)
    }

    @Test
    fun evaluateFormula_calculatesValidExpressionAndUpdatesHistory() {
        viewModel.setFormulaText("2 * pi * 5")
        viewModel.evaluateFormula()

        val uiState = viewModel.uiState.value
        assertTrue("Overlay should be visible after evaluation", uiState.isOverlayVisible)
        assertNotNull("Current result should be present", uiState.currentResult)

        val result = uiState.currentResult
        assertTrue("Result should be Success", result is MathResult.Success)

        val success = result as MathResult.Success
        assertEquals(31.41592653589793, success.value, 1e-5)
        assertEquals(1, uiState.calculationHistory.size)
        assertEquals(success, uiState.calculationHistory.first())
    }

    @Test
    fun evaluateFormula_handlesInvalidExpression() {
        viewModel.evaluateFormula("2 / 0")

        val uiState = viewModel.uiState.value
        assertTrue("Result should be Failure", uiState.currentResult is MathResult.Failure)

        val failure = uiState.currentResult as MathResult.Failure
        assertEquals(MathErrorType.DIVISION_BY_ZERO, failure.errorType)
    }

    @Test
    fun recognizeAndEvaluateStrokes_evaluatesExpressionAndUpdatesState() {
        viewModel.recognizeAndEvaluateStrokes()

        val uiState = viewModel.uiState.value
        assertTrue("Overlay should be visible after recognition", uiState.isOverlayVisible)
        assertTrue("Formula text should not be blank", uiState.formulaText.isNotBlank())
        assertTrue("Current result should be Success", uiState.currentResult is MathResult.Success)
    }

    @Test
    fun variables_canBeSetRemovedAndEvaluated() {
        viewModel.setVariable("x", 10.0)
        viewModel.setVariable("y", 2.5)

        val context = viewModel.uiState.value.evaluationContext
        assertEquals(10.0, context.variables["x"]!!, 0.01)
        assertEquals(2.5, context.variables["y"]!!, 0.01)

        viewModel.evaluateFormula("3 * x + y")
        val result = viewModel.uiState.value.currentResult as MathResult.Success
        assertEquals(32.5, result.value, 1e-5)

        viewModel.removeVariable("x")
        val updatedContext = viewModel.uiState.value.evaluationContext
        assertFalse(updatedContext.variables.containsKey("x"))
        assertTrue(updatedContext.variables.containsKey("y"))
    }

    @Test
    fun angleMode_canBeToggledAndRecalculatesTrig() {
        viewModel.setAngleMode(AngleMode.DEGREES)
        viewModel.evaluateFormula("sin(90)")

        val degResult = viewModel.uiState.value.currentResult as MathResult.Success
        assertEquals(1.0, degResult.value, 1e-5)

        viewModel.setAngleMode(AngleMode.RADIANS)
        val radContextMode = viewModel.uiState.value.evaluationContext.config.angleMode
        assertEquals(AngleMode.RADIANS, radContextMode)
    }

    @Test
    fun insertResultToCanvas_addsTextAnnotationAndCanUndoRedo() {
        viewModel.evaluateFormula("12 * 12")
        viewModel.insertResultToCanvas()

        val uiState = viewModel.uiState.value
        assertEquals(1, uiState.canvasTextAnnotations.size)
        assertTrue("Undo should be available after insertion", uiState.canUndo)

        viewModel.undo()
        assertTrue("Text annotation should be undone", viewModel.uiState.value.canvasTextAnnotations.isEmpty())

        viewModel.redo()
        assertEquals("Text annotation should be redone", 1, viewModel.uiState.value.canvasTextAnnotations.size)
    }

    @Test
    fun clear_clearsCanvasAnnotationsAndStrokesWithUndo() {
        viewModel.insertResultToCanvas("2 + 2 = 4")
        assertEquals(1, viewModel.uiState.value.canvasTextAnnotations.size)

        viewModel.clear()
        assertTrue("Canvas annotations should be cleared", viewModel.uiState.value.canvasTextAnnotations.isEmpty())
        assertTrue("Undo should be available after clear", viewModel.uiState.value.canUndo)

        viewModel.undo()
        assertEquals("Cleared content restored via undo", 1, viewModel.uiState.value.canvasTextAnnotations.size)
    }

    @Test
    fun clearCalculationHistory_clearsHistory() {
        viewModel.evaluateFormula("2 + 2")
        viewModel.evaluateFormula("5 * 5")
        assertEquals(2, viewModel.uiState.value.calculationHistory.size)

        viewModel.clearCalculationHistory()
        assertTrue("History should be empty after clear", viewModel.uiState.value.calculationHistory.isEmpty())
    }
}
