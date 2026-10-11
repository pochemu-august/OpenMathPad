package com.augustuz.openmathpad.canvas.processing

import com.augustuz.openmathpad.canvas.model.StrokePoint
import kotlin.math.hypot

/**
 * Real-time Streamline / Pull-String Stabilizer for stroke input.
 * Absorbs high-frequency hand tremors, wobble, and accidental jitter.
 *
 * @param weight Tracking/follow coefficient for exponential smoothing (0.0 to 1.0).
 *               Lower values give stronger stabilization; higher values follow pen closer.
 * @param deadzone Deadband threshold in pixels to prevent trembling when holding still.
 */
class StrokeStabilizer(
    private val weight: Float = 0.45f,
    private val deadzone: Float = 0.5f
) {
    private var lastX: Float = Float.NaN
    private var lastY: Float = Float.NaN
    private var lastPressure: Float = Float.NaN

    fun reset() {
        lastX = Float.NaN
        lastY = Float.NaN
        lastPressure = Float.NaN
    }

    /**
     * Filters a single coordinate in real time during MotionEvent processing.
     */
    fun filter(rawX: Float, rawY: Float, rawPressure: Float): Triple<Float, Float, Float> {
        if (lastX.isNaN() || lastY.isNaN()) {
            lastX = rawX
            lastY = rawY
            lastPressure = rawPressure
            return Triple(rawX, rawY, rawPressure)
        }

        val dx = rawX - lastX
        val dy = rawY - lastY
        val dist = hypot(dx, dy)

        if (dist < deadzone) {
            return Triple(lastX, lastY, lastPressure)
        }

        val stabilizedX = lastX + weight * dx
        val stabilizedY = lastY + weight * dy
        val stabilizedPressure = if (lastPressure.isNaN()) {
            rawPressure
        } else {
            lastPressure + weight * (rawPressure - lastPressure)
        }

        lastX = stabilizedX
        lastY = stabilizedY
        lastPressure = stabilizedPressure

        return Triple(stabilizedX, stabilizedY, stabilizedPressure)
    }

    /**
     * Stabilizes a list of recorded points sequentially.
     */
    fun filterPoints(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size <= 1) return points
        reset()
        val result = ArrayList<StrokePoint>(points.size)
        for (p in points) {
            val (sx, sy, sp) = filter(p.x, p.y, p.pressure)
            result.add(p.copy(x = sx, y = sy, pressure = sp))
        }
        return result
    }
}
