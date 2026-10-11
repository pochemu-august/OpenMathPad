package com.augustuz.openmathpad.canvas

import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.canvas.processing.GeometrySnapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometrySnapperTest {

    @Test
    fun snapPoint_snapsToExistingStrokeVertex_withinThreshold() {
        val stroke = DrawingStroke(
            points = listOf(
                StrokePoint(x = 100f, y = 100f),
                StrokePoint(x = 200f, y = 100f)
            )
        )

        // Point near start vertex (102, 98) -> should snap to (100, 100)
        val (snappedX, snappedY) = GeometrySnapper.snapPoint(
            x = 102f,
            y = 98f,
            existingStrokes = listOf(stroke),
            snapThreshold = 20f
        )
        assertEquals(100f, snappedX, 0.001f)
        assertEquals(100f, snappedY, 0.001f)

        // Point near end vertex (195, 103) -> should snap to (200, 100)
        val (endX, endY) = GeometrySnapper.snapPoint(
            x = 195f,
            y = 103f,
            existingStrokes = listOf(stroke),
            snapThreshold = 20f
        )
        assertEquals(200f, endX, 0.001f)
        assertEquals(100f, endY, 0.001f)
    }

    @Test
    fun snapPoint_snapsToGridIntersection_whenNoVertexNearby() {
        // Grid cell = 36px, intersection at (72, 108)
        val nearGridX = 73f
        val nearGridY = 106f

        val (snappedX, snappedY) = GeometrySnapper.snapPoint(
            x = nearGridX,
            y = nearGridY,
            existingStrokes = emptyList(),
            gridSize = 36f,
            isGridEnabled = true,
            snapThreshold = 20f
        )
        assertEquals(72f, snappedX, 0.001f)
        assertEquals(108f, snappedY, 0.001f)
    }

    @Test
    fun snapPoint_returnsOriginal_whenOutsideThreshold() {
        val (x, y) = GeometrySnapper.snapPoint(
            x = 50f,
            y = 50f,
            existingStrokes = emptyList(),
            gridSize = 36f,
            isGridEnabled = true,
            snapThreshold = 5f // Too small threshold
        )
        // 50 is distance 14 from 36 and distance 22 from 72, so distance > 5
        assertEquals(50f, x, 0.001f)
        assertEquals(50f, y, 0.001f)
    }

    @Test
    fun calculateStraightness_straightLine_returnsHighRatio() {
        val linePoints = listOf(
            StrokePoint(x = 0f, y = 0f),
            StrokePoint(x = 25f, y = 25f),
            StrokePoint(x = 50f, y = 50f),
            StrokePoint(x = 100f, y = 100f)
        )
        val straightness = GeometrySnapper.calculateStraightness(linePoints)
        assertTrue("Straight line must have straightness >= 0.88", straightness >= 0.88f)
    }

    @Test
    fun calculateStraightness_curvedLoop_returnsLowRatio() {
        // Circular loop / letter 'O' shape
        val loopPoints = listOf(
            StrokePoint(x = 0f, y = 0f),
            StrokePoint(x = 50f, y = 50f),
            StrokePoint(x = 100f, y = 0f),
            StrokePoint(x = 50f, y = -50f),
            StrokePoint(x = 5f, y = -5f) // Ends close to start
        )
        val straightness = GeometrySnapper.calculateStraightness(loopPoints)
        assertTrue("Curved loop must have straightness < 0.88 to prevent ruining handwriting", straightness < 0.88f)
    }

    @Test
    fun createStraightLinePoints_generatesCollinearSnappedLine() {
        val rawPoints = listOf(
            StrokePoint(x = 1f, y = 2f),
            StrokePoint(x = 15f, y = 30f),
            StrokePoint(x = 45f, y = 80f),
            StrokePoint(x = 73f, y = 107f)
        )

        val straight = GeometrySnapper.createStraightLinePoints(
            points = rawPoints,
            existingStrokes = emptyList(),
            gridSize = 36f,
            isGridEnabled = true,
            snapThreshold = 24f,
            numSamples = 5
        )

        assertEquals(6, straight.size)
        // Start snaps to (0, 0)
        assertEquals(0f, straight.first().x, 0.001f)
        assertEquals(0f, straight.first().y, 0.001f)
        // End snaps to (72, 108)
        assertEquals(72f, straight.last().x, 0.001f)
        assertEquals(108f, straight.last().y, 0.001f)

        // Verify intermediate points are strictly collinear
        for (i in 1 until straight.size - 1) {
            val t = i / 5f
            assertEquals(72f * t, straight[i].x, 0.001f)
            assertEquals(108f * t, straight[i].y, 0.001f)
        }
    }

    @Test
    fun createRectanglePoints_createsClosedBox() {
        val start = StrokePoint(x = 2f, y = 2f)
        val end = StrokePoint(x = 71f, y = 107f)

        val rectPoints = GeometrySnapper.createRectanglePoints(
            start = start,
            end = end,
            existingStrokes = emptyList(),
            gridSize = 36f,
            isGridEnabled = true,
            snapThreshold = 20f
        )

        // Snaps to (0, 0) and (72, 108)
        assertEquals(5, rectPoints.size)
        assertEquals(0f, rectPoints.first().x, 0.001f)
        assertEquals(0f, rectPoints.first().y, 0.001f)
        assertEquals(0f, rectPoints.last().x, 0.001f)
        assertEquals(0f, rectPoints.last().y, 0.001f)
    }

    @Test
    fun createVectorArrowPoints_createsArrowHeadWings() {
        val start = StrokePoint(x = 0f, y = 0f)
        val end = StrokePoint(x = 72f, y = 0f)

        val arrowPoints = GeometrySnapper.createVectorArrowPoints(
            start = start,
            end = end,
            existingStrokes = emptyList(),
            gridSize = 36f,
            isGridEnabled = true,
            snapThreshold = 20f
        )

        // Traces: start -> end -> wing1 -> end -> wing2
        assertEquals(5, arrowPoints.size)
        assertEquals(0f, arrowPoints[0].x, 0.001f)
        assertEquals(72f, arrowPoints[1].x, 0.001f)
        assertEquals(72f, arrowPoints[3].x, 0.001f)
    }
}
