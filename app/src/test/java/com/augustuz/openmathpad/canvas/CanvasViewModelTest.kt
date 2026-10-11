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
        assertEquals(1.5f, uiState.config.penWidth, 0.001f)
        assertTrue("Notebook grid should be enabled by default", uiState.config.showGrid)
        assertFalse("Coordinate axes should be disabled by default", uiState.config.showCoordinateAxes)
        assertFalse("Ruler mode should be disabled by default", uiState.config.rulerModeEnabled)
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
    fun toggles_rulerGridAndAxes_switchCorrectly() {
        assertFalse(viewModel.uiState.value.config.rulerModeEnabled)
        viewModel.toggleRulerMode()
        assertTrue(viewModel.uiState.value.config.rulerModeEnabled)
        viewModel.toggleRulerMode()
        assertFalse(viewModel.uiState.value.config.rulerModeEnabled)

        assertTrue(viewModel.uiState.value.config.showGrid)
        viewModel.toggleGrid()
        assertFalse(viewModel.uiState.value.config.showGrid)
        viewModel.toggleGrid()
        assertTrue(viewModel.uiState.value.config.showGrid)

        assertFalse(viewModel.uiState.value.config.showCoordinateAxes)
        viewModel.toggleCoordinateAxes()
        assertTrue(viewModel.uiState.value.config.showCoordinateAxes)
        viewModel.toggleCoordinateAxes()
        assertFalse(viewModel.uiState.value.config.showCoordinateAxes)
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
    fun shapesAndTheme_updatesCorrectly() {
        assertEquals(com.augustuz.openmathpad.canvas.model.GeometricShape.NONE, viewModel.uiState.value.config.activeShape)
        viewModel.setActiveShape(com.augustuz.openmathpad.canvas.model.GeometricShape.LINE)
        assertEquals(com.augustuz.openmathpad.canvas.model.GeometricShape.LINE, viewModel.uiState.value.config.activeShape)

        assertFalse("Dark mode must be false by default for classic white notebook paper", viewModel.uiState.value.config.isDarkMode)
        viewModel.toggleDarkMode()
        assertTrue(viewModel.uiState.value.config.isDarkMode)
        viewModel.toggleDarkMode()
        assertFalse(viewModel.uiState.value.config.isDarkMode)
    }

    @Test
    fun highlighterControls_updatesCorrectly() {
        val yellow = Color(0xFFFFEB3B).copy(alpha = 0.45f)
        viewModel.setHighlighterColor(yellow)
        assertEquals(yellow, viewModel.uiState.value.config.highlighterColor)

        viewModel.setHighlighterWidth(32.0f)
        assertEquals(32.0f, viewModel.uiState.value.config.highlighterWidth, 0.01f)
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

    @Test
    fun gitpodTheme_togglingDarkMode_switchesDefaultInkColors() {
        // Default light mode has Deep Charcoal #0F172A ink
        assertEquals(com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.LightDefault, viewModel.uiState.value.config.penColor)

        // Toggling to dark mode should auto-switch default ink to Crisp Chalk White #F8FAFC
        viewModel.toggleDarkMode()
        assertTrue(viewModel.uiState.value.config.isDarkMode)
        assertEquals(com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.DarkDefault, viewModel.uiState.value.config.penColor)

        // Toggling back to light mode should restore Deep Charcoal #0F172A
        viewModel.toggleDarkMode()
        assertFalse(viewModel.uiState.value.config.isDarkMode)
        assertEquals(com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.LightDefault, viewModel.uiState.value.config.penColor)
    }

    @Test
    fun mediaResize_clampsToMinimum64dp() {
        viewModel.addImage("test.png", x = 0f, y = 0f, width = 200f, height = 200f)
        val imageId = viewModel.uiState.value.images.first().id

        // Attempt resizing below 64dp
        viewModel.resizeImage(imageId, width = 20f, height = 30f)
        val clamped = viewModel.uiState.value.images.first()
        assertEquals(64f, clamped.width, 0.001f)
        assertEquals(64f, clamped.height, 0.001f)

        // Attempt updateImageGeometry below 64dp
        viewModel.updateImageGeometry(imageId, 10f, 10f, 40f, 50f)
        val geomClamped = viewModel.uiState.value.images.first()
        assertEquals(64f, geomClamped.width, 0.001f)
        assertEquals(64f, geomClamped.height, 0.001f)
    }

    @Test
    fun geometricShapes_drawingStroke_createsAnalyticShapes() {
        val boxPoints = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(100f, 0f),
            StrokePoint(100f, 80f),
            StrokePoint(0f, 80f),
            StrokePoint(0f, 0f)
        )
        val boxStroke = DrawingStroke(
            points = boxPoints,
            shape = com.augustuz.openmathpad.canvas.model.GeometricShape.RECTANGLE
        )
        assertNotNull(boxStroke.path)
        assertEquals(com.augustuz.openmathpad.canvas.model.GeometricShape.RECTANGLE, boxStroke.shape)

        val vectorPoints = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(100f, 0f),
            StrokePoint(80f, -10f),
            StrokePoint(100f, 0f),
            StrokePoint(80f, 10f)
        )
        val vectorStroke = DrawingStroke(
            points = vectorPoints,
            shape = com.augustuz.openmathpad.canvas.model.GeometricShape.VECTOR_ARROW
        )
        assertNotNull(vectorStroke.path)
        assertEquals(com.augustuz.openmathpad.canvas.model.GeometricShape.VECTOR_ARROW, vectorStroke.shape)
    }

    @Test
    fun mediaInsertion_updateTaskImage_commitsGeometryAndSavesUndo() {
        val testPath = "/cache/tasks/test_image_persist.jpg"
        viewModel.addImage(filePath = testPath, x = 10f, y = 20f, width = 100f, height = 100f)
        val image = viewModel.uiState.value.images.first()

        val updated = image.copy(x = 150f, y = 220f, width = 300f, height = 250f)
        viewModel.updateTaskImage(updated)

        val persisted = viewModel.uiState.value.images.first()
        assertEquals(150f, persisted.x, 0.001f)
        assertEquals(220f, persisted.y, 0.001f)
        assertEquals(300f, persisted.width, 0.001f)
        assertEquals(250f, persisted.height, 0.001f)
        assertTrue(viewModel.uiState.value.canUndo)

        // Undo restores previous position
        viewModel.undo()
        val restored = viewModel.uiState.value.images.first()
        assertEquals(10f, restored.x, 0.001f)
        assertEquals(20f, restored.y, 0.001f)
        assertEquals(100f, restored.width, 0.001f)
        assertEquals(100f, restored.height, 0.001f)
    }

    @Test
    fun geometricShapes_circle_createsStationaryCenterAnalyticShape() {
        val circlePoints = listOf(
            StrokePoint(100f, 100f),
            StrokePoint(150f, 100f)
        )
        val circleStroke = DrawingStroke(
            points = circlePoints,
            shape = com.augustuz.openmathpad.canvas.model.GeometricShape.CIRCLE
        )
        assertNotNull(circleStroke.path)
        assertEquals(com.augustuz.openmathpad.canvas.model.GeometricShape.CIRCLE, circleStroke.shape)
    }

    @Test
    fun interactiveShapes_addSelectUpdateAndUndoRedo() {
        val testShape = com.augustuz.openmathpad.canvas.model.CanvasShape(
            id = "shape_1",
            type = com.augustuz.openmathpad.canvas.model.CanvasShapeType.VECTOR,
            startX = 10f,
            startY = 20f,
            endX = 110f,
            endY = 120f
        )
        viewModel.addShape(testShape)

        val stateAfterAdd = viewModel.uiState.value
        assertEquals(1, stateAfterAdd.shapes.size)
        assertEquals("shape_1", stateAfterAdd.selectedShapeId)
        assertTrue(stateAfterAdd.canUndo)

        // Select and update geometry
        viewModel.updateShapeGeometry("shape_1", 20f, 30f, 120f, 130f)
        val updated = viewModel.uiState.value.shapes.first()
        assertEquals(20f, updated.startX, 0.001f)
        assertEquals(30f, updated.startY, 0.001f)
        assertEquals(120f, updated.endX, 0.001f)
        assertEquals(130f, updated.endY, 0.001f)

        // Commit shape with undo point
        viewModel.commitShape(updated)

        // Deselect
        viewModel.selectShape(null)
        assertNull(viewModel.uiState.value.selectedShapeId)

        // Select
        viewModel.selectShape("shape_1")
        assertEquals("shape_1", viewModel.uiState.value.selectedShapeId)

        // Remove shape
        viewModel.removeShape("shape_1")
        assertTrue(viewModel.uiState.value.shapes.isEmpty())
        assertNull(viewModel.uiState.value.selectedShapeId)

        // Undo restores shape
        viewModel.undo()
        assertEquals(1, viewModel.uiState.value.shapes.size)

        // Redo removes shape again
        viewModel.redo()
        assertTrue(viewModel.uiState.value.shapes.isEmpty())
    }

    @Test
    fun strokeBounds_precomputesCachedBoundingBoxForCulling() {
        val stroke = DrawingStroke(
            points = listOf(
                StrokePoint(50f, 50f),
                StrokePoint(150f, 150f)
            ),
            baseWidth = 4f
        )
        val bounds = stroke.bounds
        assertTrue(bounds.left <= 50f)
        assertTrue(bounds.top <= 50f)
        assertTrue(bounds.right >= 150f)
        assertTrue(bounds.bottom >= 150f)

        // Viewport intersection testing
        val visibleViewport = androidx.compose.ui.geometry.Rect(0f, 0f, 200f, 200f)
        val offscreenViewport = androidx.compose.ui.geometry.Rect(500f, 500f, 800f, 800f)
        assertTrue(bounds.overlaps(visibleViewport))
        assertFalse(bounds.overlaps(offscreenViewport))
    }

    @Test
    fun fastStrokeLandingFilter_eliminatesJitterHook() {
        // Fast vertical downward stroke with initial backward hook:
        // p0 = (100, 100), p1 = (96, 95) [hook up-left], p2 = (100, 125), p3 = (100, 160)
        val rawPoints = listOf(
            StrokePoint(100f, 100f),
            StrokePoint(96f, 95f),
            StrokePoint(100f, 125f),
            StrokePoint(100f, 160f)
        )
        val filtered = com.augustuz.openmathpad.canvas.processing.BezierSmoother.filterLandingJitter(
            rawPoints,
            maxDistancePx = 18f
        )

        // Jitter hook point (96, 95) must be discarded
        assertEquals(3, filtered.size)
        // Dominant vector is downward, so origin aligns cleanly
        assertTrue("Origin y should align with stroke trajectory", filtered.first().y <= 125f)
        assertFalse("Hook point must be removed", filtered.any { it.x == 96f && it.y == 95f })
    }

    @Test
    fun adaptiveInkColor_resolvesCorrectLuminance() {
        val defaultInk = com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.Default
        assertTrue(com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.isDefaultInk(defaultInk))

        val resolvedLight = com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.resolve(defaultInk, isDark = false)
        val resolvedDark = com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.resolve(defaultInk, isDark = true)
        assertEquals(com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.LightDefault, resolvedLight)
        assertEquals(com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.DarkDefault, resolvedDark)

        // Custom colors retain hue across modes
        val customColor = Color(0xFF1D4ED8)
        val customResolvedLight = com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.resolve(customColor, isDark = false)
        val customResolvedDark = com.augustuz.openmathpad.canvas.model.AdaptiveInkColor.resolve(customColor, isDark = true)
        assertEquals(customColor, customResolvedLight)
        assertNotNull(customResolvedDark)
    }
}
