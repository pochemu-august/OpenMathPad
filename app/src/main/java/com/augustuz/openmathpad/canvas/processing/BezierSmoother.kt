package com.augustuz.openmathpad.canvas.processing

import android.view.MotionEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.augustuz.openmathpad.canvas.model.StrokePoint
import kotlin.math.hypot

/**
 * MotionEvent historical point extractor with minimum distance thresholding
 * and Midpoint Quadratic Bezier path generator.
 */
object BezierSmoother {

    private const val MIN_DISTANCE_SCREEN_PX = 1.5f

    /**
     * Extracts all historical and current points from a MotionEvent, discarding
     * micro-movement jitter points closer than [MIN_DISTANCE_SCREEN_PX] to eliminate loops/blobs,
     * while preserving 240-360Hz high-precision stylus sampling on Xiaomi Pad 7 Pro.
     *
     * @param event Native MotionEvent.
     * @param baseWidth Active base stroke width.
     * @param pressureSmoother PressureSmoother filter instance.
     * @return Chronological list of extracted StrokePoints.
     */
    fun extractPointsFromEvent(
        event: MotionEvent,
        baseWidth: Float,
        pressureSmoother: PressureSmoother,
        pointerIndex: Int = 0,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f,
        zoomScale: Float = 1.0f,
        strokeStabilizer: StrokeStabilizer? = null
    ): List<StrokePoint> {
        val historySize = event.historySize
        val points = ArrayList<StrokePoint>(historySize + 1)
        val pIdx = if (event.pointerCount > 0) pointerIndex.coerceIn(0, event.pointerCount - 1) else 0
        val toolType = if (event.pointerCount > 0) event.getToolType(pIdx) else MotionEvent.TOOL_TYPE_UNKNOWN
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val scale = if (zoomScale <= 0f) 1.0f else zoomScale
        val minDistanceScene = MIN_DISTANCE_SCREEN_PX / scale

        var lastX = Float.NaN
        var lastY = Float.NaN

        // 1. Process all historical points for high-precision 240-360Hz sampling
        for (h in 0 until historySize) {
            val hX = event.getHistoricalX(pIdx, h)
            val hY = event.getHistoricalY(pIdx, h)
            val sceneHX = (hX - panOffsetX) / scale
            val sceneHY = (hY - panOffsetY) / scale

            if (!lastX.isNaN() && hypot(sceneHX - lastX, sceneHY - lastY) < minDistanceScene) {
                continue
            }
            lastX = sceneHX
            lastY = sceneHY

            val hPressure = event.getHistoricalPressure(pIdx, h)
            val hTime = event.getHistoricalEventTime(h)

            pressureSmoother.update(hPressure, baseWidth, isStylus)
            val (stX, stY, stP) = if (strokeStabilizer != null) {
                strokeStabilizer.filter(sceneHX, sceneHY, pressureSmoother.currentSmoothedPressure)
            } else {
                Triple(sceneHX, sceneHY, pressureSmoother.currentSmoothedPressure)
            }
            points.add(StrokePoint(stX, stY, stP, pressureSmoother.currentCalculatedWidth, hTime))
        }

        // 2. Process current event point
        val cX = event.getX(pIdx)
        val cY = event.getY(pIdx)
        val sceneCX = (cX - panOffsetX) / scale
        val sceneCY = (cY - panOffsetY) / scale

        if (lastX.isNaN() || hypot(sceneCX - lastX, sceneCY - lastY) >= minDistanceScene) {
            val cPressure = event.getPressure(pIdx)
            val cTime = event.eventTime

            pressureSmoother.update(cPressure, baseWidth, isStylus)
            val (stX, stY, stP) = if (strokeStabilizer != null) {
                strokeStabilizer.filter(sceneCX, sceneCY, pressureSmoother.currentSmoothedPressure)
            } else {
                Triple(sceneCX, sceneCY, pressureSmoother.currentSmoothedPressure)
            }
            points.add(StrokePoint(stX, stY, stP, pressureSmoother.currentCalculatedWidth, cTime))
        }

        return points
    }

    /**
     * Filters micro-jitter landing artifacts (backward hooks/flickers) at the beginning of fast strokes.
     * At stroke landing (first 3-4 points), checks for sudden direction reversal (> 110-degree sharp angle
     * within [maxDistancePx] of ACTION_DOWN). If detected, snaps origin directly to dominant trajectory
     * and discards the landing jitter point.
     */
    fun filterLandingJitter(
        points: List<StrokePoint>,
        maxDistancePx: Float = 16f
    ): List<StrokePoint> {
        if (points.size < 3) return points

        val p0 = points[0]
        val p1 = points[1]
        val p2 = points[2]

        val d01 = hypot(p1.x - p0.x, p1.y - p0.y)
        if (d01 > maxDistancePx || d01 < 0.01f) {
            return points
        }

        val v1x = p1.x - p0.x
        val v1y = p1.y - p0.y

        val (vDomX, vDomY) = if (points.size >= 4) {
            val p3 = points[3]
            Pair(p3.x - p1.x, p3.y - p1.y)
        } else {
            Pair(p2.x - p1.x, p2.y - p1.y)
        }

        val magDom = hypot(vDomX, vDomY)
        if (magDom < 0.01f) return points

        val dot = v1x * vDomX + v1y * vDomY
        val cosAngle = dot / (d01 * magDom)

        // cos(110°) ≈ -0.342020f. Reversal detected if cosAngle < -0.342020f
        if (cosAngle < -0.342020f) {
            val uDomX = vDomX / magDom
            val uDomY = vDomY / magDom

            val t = (p0.x - p2.x) * uDomX + (p0.y - p2.y) * uDomY
            val snappedP0 = p0.copy(
                x = p2.x + t * uDomX,
                y = p2.y + t * uDomY
            )

            val result = ArrayList<StrokePoint>(points.size - 1)
            result.add(snappedP0)
            for (i in 2 until points.size) {
                result.add(points[i])
            }
            return result
        }

        return points
    }

    /**
     * Constructs a smooth Compose Path using Midpoint Quadratic Bezier interpolation with zero-latency start.
     * Draws direct linear segments for the first 2 points, only blending into smooth midpoint Beziers
     * once a stable velocity vector is established.
     *
     * @param points List of recorded StrokePoints.
     * @return Smoothed Compose Path object.
     */
    fun buildBezierPath(points: List<StrokePoint>): Path {
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
            val p1 = points[1]
            path.lineTo(p1.x, p1.y)
            return path
        }

        // Zero-latency start: Direct linear segment connecting the first 2 points (P0 -> P1)
        val p1 = points[1]
        path.lineTo(p1.x, p1.y)

        if (points.size == 3) {
            val p2 = points[2]
            path.lineTo(p2.x, p2.y)
            return path
        }

        // Blend into smooth midpoint Beziers from P1 onwards
        val firstMidX = (p1.x + points[2].x) / 2f
        val firstMidY = (p1.y + points[2].y) / 2f
        path.lineTo(firstMidX, firstMidY)

        for (i in 2 until points.size - 1) {
            val pCurr = points[i]
            val pNext = points[i + 1]
            val midX = (pCurr.x + pNext.x) / 2f
            val midY = (pCurr.y + pNext.y) / 2f
            path.quadraticTo(pCurr.x, pCurr.y, midX, midY)
        }

        val lastPoint = points.last()
        path.lineTo(lastPoint.x, lastPoint.y)

        return path
    }
}
