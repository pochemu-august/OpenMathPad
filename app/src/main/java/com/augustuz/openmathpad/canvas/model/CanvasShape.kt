package com.augustuz.openmathpad.canvas.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Geometric shape type for first-class interactive geometric objects.
 */
enum class CanvasShapeType {
    VECTOR,
    BOX,
    CIRCLE,
    LINE
}

/**
 * Handle type for interactive shape transformations and resizing.
 */
enum class ShapeHandle {
    NONE,
    BODY,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    START_ENDPOINT,
    END_ENDPOINT
}

/**
 * First-class interactive geometric object on the infinite canvas.
 * Supports hit-testing, selection, movement, corner/endpoint handle resizing, and theme-adaptive coloring.
 */
data class CanvasShape(
    val id: String = UUID.randomUUID().toString(),
    val type: CanvasShapeType,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val color: Color = AdaptiveInkColor.Default,
    val strokeWidth: Float = 2.0f,
    val isSelected: Boolean = false
) {
    /**
     * Resolves color based on active canvas theme (Light vs Dark mode).
     */
    fun getEffectiveColor(isDark: Boolean): Color = AdaptiveInkColor.resolve(color, isDark)

    /**
     * Cached bounding rectangle in scene coordinates for O(1) viewport culling.
     */
    val bounds: Rect by lazy {
        when (type) {
            CanvasShapeType.BOX, CanvasShapeType.LINE, CanvasShapeType.VECTOR -> {
                val l = minOf(startX, endX)
                val t = minOf(startY, endY)
                val r = maxOf(startX, endX)
                val b = maxOf(startY, endY)
                val padding = strokeWidth * 2f + 20f
                Rect(l - padding, t - padding, r + padding, b + padding)
            }
            CanvasShapeType.CIRCLE -> {
                val r = radius
                val padding = strokeWidth * 2f + 8f
                Rect(startX - r - padding, startY - r - padding, startX + r + padding, startY + r + padding)
            }
        }
    }

    /**
     * Stationary center to edge radius for Circle shapes.
     */
    val radius: Float
        get() = hypot(endX - startX, endY - startY)

    val minX: Float get() = when (type) {
        CanvasShapeType.CIRCLE -> startX - radius
        else -> minOf(startX, endX)
    }

    val minY: Float get() = when (type) {
        CanvasShapeType.CIRCLE -> startY - radius
        else -> minOf(startY, endY)
    }

    val maxX: Float get() = when (type) {
        CanvasShapeType.CIRCLE -> startX + radius
        else -> maxOf(startX, endX)
    }

    val maxY: Float get() = when (type) {
        CanvasShapeType.CIRCLE -> startY + radius
        else -> maxOf(startY, endY)
    }

    val width: Float get() = max(1f, maxX - minX)
    val height: Float get() = max(1f, maxY - minY)

    /**
     * Hit tests a scene coordinate point against this shape.
     */
    fun hitTest(sceneX: Float, sceneY: Float, tolerance: Float = 24f): Boolean {
        return when (type) {
            CanvasShapeType.BOX -> {
                val l = minOf(startX, endX)
                val r = maxOf(startX, endX)
                val t = minOf(startY, endY)
                val b = maxOf(startY, endY)
                (sceneX in (l - tolerance)..(r + tolerance)) && (sceneY in (t - tolerance)..(b + tolerance))
            }
            CanvasShapeType.CIRCLE -> {
                val r = radius
                val d = hypot(sceneX - startX, sceneY - startY)
                d <= (r + tolerance)
            }
            CanvasShapeType.VECTOR, CanvasShapeType.LINE -> {
                distanceToSegment(sceneX, sceneY, startX, startY, endX, endY) <= tolerance
            }
        }
    }

    /**
     * Renders this geometric shape using Compose DrawScope.
     */
    fun draw(drawScope: DrawScope, isDark: Boolean) {
        val effectiveColor = getEffectiveColor(isDark)
        val style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = if (type == CanvasShapeType.BOX) StrokeJoin.Miter else StrokeJoin.Round
        )

        with(drawScope) {
            when (type) {
                CanvasShapeType.BOX -> {
                    val l = minOf(startX, endX)
                    val t = minOf(startY, endY)
                    val w = max(1f, maxOf(startX, endX) - l)
                    val h = max(1f, maxOf(startY, endY) - t)
                    drawRect(
                        color = effectiveColor,
                        topLeft = Offset(l, t),
                        size = Size(w, h),
                        style = style
                    )
                }
                CanvasShapeType.CIRCLE -> {
                    drawCircle(
                        color = effectiveColor,
                        radius = radius,
                        center = Offset(startX, startY),
                        style = style
                    )
                }
                CanvasShapeType.LINE -> {
                    drawLine(
                        color = effectiveColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                CanvasShapeType.VECTOR -> {
                    // 1. Draw shaft
                    drawLine(
                        color = effectiveColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    // 2. Draw sharp symmetric arrowhead wings
                    val angle = atan2(endY - startY, endX - startX)
                    val arrowLen = max(18f, strokeWidth * 8f)
                    val wingAngle = 2.61799f // 150 degrees backwards from heading
                    val w1X = endX + arrowLen * cos(angle + wingAngle)
                    val w1Y = endY + arrowLen * sin(angle + wingAngle)
                    val w2X = endX + arrowLen * cos(angle - wingAngle)
                    val w2Y = endY + arrowLen * sin(angle - wingAngle)

                    drawLine(
                        color = effectiveColor,
                        start = Offset(endX, endY),
                        end = Offset(w1X, w1Y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = effectiveColor,
                        start = Offset(endX, endY),
                        end = Offset(w2X, w2Y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }

    companion object {
        fun distanceToSegment(
            px: Float, py: Float,
            x1: Float, y1: Float,
            x2: Float, y2: Float
        ): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            if (dx == 0f && dy == 0f) return hypot(px - x1, py - y1)
            val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
            val projX = x1 + t * dx
            val projY = y1 + t * dy
            return hypot(px - projX, py - projY)
        }
    }
}
