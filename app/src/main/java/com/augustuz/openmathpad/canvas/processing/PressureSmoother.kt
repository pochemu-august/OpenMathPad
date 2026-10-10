package com.augustuz.openmathpad.canvas.processing

import kotlin.math.max

/**
 * Pressure sensitivity scaling and exponential low-pass filter.
 * Smoothes raw stylus pressure and calculates dynamic stroke width.
 */
class PressureSmoother(
    private val minScale: Float = 0.35f,
    private val maxScale: Float = 1.75f,
    private val smoothingFactor: Float = 0.35f,
    private val defaultPressure: Float = 0.5f
) {
    private var lastSmoothedPressure: Float? = null

    /**
     * Resets filter state when beginning a new stroke gesture.
     */
    fun reset() {
        lastSmoothedPressure = null
    }

    /**
     * Calculates smoothed pressure and dynamic stroke width.
     *
     * @param rawPressure Stylus pressure from MotionEvent (0.0f to 1.0f+).
     * @param baseWidth Active base stroke width.
     * @param isStylus True if tool type is stylus/eraser.
     * @return Pair of (smoothedPressure, calculatedWidth).
     */
    fun calculateWidth(
        rawPressure: Float,
        baseWidth: Float,
        isStylus: Boolean
    ): Pair<Float, Float> {
        val effectiveRaw = if (!isStylus || rawPressure <= 0.001f) {
            defaultPressure
        } else {
            rawPressure.coerceIn(0.05f, 2.0f)
        }

        val smoothed = lastSmoothedPressure?.let { prev ->
            prev + smoothingFactor * (effectiveRaw - prev)
        } ?: effectiveRaw

        lastSmoothedPressure = smoothed

        val scale = minScale + (maxScale - minScale) * smoothed.coerceAtMost(1.2f)
        val calculatedWidth = max(1.0f, baseWidth * scale)

        return Pair(smoothed, calculatedWidth)
    }
}
