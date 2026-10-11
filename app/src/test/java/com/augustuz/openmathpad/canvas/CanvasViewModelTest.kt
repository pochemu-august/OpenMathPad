package com.augustuz.openmathpad.canvas

import androidx.compose.ui.graphics.Color
import com.augustuz.openmathpad.canvas.model.CanvasTool
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.canvas.state.CanvasViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertTrue("Initial images list must be empty", uiState.images.isEmpty())
        assertNull("No image selected initially", uiState.selectedImageId)
        assertFalse("Undo should be disabled initially", uiState.canUndo)
        assertFalse("Redo should be disabled initially", uiState.canRedo)
        assertEquals(0f, uiState.panOffsetX, 0.001f)
        assertEquals(0f, uiState.panOffsetY, 0.001f)
        assertEquals(1.0f, uiState.zoomScale, 0.001f)
    }

    @Test
    fun infiniteCanvas_panAndZoom_clampingAndReset() {
        viewModel.setZoom(0.05f) // Below MIN_ZOOM (0.2x)
        assertEquals(CanvasViewModel.MIN_ZOOM, viewModel.uiState.value.zoomScale, 0.001f)

        viewModel.setZoom(10.0f) // Above MAX_ZOOM (5.0x)
        assertEquals(CanvasViewModel.MAX_ZOOM, viewModel.uiState.value.zoomScale, 0.001f)

        viewModel.updatePanAndZoom(panX = 250f, panY = -180f, zoom = 2.5f)
        assertEquals(250f, viewModel.uiState.value.panOffsetX, 0.001f)
        assertEquals(-180f, viewModel.uiState.value.panOffsetY, 0.001f)
        assertEquals(2.5f, viewModel.uiState.value.zoomScale, 0.001f)

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
        assertEquals(100f, viewModel.screenToSceneX(screenX), 0.001f)
        assertEquals(100f, viewModel.screenToSceneY(screenY), 0.001f)
    }

    @Test
    fun mediaInsertion_addImage_updatesStateAndSupportsUndoRedo() {
        val testPath = "/cache/tasks/test_image.jpg"
        viewModel.addImage(filePath = testPath, x = 120f, y = 80f, width = 400f, height = 300f)

        val uiState = viewModel.uiState.value
        assertEquals(1, uiState.images.size)
        val image = uiState.images.first()
        assertEquals(testPath, image.filePath)
        assertEquals(120f, image.x, 0.001f)
        assertEquals(80f, image.y, 0.001f)
        assertEquals(400f, image.width, 0.001f)
        assertEquals(300f, image.height, 0.001f)
        assertEquals(image.id, uiState.selectedImageId)
        assertTrue(uiState.canUndo)

        // Undo image addition
        viewModel.undo()
        assertTrue("Undo must remove inserted image", viewModel.uiState.value.images.isEmpty())

        // Redo image addition
        viewModel.redo()
        assertEquals("Redo must restore inserted image", 1, viewModel.uiState.value.images.size)
    }

    @Test
    fun mediaInsertion_updateGeometryAndSelect_updatesImage() {
        val testPath = "/cache/tasks/test_image_2.jpg"
        viewModel.addImage(filePath = testPath, x = 0f, y = 0f)
        val imageId = viewModel.uiState.value.images.first().id

        viewModel.updateImageGeometry(imageId, 50f, 60f, 500f, 400f)
        val updated = viewModel.uiState.value.images.first()
        assertEquals(50f, updated.x, 0.001f)
        assertEquals(60f, updated.y, 0.001f)
        assertEquals(500f, updated.width, 0.001f)
        assertEquals(400f, updated.height, 0.001f)

        viewModel.selectImage(null)
        assertNull(viewModel.uiState.value.selectedImageId)
        assertFalse(viewModel.uiState.value.images.first().isSelected)

        viewModel.selectImage(imageId)
        assertEquals(imageId, viewModel.uiState.value.selectedImageId)
        assertTrue(viewModel.uiState.value.images.first().isSelected)
    }

    @Test
    fun mediaInsertion_removeImage_removesAndSupportsUndo() {
        viewModel.addImage(filePath = "task_1.jpg")
        viewModel.addImage(filePath = "task_2.jpg")
        assertEquals(2, viewModel.uiState.value.images.size)

        val firstId = viewModel.uiState.value.images.first().id
        viewModel.removeImage(firstId)

        assertEquals(1, viewModel.uiState.value.images.size)
        assertFalse(viewModel.uiState.value.images.any { it.id == firstId })

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
    fun clear_clearsStrokesAndImagesWithUndo() {
        viewModel.addImage("sample_path.jpg")
        viewModel.addStroke(DrawingStroke(points = listOf(StrokePoint(10f, 10f))))

        val stateBeforeClear = viewModel.uiState.value
        assertEquals(1, stateBeforeClear.images.size)
        assertEquals(1, stateBeforeClear.strokes.size)

        viewModel.clear()

        val clearedState = viewModel.uiState.value
        assertTrue(clearedState.images.isEmpty())
        assertTrue(clearedState.strokes.isEmpty())
        assertTrue(clearedState.canUndo)

        viewModel.undo()
        val restoredState = viewModel.uiState.value
        assertEquals(1, restoredState.images.size)
        assertEquals(1, restoredState.strokes.size)
    }
}
