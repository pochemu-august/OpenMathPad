package com.augustuz.openmathpad.canvas.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.augustuz.openmathpad.canvas.processing.BezierSmoother
import java.util.UUID

/**
 * Represents a complete drawn stroke on the infinite canvas.
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
    val baseWidth: Float = 2.0f,
    val tool: CanvasTool = CanvasTool.PEN,
    val alpha: Float = if (tool == CanvasTool.HIGHLIGHTER) 0.5f else 1.0f
) {
    /**
     * Lazily calculated smooth curve path for rendering via Midpoint Quadratic Bezier interpolation.
     * Cached once per stroke data model instance to prevent path rebuilding during pan and zoom.
     */
    val path: Path by lazy {
        BezierSmoother.buildBezierPath(points)
    }

    /**
     * Crisp, uniform stroke width for ballpoint/gel pen inking without dynamic pressure variance.
     */
    val strokeWidth: Float
        get() = baseWidth

    /**
     * Lazily cached Compose DrawScope Stroke style to eliminate allocation during 144Hz redraw loops.
     */
    val strokeStyle: Stroke by lazy {
        Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    /**
     * Uniform width check for ballpoint pen strokes.
     */
    val isUniformWidth: Boolean = true

    /**
     * Axis-aligned bounding box [minX, minY, maxX, maxY] with padding.
     */
    val boundingBox: FloatArray by lazy {
        if (points.isEmpty()) return@lazy floatArrayOf(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (i in points.indices) {
            val p = points[i]
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        val padding = strokeWidth * 2f
        floatArrayOf(minX - padding, minY - padding, maxX + padding, maxY + padding)
    }
}
