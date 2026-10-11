package com.augustuz.openmathpad.canvas

import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.canvas.processing.StrokeBeautifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeBeautifierTest {

    @Test
    fun movingAverage_preservesEndpointsAndSmoothsJitter() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(10f, 20f), // Jitter spike at Y=20 instead of 10
            StrokePoint(20f, 0f)
        )

        val smoothed = StrokeBeautifier.applyMovingAverage(points)

        assertEquals(3, smoothed.size)
        // Endpoints preserved
        assertEquals(0f, smoothed.first().x, 0.001f)
        assertEquals(0f, smoothed.first().y, 0.001f)
        assertEquals(20f, smoothed.last().x, 0.001f)
        assertEquals(0f, smoothed.last().y, 0.001f)

        // Middle point: 0.25*0 + 0.5*20 + 0.25*0 = 10.0f
        assertEquals(10f, smoothed[1].y, 0.001f)
        assertEquals(10f, smoothed[1].x, 0.001f)
    }

    @Test
    fun movingAverage_shortPointList_returnsUnchanged() {
        val empty = emptyList<StrokePoint>()
        assertEquals(empty, StrokeBeautifier.applyMovingAverage(empty))

        val single = listOf(StrokePoint(5f, 5f))
        assertEquals(single, StrokeBeautifier.applyMovingAverage(single))

        val two = listOf(StrokePoint(0f, 0f), StrokePoint(10f, 10f))
        assertEquals(two, StrokeBeautifier.applyMovingAverage(two))
    }

    @Test
    fun rdp_prunesCollinearRedundantPoints() {
        // Collinear line from (0,0) to (100,0) with micro-jitter points along the line
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(25f, 0.2f),
            StrokePoint(50f, 0.1f),
            StrokePoint(75f, 0.3f),
            StrokePoint(100f, 0f)
        )

        val simplified = StrokeBeautifier.simplifyRdp(points, epsilon = 1.0f)

        // With epsilon = 1.0f, the micro-deviations (<0.3f) are pruned
        assertEquals(2, simplified.size)
        assertEquals(0f, simplified.first().x, 0.001f)
        assertEquals(100f, simplified.last().x, 0.001f)
    }

    @Test
    fun rdp_preservesSharpCornersOfMathSymbols() {
        // V-shape / Square Root vertex: (0, 100) -> (50, 0) -> (100, 100)
        val points = listOf(
            StrokePoint(0f, 100f),
            StrokePoint(25f, 50f),
            StrokePoint(50f, 0f), // Sharp vertex corner
            StrokePoint(75f, 50f),
            StrokePoint(100f, 100f)
        )

        val simplified = StrokeBeautifier.simplifyRdp(points, epsilon = 1.0f)

        // Must retain the sharp vertex corner at (50, 0)
        assertTrue(simplified.size >= 3)
        val hasVertex = simplified.any { it.x == 50f && it.y == 0f }
        assertTrue("RDP must retain corner vertices for mathematical symbols", hasVertex)
    }

    @Test
    fun dynamicPressureAndTapering_tapersStartAndEnd() {
        val baseWidth = 10.0f
        val points = List(15) { i ->
            StrokePoint(
                x = i * 10f,
                y = 0f,
                pressure = 0.5f,
                timestamp = i * 20L
            )
        }

        val tapered = StrokeBeautifier.applyDynamicPressureAndTapering(points, baseWidth)

        assertEquals(15, tapered.size)
        val firstWidth = tapered.first().width
        val midWidth = tapered[7].width
        val lastWidth = tapered.last().width

        assertTrue("First point must be tapered down compared to mid stroke", firstWidth < midWidth)
        assertTrue("Last point must be tapered down compared to mid stroke", lastWidth < midWidth)
        assertTrue("Widths must always be positive and at least 1.0f", tapered.all { it.width >= 1.0f })
    }

    @Test
    fun buildSmoothPath_producesValidPath() {
        val points = listOf(
            StrokePoint(10f, 10f),
            StrokePoint(30f, 60f),
            StrokePoint(70f, 80f),
            StrokePoint(120f, 40f)
        )

        val path = StrokeBeautifier.buildSmoothPath(points)
        assertNotNull(path)
        assertFalse("Path must not be empty for valid points", path.isEmpty)
    }

    @Test
    fun beautifyStroke_runsFullPipeline() {
        val rawPoints = listOf(
            StrokePoint(0f, 0f, timestamp = 0L),
            StrokePoint(10f, 0.2f, timestamp = 16L),
            StrokePoint(20f, 0.1f, timestamp = 32L),
            StrokePoint(30f, 50f, timestamp = 48L), // Corner
            StrokePoint(40f, 100f, timestamp = 64L),
            StrokePoint(50f, 100.1f, timestamp = 80L),
            StrokePoint(60f, 100f, timestamp = 96L)
        )
        val stroke = DrawingStroke(points = rawPoints, baseWidth = 8.0f)

        val beautified = StrokeBeautifier.beautifyStroke(stroke)

        assertNotNull(beautified)
        assertTrue(beautified.points.isNotEmpty())
        assertTrue("Beautified stroke must have fewer or equal points due to RDP pruning", beautified.points.size <= rawPoints.size)
        // Ensure path can be computed without throwing
        assertNotNull(beautified.path)
    }
}
