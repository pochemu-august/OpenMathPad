package com.augustuz.openmathpad.canvas

import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.canvas.rasterizer.CanvasRasterizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasRasterizerTest {

    private val rasterizer = CanvasRasterizer(
        targetWidth = 512,
        targetHeight = 128,
        paddingDp = 10f,
        resultMarginDp = 20f
    )

    @Test
    fun rasterize_emptyStrokes_returnsBlankResult() {
        val result = rasterizer.rasterize(emptyList())

        assertTrue("Result for empty strokes must be blank", result.isBlank)
        assertEquals(0f, result.minX, 0.001f)
        assertEquals(0f, result.minY, 0.001f)
        assertEquals(0f, result.placementPointX, 0.001f)
    }

    @Test
    fun rasterize_strokesWithNoPoints_returnsBlankResult() {
        val emptyStroke = DrawingStroke(points = emptyList())
        val result = rasterizer.rasterize(listOf(emptyStroke))

        assertTrue("Result for strokes with no points must be blank", result.isBlank)
    }

    @Test
    fun rasterize_validStrokes_computesBoundsAndPlacementPoint() {
        val stroke = DrawingStroke(
            points = listOf(
                StrokePoint(x = 100f, y = 50f),
                StrokePoint(x = 300f, y = 150f)
            )
        )

        val result = rasterizer.rasterize(listOf(stroke))

        assertFalse("Result for valid strokes must not be blank", result.isBlank)

        // Bounds = [minX - padding, minY - padding, maxX + padding, maxY + padding]
        // minX = 100, maxX = 300, minY = 50, maxY = 150, padding = 10
        assertEquals(90f, result.minX, 0.001f)
        assertEquals(40f, result.minY, 0.001f)
        assertEquals(310f, result.maxX, 0.001f)
        assertEquals(160f, result.maxY, 0.001f)

        // Placement point X = bounds.right + margin (310 + 20 = 330)
        assertEquals(330f, result.placementPointX, 0.001f)
        // Placement point Y = bounds.centerY() (100)
        assertEquals(100f, result.placementPointY, 0.001f)
    }

    @Test
    fun rasterize_multipleStrokes_computesCombinedBounds() {
        val stroke1 = DrawingStroke(
            points = listOf(
                StrokePoint(x = 10f, y = 20f),
                StrokePoint(x = 50f, y = 60f)
            )
        )
        val stroke2 = DrawingStroke(
            points = listOf(
                StrokePoint(x = 100f, y = 120f),
                StrokePoint(x = 200f, y = 220f)
            )
        )

        val result = rasterizer.rasterize(listOf(stroke1, stroke2))

        assertFalse(result.isBlank)
        // minX=10, maxX=200, minY=20, maxY=220, padding=10
        assertEquals(0f, result.minX, 0.001f)
        assertEquals(10f, result.minY, 0.001f)
        assertEquals(210f, result.maxX, 0.001f)
        assertEquals(230f, result.maxY, 0.001f)
    }

    @Test
    fun rasterize_customPaddingAndMargin_appliesToPaddedBounds() {
        val customRasterizer = CanvasRasterizer(
            targetWidth = 256,
            targetHeight = 256,
            paddingDp = 25f,
            resultMarginDp = 50f
        )
        val stroke = DrawingStroke(
            points = listOf(
                StrokePoint(x = 100f, y = 100f),
                StrokePoint(x = 200f, y = 200f)
            )
        )

        val result = customRasterizer.rasterize(listOf(stroke))

        // minX = 100 - 25 = 75, maxX = 200 + 25 = 225
        assertEquals(75f, result.minX, 0.001f)
        assertEquals(225f, result.maxX, 0.001f)
        // placementPointX = 225 + 50 = 275
        assertEquals(275f, result.placementPointX, 0.001f)
    }

    @Test
    fun rasterizeStrokes_aliasMethod_returnsIdenticalResult() {
        val stroke = DrawingStroke(
            points = listOf(
                StrokePoint(x = 50f, y = 50f),
                StrokePoint(x = 150f, y = 150f)
            )
        )

        val result1 = rasterizer.rasterize(listOf(stroke))
        val result2 = rasterizer.rasterizeStrokes(listOf(stroke))

        assertEquals(result1.minX, result2.minX, 0.001f)
        assertEquals(result1.maxX, result2.maxX, 0.001f)
        assertEquals(result1.placementPointX, result2.placementPointX, 0.001f)
        assertEquals(result1.isBlank, result2.isBlank)
    }

    @Test
    fun rasterize_bitmapRendering_handlesBitmapCreationAndResultStructure() {
        val stroke = DrawingStroke(
            points = listOf(
                StrokePoint(x = 10f, y = 10f),
                StrokePoint(x = 100f, y = 100f)
            )
        )

        val result = rasterizer.rasterize(listOf(stroke))

        assertNotNull("Result bounds must be computed", result.bounds)
        assertNotNull("Result placement point must be computed", result.resultPlacementPoint)
        assertFalse("Result must not be marked blank for valid stroke", result.isBlank)
    }
}
