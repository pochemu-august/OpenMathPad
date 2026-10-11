package com.augustuz.openmathpad.canvas.processing

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance Stroke Beautification & Smoothing Engine for handwriting.
 *
 * Implements:
 * 1. Jitter Reduction via Real-Time Stabilizer, Moving-Average, and RDP.
 * 2. Chaikin's Corner-Cutting algorithm and Catmull-Rom to Cubic Bezier curve smoothing.
 * 3. Dynamic Calligraphic Width & Tapering ($width = baseWidth \times (pressure \times k_p + \frac{k_v}{velocity})$).
 */
object StrokeBeautifier {

    /**
     * Executes the complete stroke beautification pipeline on raw input points:
     * - Moving-average & stabilizer tremor filtering
     * - RDP spike & redundancy elimination
     * - Chaikin corner-cutting curve smoothing
     * - Dynamic velocity/pressure thickness interpolation & end tapering
     */
    fun beautifyPoints(
        points: List<StrokePoint>,
        baseWidth: Float,
        rdpEpsilon: Float = 1.0f,
        applyChaikin: Boolean = true
    ): List<StrokePoint> {
        if (points.size <= 2) return points

        // 1. Jitter reduction: moving-average smoothing for high-frequency tremors
        val maFiltered = applyMovingAverage(points)

        // 2. Jitter reduction: RDP algorithm to prune redundant collinear jitter & spikes
        val rdpSimplified = simplifyRdp(maFiltered, epsilon = rdpEpsilon)

        // 3. Chaikin corner-cutting smoothing to organically soften angles
        val smoothed = if (applyChaikin && rdpSimplified.size >= 3) {
            applyChaikinSmoothing(rdpSimplified, iterations = 1)
        } else {
            rdpSimplified
        }

        // 4. Dynamic calligraphic pressure & velocity tapering across the stroke
        return applyDynamicPressureAndTapering(smoothed, baseWidth)
    }

    /**
     * Beautifies an entire [DrawingStroke] and returns an updated copy.
     */
    fun beautifyStroke(
        stroke: DrawingStroke,
        rdpEpsilon: Float = 1.0f
    ): DrawingStroke {
        if (stroke.points.size <= 2) return stroke
        val beautifiedPoints = beautifyPoints(stroke.points, stroke.baseWidth, rdpEpsilon)
        return stroke.copy(points = beautifiedPoints)
    }

    /**
     * Applies a 3-point Gaussian-weighted moving average filter:
     * P'_i = 0.25 * P_{i-1} + 0.5 * P_i + 0.25 * P_{i+1}
     *
     * Keeps the initial and final anchor points unchanged.
     */
    fun applyMovingAverage(points: List<StrokePoint>): List<StrokePoint> {
        val size = points.size
        if (size <= 2) return points

        val result = ArrayList<StrokePoint>(size)
        result.add(points[0])

        for (i in 1 until size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            val smoothedX = 0.25f * prev.x + 0.50f * curr.x + 0.25f * next.x
            val smoothedY = 0.25f * prev.y + 0.50f * curr.y + 0.25f * next.y
            val smoothedPressure = 0.25f * prev.pressure + 0.50f * curr.pressure + 0.25f * next.pressure

            result.add(
                curr.copy(
                    x = smoothedX,
                    y = smoothedY,
                    pressure = smoothedPressure
                )
            )
        }

        result.add(points[size - 1])
        return result
    }

    /**
     * Chaikin's Corner-Cutting algorithm for organic curve smoothing:
     * Subdivides each line segment P_i -> P_{i+1} at 25% (Q) and 75% (R).
     */
    fun applyChaikinSmoothing(points: List<StrokePoint>, iterations: Int = 1): List<StrokePoint> {
        if (points.size <= 2 || iterations <= 0) return points

        var current = points
        repeat(iterations) {
            val count = current.size
            val nextPoints = ArrayList<StrokePoint>(count * 2)
            nextPoints.add(current.first())

            for (i in 0 until count - 1) {
                val p0 = current[i]
                val p1 = current[i + 1]

                // Q = 0.75 * p0 + 0.25 * p1
                val qX = 0.75f * p0.x + 0.25f * p1.x
                val qY = 0.75f * p0.y + 0.25f * p1.y
                val qPressure = 0.75f * p0.pressure + 0.25f * p1.pressure
                val qTime = (0.75f * p0.timestamp + 0.25f * p1.timestamp).toLong()

                // R = 0.25 * p0 + 0.75 * p1
                val rX = 0.25f * p0.x + 0.75f * p1.x
                val rY = 0.25f * p0.y + 0.75f * p1.y
                val rPressure = 0.25f * p0.pressure + 0.75f * p1.pressure
                val rTime = (0.25f * p0.timestamp + 0.75f * p1.timestamp).toLong()

                nextPoints.add(StrokePoint(qX, qY, qPressure, p0.width, qTime))
                nextPoints.add(StrokePoint(rX, rY, rPressure, p1.width, rTime))
            }

            nextPoints.add(current.last())
            current = nextPoints
        }
        return current
    }

    /**
     * Ramer-Douglas-Peucker (RDP) algorithm to prune noisy collinear points
     * while preserving high-curvature corners (such as math symbols: +, -, =, sqrt).
     */
    fun simplifyRdp(points: List<StrokePoint>, epsilon: Float = 1.0f): List<StrokePoint> {
        if (points.size <= 2 || epsilon <= 0f) return points

        var maxDistance = 0f
        var index = 0

        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val distance = perpendicularDistance(points[i], first, last)
            if (distance > maxDistance) {
                maxDistance = distance
                index = i
            }
        }

