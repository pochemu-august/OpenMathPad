package com.augustuz.openmathpad.canvas.model

/**
 * Represents a single coordinate recorded from MotionEvent stream.
 *
 * @param x X coordinate on canvas surface.
 * @param y Y coordinate on canvas surface.
 * @param pressure Stylus touch pressure (0.0f to 1.0f+).
 * @param width Calculated stroke width at this point based on pressure scaling.
 * @param timestamp Time of event capture in milliseconds.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val width: Float = 5.0f,
    val timestamp: Long = System.currentTimeMillis()
)
