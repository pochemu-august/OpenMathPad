package com.augustuz.openmathpad.canvas

import com.augustuz.openmathpad.canvas.processing.PressureSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureSmootherTest {

    @Test
    fun calculateWidth_scalesWithPressure() {
        val smoother = PressureSmoother(minScale = 0.5f, maxScale = 1.5f, smoothingFactor = 1.0f)
        val baseWidth = 10.0f

        val (pressureLight, widthLight) = smoother.calculateWidth(rawPressure = 0.1f, baseWidth = baseWidth, isStylus = true)
        val smootherHeavy = PressureSmoother(minScale = 0.5f, maxScale = 1.5f, smoothingFactor = 1.0f)
        val (pressureHeavy, widthHeavy) = smootherHeavy.calculateWidth(rawPressure = 1.0f, baseWidth = baseWidth, isStylus = true)

        assertTrue("Heavy pressure width should be greater than light pressure width", widthHeavy > widthLight)
        assertEquals(0.1f, pressureLight, 0.01f)
        assertEquals(1.0f, pressureHeavy, 0.01f)
    }

    @Test
    fun calculateWidth_appliesSmoothingFactor() {
        val smoother = PressureSmoother(minScale = 0.5f, maxScale = 1.5f, smoothingFactor = 0.5f)
        val baseWidth = 10.0f

        val (p1, _) = smoother.calculateWidth(rawPressure = 0.2f, baseWidth = baseWidth, isStylus = true)
        assertEquals(0.2f, p1, 0.01f)

        // Second sample jumped to 1.0f, but smoother factor is 0.5f => smoothed should be ~0.6f
        val (p2, _) = smoother.calculateWidth(rawPressure = 1.0f, baseWidth = baseWidth, isStylus = true)
        assertEquals(0.6f, p2, 0.01f)
    }

    @Test
    fun calculateWidth_nonStylus_usesDefaultPressure() {
        val smoother = PressureSmoother(minScale = 0.5f, maxScale = 1.5f, smoothingFactor = 1.0f)
        val baseWidth = 10.0f

        val (pressure, width) = smoother.calculateWidth(rawPressure = 0.0f, baseWidth = baseWidth, isStylus = false)
        assertEquals(0.5f, pressure, 0.01f)
        assertEquals(10.0f, width, 0.01f)
    }

    @Test
    fun reset_clearsPreviousPressureState() {
        val smoother = PressureSmoother(minScale = 0.5f, maxScale = 1.5f, smoothingFactor = 0.2f)
        smoother.calculateWidth(rawPressure = 1.0f, baseWidth = 10f, isStylus = true)
        smoother.reset()

        // After reset, first point should take exact raw pressure without blending with previous
        val (pressure, _) = smoother.calculateWidth(rawPressure = 0.3f, baseWidth = 10f, isStylus = true)
        assertEquals(0.3f, pressure, 0.01f)
    }
}
