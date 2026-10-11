package com.augustuz.openmathpad.canvas.model

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.augustuz.openmathpad.canvas.processing.BezierSmoother
import java.util.UUID
import kotlin.math.hypot

/**
 * Represents a complete drawn stroke or analytic geometric shape on the infinite canvas.
 *
 * @param id Unique identifier for stroke.
 * @param points List of recorded points composing this stroke.
 * @param color Color of stroke rendering.
 * @param baseWidth Base stroke width configured at drawing time.
 * @param tool Active tool used for drawing this stroke.
 * @param shape Geometric shape mode if this stroke is a shape (bypasses Bezier smoothing).
 * @param alpha Transparency opacity value.
 */
data class DrawingStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val color: Color = AdaptiveInkColor.Default,
    val baseWidth: Float = 1.5f,
    val tool: CanvasTool = CanvasTool.PEN,
    val shape: GeometricShape = GeometricShape.NONE,
    val alpha: Float = if (tool == CanvasTool.HIGHLIGHTER) 0.5f else 1.0f
) {
    /**
     * Resolves stroke color dynamically based on theme (Light vs Dark mode).
     */
    fun getEffectiveColor(isDark: Boolean): Color {
        return if (tool == CanvasTool.HIGHLIGHTER) {
            color
        } else {
            AdaptiveInkColor.resolve(color, isDark)
        }
    }

    /**
     * Pre-computed Compose Rect bounding box in scene coordinates for O(1) viewport culling.
     */
    val bounds: Rect by lazy {
        if (points.isEmpty()) return@lazy Rect.Zero
        val padding = strokeWidth * 2f
        if (shape == GeometricShape.CIRCLE && points.size >= 2) {
            val center = points[0]
            val edge = points[1]
            val radius = hypot(edge.x - center.x, edge.y - center.y)
            return@lazy Rect(
                center.x - radius - padding,
                center.y - radius - padding,
                center.x + radius + padding,
                center.y + radius + padding
            )
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in points.indices) {
            val p = points[i]
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        Rect(minX - padding, minY - padding, maxX + padding, maxY + padding)
    }

    /**
     * Lazily calculated curve or analytic geometric path.
     * Geometric shapes strictly bypass Bezier smoothing to guarantee 90-degree box corners and sharp arrowheads.
     */
    val path: Path by lazy {
        when (shape) {
            GeometricShape.RECTANGLE -> {
                val p = Path()
                if (points.isNotEmpty()) {
                    val minX = points.minOf { it.x }
                    val maxX = points.maxOf { it.x }
                    val minY = points.minOf { it.y }
                    val maxY = points.maxOf { it.y }
                    p.moveTo(minX, minY)
                    p.lineTo(maxX, minY)
                    p.lineTo(maxX, maxY)
                    p.lineTo(minX, maxY)
                    p.close()
                }
                p
            }
            GeometricShape.VECTOR_ARROW -> {
                val p = Path()
                if (points.size >= 5) {
                    // Straight stem: start -> end
                    p.moveTo(points[0].x, points[0].y)
                    p.lineTo(points[1].x, points[1].y)
                    // Sharp symmetric wings: wing1 -> end -> wing2
                    p.moveTo(points[2].x, points[2].y)
                    p.lineTo(points[1].x, points[1].y)
                    p.lineTo(points[4].x, points[4].y)
                } else if (points.size >= 2) {
                    p.moveTo(points.first().x, points.first().y)
                    p.lineTo(points.last().x, points.last().y)
                }
                p
            }
            GeometricShape.LINE -> {
                val p = Path()
                if (points.isNotEmpty()) {
                    p.moveTo(points.first().x, points.first().y)
                    p.lineTo(points.last().x, points.last().y)
                }
                p
            }
            GeometricShape.CIRCLE -> {
                val p = Path()
                if (points.size >= 2) {
                    val p0 = points[0]
                    val p1 = points[1]
                    val radius = hypot(p1.x - p0.x, p1.y - p0.y)
                    p.addOval(
                        Rect(
                            p0.x - radius,
                            p0.y - radius,
                            p0.x + radius,
                            p0.y + radius
                        )
                    )
                }
                p
            }
            GeometricShape.NONE -> {
                BezierSmoother.buildBezierPath(points)
            }
        }
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
            join = if (shape == GeometricShape.RECTANGLE) StrokeJoin.Miter else StrokeJoin.Round
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
