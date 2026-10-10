package com.augustuz.openmathpad.canvas.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.augustuz.openmathpad.canvas.processing.BezierSmoother
import java.util.UUID

/**
 * Represents a complete drawn stroke on the canvas.
 *
 * @param id Unique identifier for stroke.
 * @param points List of recorded points composing this stroke.
 * @param color Color of stroke rendering.
 * @param baseWidth Base stroke width configured at drawing time.
 * @param tool Active tool used for drawing this stroke.
 * @param alpha Transparency opacity value.
 */
data class DrawingStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val color: Color = Color.Black,
    val baseWidth: Float = 5.0f,
    val tool: CanvasTool = CanvasTool.PEN,
    val alpha: Float = if (tool == CanvasTool.HIGHLIGHTER) 0.4f else 1.0f
) {
    /**
     * Lazily calculated Bezier curve path for smooth rendering.
     */
    val path: Path by lazy {
        BezierSmoother.buildBezierPath(points)
    }

    /**
     * Axis-aligned bounding box [minX, minY, maxX, maxY] with padding.
     */
    val boundingBox: FloatArray by lazy {
        if (points.isEmpty()) return@lazy floatArrayOf(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        val padding = baseWidth * 2f
        floatArrayOf(minX - padding, minY - padding, maxX + padding, maxY + padding)
    }
}
