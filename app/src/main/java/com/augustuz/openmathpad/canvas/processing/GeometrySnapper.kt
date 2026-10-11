package com.augustuz.openmathpad.canvas.processing

import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

/**
 * Geometric magnetic snapping, shape generation, and straightness calculation engine for GeoGebra-style drawing.
 */
object GeometrySnapper {

    /**
     * Calculates the linearity / straightness ratio of a drawn stroke:
     * direct distance between start and end divided by the total integrated path length.
     *
     * @param points List of recorded StrokePoints.
     * @return Value in [0.0, 1.0]. Nearly straight lines return >= 0.88.
     */
    fun calculateStraightness(points: List<StrokePoint>): Float {
        if (points.size < 2) return 0f
        val start = points.first()
        val end = points.last()
        val directDist = hypot(end.x - start.x, end.y - start.y)
        if (directDist < 5f) return 0f

        var totalPathLength = 0f
        for (i in 1 until points.size) {
            totalPathLength += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }

        if (totalPathLength <= 0f) return 0f
        return (directDist / totalPathLength).coerceIn(0f, 1f)
    }

    /**
     * Snaps a scene coordinate (x, y) to the nearest existing stroke vertex (endpoint)
     * or nearest grid intersection within [snapThreshold].
     *
     * @param x Scene X coordinate.
     * @param y Scene Y coordinate.
     * @param existingStrokes List of completed strokes on the canvas.
     * @param gridSize Distance between grid lines in scene units.
     * @param isGridEnabled True if notebook squared paper grid is active.
     * @param snapThreshold Maximum distance for magnetic snapping.
     * @return Snapped (x, y) coordinates or original (x, y) if outside threshold.
     */
    fun snapPoint(
        x: Float,
        y: Float,
        existingStrokes: List<DrawingStroke>,
        gridSize: Float = 36f,
        isGridEnabled: Boolean = true,
        snapThreshold: Float = 20f
    ): Pair<Float, Float> {
        var closestDist = snapThreshold
        var snappedX = x
        var snappedY = y
        var foundVertex = false

        // 1. First priority: Snap to existing stroke vertices (start and end endpoints)
        for (stroke in existingStrokes) {
            val pts = stroke.points
            if (pts.isEmpty()) continue

            val first = pts.first()
            val dFirst = hypot(x - first.x, y - first.y)
            if (dFirst < closestDist) {
                closestDist = dFirst
                snappedX = first.x
                snappedY = first.y
                foundVertex = true
            }

            val last = pts.last()
            val dLast = hypot(x - last.x, y - last.y)
            if (dLast < closestDist) {
                closestDist = dLast
                snappedX = last.x
                snappedY = last.y
                foundVertex = true
            }
        }

        if (foundVertex) {
            return Pair(snappedX, snappedY)
        }

        // 2. Second priority: Snap to nearest grid intersection
        if (isGridEnabled && gridSize > 0f) {
            val nearestGridX = round(x / gridSize) * gridSize
            val nearestGridY = round(y / gridSize) * gridSize
            val dGrid = hypot(x - nearestGridX, y - nearestGridY)
            if (dGrid < snapThreshold) {
                return Pair(nearestGridX, nearestGridY)
            }
        }

        return Pair(x, y)
    }

    /**
     * Converts recorded stroke points into a perfectly straight line segment connecting
     * start and end points, with magnetic snapping applied to both endpoints.
     */
    fun createStraightLinePoints(
        points: List<StrokePoint>,
        existingStrokes: List<DrawingStroke>,
        gridSize: Float = 36f,
        isGridEnabled: Boolean = true,
        snapThreshold: Float = 20f,
        numSamples: Int = 8
    ): List<StrokePoint> {
        if (points.isEmpty()) return points
        if (points.size == 1) return points

        val start = points.first()
        val end = points.last()

        val (snappedStartX, snappedStartY) = snapPoint(
            start.x, start.y, existingStrokes, gridSize, isGridEnabled, snapThreshold
        )
        val (snappedEndX, snappedEndY) = snapPoint(
            end.x, end.y, existingStrokes, gridSize, isGridEnabled, snapThreshold
        )

        val result = ArrayList<StrokePoint>(numSamples + 1)
        for (i in 0..numSamples) {
            val t = i.toFloat() / numSamples
            val curX = snappedStartX + t * (snappedEndX - snappedStartX)
            val curY = snappedStartY + t * (snappedEndY - snappedStartY)
            val time = (start.timestamp + t * (end.timestamp - start.timestamp)).toLong()
            result.add(
                StrokePoint(
                    x = curX,
                    y = curY,
                    pressure = 0.5f,
                    width = start.width,
                    timestamp = time
                )
            )
        }
        return result
    }