        return if (maxDistance > epsilon) {
            val left = simplifyRdp(points.subList(0, index + 1), epsilon)
            val right = simplifyRdp(points.subList(index, points.size), epsilon)
            val combined = ArrayList<StrokePoint>(left.size + right.size - 1)
            combined.addAll(left.subList(0, left.size - 1))
            combined.addAll(right)
            combined
        } else {
            listOf(first, last)
        }
    }

    /**
     * Calculates perpendicular distance from [point] to line segment ([lineStart], [lineEnd]).
     */
    fun perpendicularDistance(
        point: StrokePoint,
        lineStart: StrokePoint,
        lineEnd: StrokePoint
    ): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y

        val segmentLength = hypot(dx, dy)
        if (segmentLength == 0f) {
            return hypot(point.x - lineStart.x, point.y - lineStart.y)
        }

        val numerator = abs(dy * point.x - dx * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        return numerator / segmentLength
    }

    /**
     * Dynamic line thickness calculation based on stylus pressure AND velocity:
     * width = baseWidth * (pressure * k_p + k_v / velocity)
     * with start and end tapering for calligraphic elegance.
     */
    fun applyDynamicPressureAndTapering(
        points: List<StrokePoint>,
        baseWidth: Float,
        kp: Float = 0.70f,
        kv: Float = 0.35f
    ): List<StrokePoint> {
        val size = points.size
        if (size == 0) return points
        if (size == 1) {
            val p = points[0]
            val w = max(1.0f, baseWidth * p.pressure.coerceIn(0.4f, 1.6f))
            return listOf(p.copy(width = w))
        }

        val rawWidths = FloatArray(size)

        for (i in 0 until size) {
            val curr = points[i]
            val velocity = if (i > 0) {
                val prev = points[i - 1]
                val dist = hypot(curr.x - prev.x, curr.y - prev.y)
                val dt = max(1L, curr.timestamp - prev.timestamp)
                dist / dt.toFloat() // pixels per millisecond
            } else 0.5f

            // Velocity factor: kv / velocity
            val safeVelocity = max(0.12f, velocity)
            val velocityFactor = kv / safeVelocity

            // Pressure factor: pressure * kp
            val effectivePressure = if (curr.pressure <= 0.01f) 0.5f else curr.pressure.coerceIn(0.15f, 1.5f)
            val pressureFactor = effectivePressure * kp

            // Combined scale clamped to realistic calligraphic nib range
            val dynamicScale = (pressureFactor + velocityFactor).coerceIn(0.35f, 2.2f)

            // Start & End Tapering: smoothly ramp width up at start and down at tip lift
            val startTaperCount = min(4, size / 3)
            val endTaperCount = min(5, size / 3)

            val startTaper = if (startTaperCount > 0 && i < startTaperCount) {
                0.35f + 0.65f * ((i + 1).toFloat() / (startTaperCount + 1))
            } else 1.0f

            val endTaper = if (endTaperCount > 0 && i >= size - endTaperCount) {
                0.25f + 0.75f * ((size - i).toFloat() / (endTaperCount + 1))
            } else 1.0f

            val taperFactor = min(startTaper, endTaper)
            val calculatedWidth = max(1.0f, baseWidth * dynamicScale * taperFactor)
            rawWidths[i] = calculatedWidth
        }

        // Apply moving-average smoothing to widths to guarantee zero abrupt thickness stepping
        val smoothedWidths = FloatArray(size)
        smoothedWidths[0] = rawWidths[0]
        smoothedWidths[size - 1] = rawWidths[size - 1]
        for (i in 1 until size - 1) {
            smoothedWidths[i] = 0.25f * rawWidths[i - 1] + 0.50f * rawWidths[i] + 0.25f * rawWidths[i + 1]
        }

        val result = ArrayList<StrokePoint>(size)
        for (i in 0 until size) {
            result.add(points[i].copy(width = smoothedWidths[i]))
        }
        return result
    }

    /**
     * Converts points into a smooth Compose [Path] via Catmull-Rom splines converted
     * into cubic Bezier segments, ensuring smooth C1 tangent continuity.
     */
    fun buildSmoothPath(points: List<StrokePoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path

        if (points.size == 1) {
            val p = points[0]
            val radius = p.width / 2f
            path.addOval(
                Rect(
                    p.x - radius,
                    p.y - radius,
                    p.x + radius,
                    p.y + radius
                )
            )
            return path
        }

        val p0 = points[0]
        path.moveTo(p0.x, p0.y)

        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
            return path
        }

        val count = points.size
        for (i in 0 until count - 1) {
            val pCurrent = points[i]
            val pNext = points[i + 1]

            val pPrev = if (i > 0) points[i - 1] else {
                StrokePoint(
                    x = 2 * pCurrent.x - pNext.x,
                    y = 2 * pCurrent.y - pNext.y
                )
            }

            val pNextNext = if (i + 2 < count) points[i + 2] else {
                StrokePoint(
                    x = 2 * pNext.x - pCurrent.x,
                    y = 2 * pNext.y - pCurrent.y
                )
            }

            // Catmull-Rom to Cubic Bezier control points conversion
            val c1x = pCurrent.x + (pNext.x - pPrev.x) / 6f
            val c1y = pCurrent.y + (pNext.y - pPrev.y) / 6f

            val c2x = pNext.x - (pNextNext.x - pCurrent.x) / 6f
            val c2y = pNext.y - (pNextNext.y - pCurrent.y) / 6f

            path.cubicTo(c1x, c1y, c2x, c2y, pNext.x, pNext.y)
        }

        return path
    }
}
