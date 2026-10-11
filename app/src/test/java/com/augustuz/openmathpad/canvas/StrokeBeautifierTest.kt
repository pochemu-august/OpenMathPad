package com.augustuz.openmathpad.canvas

import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.canvas.processing.StrokeBeautifier
import com.augustuz.openmathpad.canvas.processing.StrokeStabilizer
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
            StrokePoint(10f, 20f),
            StrokePoint(20f, 0f)
        )

        val smoothed = StrokeBeautifier.applyMovingAverage(points)

        assertEquals(3, smoothed.size)
        assertEquals(0f, smoothed.first().x, 0.001f)
        assertEquals(0f, smoothed.first().y, 0.001f)
        assertEquals(20f, smoothed.last().x, 0.001f)
        assertEquals(0f, smoothed.last().y, 0.001f)
        assertEquals(10f, smoothed[1].y, 0.001f)
        assertEquals(10f, smoothed[1].x, 0.001f)
    }

    @Test
    fun chaikinSmoothing_subdividesAndSmoothsSegments() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(50f, 100f),
            StrokePoint(100f, 0f)
        )

        val smoothed = StrokeBeautifier.applyChaikinSmoothing(points, iterations = 1)

        // 3 points -> 1 + (2 segments * 2) + 1 = 6 points
        assertEquals(6, smoothed.size)
        assertEquals(0f, smoothed.first().x, 0.001f)
        assertEquals(100f, smoothed.last().x, 0.001f)
        assertTrue("Chaikin smoothed points should be bounded within initial coordinates", smoothed.all { it.x in 0f..100f && it.y in 0f..100f })
    }

    @Test
    fun strokeStabilizer_filtersTremorsAndMaintainsContinuity() {
        val stabilizer = StrokeStabilizer(weight = 0.5f, deadzone = 0.5f)

        val (x1, y1, _) = stabilizer.filter(0f, 0f, 0.5f)
        assertEquals(0f, x1, 0.001f)
        assertEquals(0f, y1, 0.001f)

        // Step by 10 pixels
        val (x2, y2, _) = stabilizer.filter(10f, 10f, 0.8f)
        // 0 + 0.5 * 10 = 5.0
        assertEquals(5f, x2, 0.001f)
        assertEquals(5f, y2, 0.001f)

        // Micro-tremor below deadzone (0.3px) should be ignored
        val (x3, y3, _) = stabilizer.filter(5.3f, 5.2f, 0.8f)
        assertEquals(5f, x3, 0.001f)
        assertEquals(5f, y3, 0.001f)
    }

    @Test
    fun rdp_prunesCollinearRedundantPoints() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(25f, 0.2f),
            StrokePoint(50f, 0.1f),
            StrokePoint(75f, 0.3f),
            StrokePoint(100f, 0f)
        )

        val simplified = StrokeBeautifier.simplifyRdp(points, epsilon = 1.0f)

        assertEquals(2, simplified.size)
        assertEquals(0f, simplified.first().x, 0.001f)
        assertEquals(100f, simplified.last().x, 0.001f)
    }

    @Test
    fun dynamicPressureAndVelocityTapering_calculatesCalligraphicWidths() {
        val baseWidth = 8.0f
        val points = List(12) { i ->
            StrokePoint(
                x = i * 15f,
                y = 0f,
                pressure = 0.7f,
                timestamp = i * 25L
            )
        }

        val tapered = StrokeBeautifier.applyDynamicPressureAndTapering(points, baseWidth)

        assertEquals(12, tapered.size)
        val firstWidth = tapered.first().width
        val midWidth = tapered[6].width
        val lastWidth = tapered.last().width

        assertTrue("First point must be tapered compared to body of stroke", firstWidth < midWidth)
        assertTrue("Last point must be tapered compared to body of stroke", lastWidth < midWidth)
        assertTrue("All widths must be positive", tapered.all { it.width >= 1.0f })
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
            StrokePoint(30f, 50f, timestamp = 48L),
            StrokePoint(40f, 100f, timestamp = 64L),
            StrokePoint(50f, 100.1f, timestamp = 80L),
            StrokePoint(60f, 100f, timestamp = 96L)
        )
        val stroke = DrawingStroke(points = rawPoints, baseWidth = 8.0f)

        val beautified = StrokeBeautifier.beautifyStroke(stroke)

        assertNotNull(beautified)
        assertTrue(beautified.points.isNotEmpty())
        assertNotNull(beautified.path)
    }
}