    /**
     * Generates points for a Vector Arrow (line segment with sharp symmetric arrowhead wings).
     * Wings are angled backwards at 150 degrees relative to trajectory.
     */
    fun createVectorArrowPoints(
        start: StrokePoint,
        end: StrokePoint,
        existingStrokes: List<DrawingStroke>,
        gridSize: Float = 36f,
        isGridEnabled: Boolean = true,
        snapThreshold: Float = 20f
    ): List<StrokePoint> {
        val (sX, sY) = snapPoint(start.x, start.y, existingStrokes, gridSize, isGridEnabled, snapThreshold)
        val (eX, eY) = snapPoint(end.x, end.y, existingStrokes, gridSize, isGridEnabled, snapThreshold)

        val angle = atan2(eY - sY, eX - sX)
        val arrowLen = 22f
        val wingAngleRad = 2.61799f // 150 degrees backwards from trajectory

        val w1X = eX + arrowLen * cos(angle + wingAngleRad)
        val w1Y = eY + arrowLen * sin(angle + wingAngleRad)
        val w2X = eX + arrowLen * cos(angle - wingAngleRad)
        val w2Y = eY + arrowLen * sin(angle - wingAngleRad)

        val time = end.timestamp
        val width = start.width
        val pressure = 0.5f

        // Traces: start -> end -> wing1 -> end -> wing2
        return listOf(
            StrokePoint(sX, sY, pressure, width, start.timestamp),
            StrokePoint(eX, eY, pressure, width, time),
            StrokePoint(w1X, w1Y, pressure, width, time),
            StrokePoint(eX, eY, pressure, width, time),
            StrokePoint(w2X, w2Y, pressure, width, time)
        )
    }

    /**
     * Generates points for a strict closed Rectangle / Box with 4 corners and closed loop.
     */
    fun createRectanglePoints(
        start: StrokePoint,
        end: StrokePoint,
        existingStrokes: List<DrawingStroke>,
        gridSize: Float = 36f,
        isGridEnabled: Boolean = true,
        snapThreshold: Float = 20f
    ): List<StrokePoint> {
        val (sX, sY) = snapPoint(start.x, start.y, existingStrokes, gridSize, isGridEnabled, snapThreshold)
        val (eX, eY) = snapPoint(end.x, end.y, existingStrokes, gridSize, isGridEnabled, snapThreshold)

        val left = minOf(sX, eX)
        val top = minOf(sY, eY)
        val right = maxOf(sX, eX)
        val bottom = maxOf(sY, eY)

        val time = end.timestamp
        val width = start.width
        val pressure = 0.5f

        // Strict 90-degree closed rectangle corners:
        // (left, top) -> (right, top) -> (right, bottom) -> (left, bottom) -> (left, top)
        return listOf(
            StrokePoint(left, top, pressure, width, start.timestamp),
            StrokePoint(right, top, pressure, width, time),
            StrokePoint(right, bottom, pressure, width, time),
            StrokePoint(left, bottom, pressure, width, time),
            StrokePoint(left, top, pressure, width, time)
        )
    }

    /**
     * Generates points for a Circle with stationary center at [start] and radius to [end].
     * Preserves strict stationary center (x0, y0) with symmetric expansion.
     */
    fun createCirclePoints(
        start: StrokePoint,
        end: StrokePoint,
        existingStrokes: List<DrawingStroke>,
        gridSize: Float = 36f,
        isGridEnabled: Boolean = true,
        snapThreshold: Float = 20f
    ): List<StrokePoint> {
        val (sX, sY) = snapPoint(start.x, start.y, existingStrokes, gridSize, isGridEnabled, snapThreshold)
        val (eX, eY) = snapPoint(end.x, end.y, existingStrokes, gridSize, isGridEnabled, snapThreshold)
        return listOf(
            start.copy(x = sX, y = sY),
            end.copy(x = eX, y = eY)
        )
    }
}
