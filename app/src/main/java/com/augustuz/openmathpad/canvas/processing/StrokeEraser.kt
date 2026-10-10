package com.augustuz.openmathpad.canvas.processing

import com.augustuz.openmathpad.canvas.model.DrawingStroke
import kotlin.math.sqrt

/**
 * Geometric stroke intersection hit testing for stroke eraser functionality.
 */
object StrokeEraser {

    /**
     * Finds all strokes that intersect an eraser touch point within threshold radius.
     *
     * @param strokes List of candidate strokes on canvas.
     * @param x Touch X coordinate.
     * @param y Touch Y coordinate.
     * @param eraserRadius Eraser tolerance radius.
     * @return List of strokes intersected by eraser.
     */
    fun findStrokesIntersecting(
        strokes: List<DrawingStroke>,
        x: Float,
        y: Float,
        eraserRadius: Float
    ): List<DrawingStroke> {
        val erased = mutableListOf<DrawingStroke>()
        for (stroke in strokes) {
            if (strokeIntersectsPoint(stroke, x, y, eraserRadius)) {
                erased.add(stroke)
            }
        }
        return erased
    }

    /**
     * Checks if a stroke intersects with point (x, y) within eraser threshold.
     */
    fun strokeIntersectsPoint(
        stroke: DrawingStroke,
        x: Float,
        y: Float,
        eraserRadius: Float
    ): Boolean {
        val bbox = stroke.boundingBox
        val threshold = eraserRadius + stroke.baseWidth
        if (x < bbox[0] - threshold || x > bbox[2] + threshold ||
            y < bbox[1] - threshold || y > bbox[3] + threshold
        ) {
            return false
        }

        val points = stroke.points
        if (points.isEmpty()) return false

        if (points.size == 1) {
            val dx = points[0].x - x
            val dy = points[0].y - y
            return (dx * dx + dy * dy) <= (threshold * threshold)
        }

        for (i in 0 until points.size - 1) {
            val dist = distanceToSegment(
                x, y,
                points[i].x, points[i].y,
                points[i + 1].x, points[i + 1].y
            )
            if (dist <= threshold) {
                return true
            }
        }

        return false
    }

    private fun distanceToSegment(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1

        if (dx == 0f && dy == 0f) {
            val dX = px - x1
            val dY = py - y1
            return sqrt(dX * dX + dY * dY)
        }

        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy

        val dX = px - projX
        val dY = py - projY
        return sqrt(dX * dX + dY * dY)
    }
}
