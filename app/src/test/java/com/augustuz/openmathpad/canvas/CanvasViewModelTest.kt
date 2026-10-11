package com.augustuz.openmathpad.canvas

import android.view.MotionEvent
import androidx.compose.ui.graphics.Color
import com.augustuz.openmathpad.canvas.model.CanvasTool
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
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
        assertFalse("Touch mode must be disabled by default for 100% palm rejection", uiState.config.touchModeEnabled)
        assertTrue("Initial stroke list must be empty", uiState.strokes.isEmpty())
        assertTrue("Initial text annotation list must be empty", uiState.canvasTextAnnotations.isEmpty())
        assertTrue("Initial images list must be empty", uiState.images.isEmpty())
        assertFalse("Undo should be disabled initially", uiState.canUndo)
        assertFalse("Redo should be disabled initially", uiState.canRedo)
        assertEquals(0f, uiState.panOffsetX, 0.001f)
        assertEquals(0f, uiState.panOffsetY, 0.001f)
        assertEquals(1.0f, uiState.zoomScale, 0.001f)
    }

    @Test
    fun infiniteCanvas_panAndZoom_clampingAndReset() {
        // Zoom clamping test
        viewModel.setZoom(0.05f) // Below MIN_ZOOM (0.2x)
        assertEquals(CanvasViewModel.MIN_ZOOM, viewModel.uiState.value.zoomScale, 0.001f)

        viewModel.setZoom(10.0f) // Above MAX_ZOOM (5.0x)
        assertEquals(CanvasViewModel.MAX_ZOOM, viewModel.uiState.value.zoomScale, 0.001f)

        viewModel.updatePanAndZoom(panX = 250f, panY = -180f, zoom = 2.5f)
        assertEquals(250f, viewModel.uiState.value.panOffsetX, 0.001f)
        assertEquals(-180f, viewModel.uiState.value.panOffsetY, 0.001f)
        assertEquals(2.5f, viewModel.uiState.value.zoomScale, 0.001f)

        // Reset zoom to 100%
        viewModel.resetZoom()
        assertEquals(1.0f, viewModel.uiState.value.zoomScale, 0.001f)
        assertEquals(0f, viewModel.uiState.value.panOffsetX, 0.001f)
        assertEquals(0f, viewModel.uiState.value.panOffsetY, 0.001f)
    }

    @Test
    fun screenToSceneCoordinates_mapsAccurately() {
        viewModel.updatePanAndZoom(panX = 100f, panY = 50f, zoom = 2.0f)

        val screenX = 300f
        val screenY = 250f

        // Scene coordinate = (screen - pan) / zoom
        // SceneX = (300 - 100) / 2.0 = 100f
        // SceneY = (250 - 50) / 2.0 = 100f
        assertEquals(100f, viewModel.screenToSceneX(screenX), 0.001f)
        assertEquals(100f, viewModel.screenToSceneY(screenY), 0.001f)
    }

    @Test
    fun mediaInsertion_addImage_updatesStateAndSupportsUndoRedo() {
        val testUri = "content://media/external/images/media/42"
        viewModel.addImage(uri = testUri, x = 120f, y = 80f, width = 400f, height = 300f)

        val uiState = viewModel.uiState.value
        assertEquals(1, uiState.images.size)
        val image = uiState.images.first()
        assertEquals(testUri, image.uri)
        assertEquals(120f, image.x, 0.001f)
        assertEquals(80f, image.y, 0.001f)
        assertEquals(400f, image.width, 0.001f)
        assertEquals(300f, image.height, 0.001f)
        assertTrue(uiState.canUndo)

        // Undo image addition
        viewModel.undo()
        assertTrue("Undo must remove inserted image", viewModel.uiState.value.images.isEmpty())

        // Redo image addition
        viewModel.redo()
        assertEquals("Redo must restore inserted image", 1, viewModel.uiState.value.images.size)
    }

    @Test
    fun mediaInsertion_updatePositionAndResize_updatesImage() {
        val testUri = "content://media/external/images/media/99"
        viewModel.addImage(uri = testUri, x = 0f, y = 0f)
        val imageId = viewModel.uiState.value.images.first().id

        viewModel.updateImagePosition(imageId, 150f, 250f)
        val moved = viewModel.uiState.value.images.first()
        assertEquals(150f, moved.x, 0.001f)
        assertEquals(250f, moved.y, 0.001f)

        viewModel.resizeImage(imageId, 500f, 400f)
        val resized = viewModel.uiState.value.images.first()
        assertEquals(500f, resized.width, 0.001f)
        assertEquals(400f, resized.height, 0.001f)
    }

    @Test
    fun mediaInsertion_removeImage_removesAndSupportsUndo() {
        viewModel.addImage(uri = "test_uri_1")
        viewModel.addImage(uri = "test_uri_2")
        assertEquals(2, viewModel.uiState.value.images.size)

        val firstId = viewModel.uiState.value.images.first().id
        viewModel.removeImage(firstId)

        assertEquals(1, viewModel.uiState.value.images.size)
        assertFalse(viewModel.uiState.value.images.any { it.id == firstId })

        // Undo removal
        viewModel.undo()
        assertEquals(2, viewModel.uiState.value.images.size)
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
    fun clear_clearsStrokesAnnotationsAndImagesWithUndo() {
        viewModel.addImage("sample_uri")
        viewModel.insertResultToCanvas("F = m * a")
        viewModel.addStroke(DrawingStroke(points = listOf(StrokePoint(10f, 10f))))

        val stateBeforeClear = viewModel.uiState.value
        assertEquals(1, stateBeforeClear.images.size)
        assertEquals(1, stateBeforeClear.canvasTextAnnotations.size)
        assertEquals(1, stateBeforeClear.strokes.size)

        viewModel.clear()

        val clearedState = viewModel.uiState.value
        assertTrue(clearedState.images.isEmpty())
        assertTrue(clearedState.canvasTextAnnotations.isEmpty())
        assertTrue(clearedState.strokes.isEmpty())
        assertTrue(clearedState.canUndo)

        // Restore via undo
        viewModel.undo()
        val restoredState = viewModel.uiState.value
        assertEquals(1, restoredState.images.size)
        assertEquals(1, restoredState.canvasTextAnnotations.size)
        assertEquals(1, restoredState.strokes.size)
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
    fun clearCalculationHistory_clearsHistory() {
        viewModel.evaluateFormula("2 + 2")
        viewModel.evaluateFormula("5 * 5")
        assertEquals(2, viewModel.uiState.value.calculationHistory.size)

        viewModel.clearCalculationHistory()
        assertTrue("History should be empty after clear", viewModel.uiState.value.calculationHistory.isEmpty())
    }
}
