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

    private const val MIN_DISTANCE_SCREEN_PX = 3.0f

    /**
     * Extracts all historical and current points from a MotionEvent, discarding
     * micro-movement jitter points closer than [MIN_DISTANCE_SCREEN_PX] to eliminate loops/blobs.
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

        // 1. Process historical points with distance threshold
        for (h in 0 until historySize) {
            val hX = if (event.pointerCount > 1) event.getHistoricalX(pIdx, h) else event.getHistoricalX(h)
            val hY = if (event.pointerCount > 1) event.getHistoricalY(pIdx, h) else event.getHistoricalY(h)
            val sceneHX = (hX - panOffsetX) / scale
            val sceneHY = (hY - panOffsetY) / scale

            if (!lastX.isNaN() && hypot(sceneHX - lastX, sceneHY - lastY) < minDistanceScene) {
                continue
            }
            lastX = sceneHX
            lastY = sceneHY

            val hPressure = if (event.pointerCount > 1) event.getHistoricalPressure(pIdx, h) else event.getHistoricalPressure(h)
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
     * Constructs a smooth Compose Path using Midpoint Quadratic Bezier interpolation.
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

        // Initial line segment to midpoint of P0 and P1
        val firstMidX = (p0.x + points[1].x) / 2f
        val firstMidY = (p0.y + points[1].y) / 2f
        path.lineTo(firstMidX, firstMidY)

        // Midpoint Quadratic Bezier curves for subsequent points
        for (i in 1 until points.size - 1) {
            val pCurr = points[i]
            val pNext = points[i + 1]
            val midX = (pCurr.x + pNext.x) / 2f
            val midY = (pCurr.y + pNext.y) / 2f

            path.quadraticTo(pCurr.x, pCurr.y, midX, midY)
        }

        // Final line segment to last point
        val lastPoint = points.last()
        path.lineTo(lastPoint.x, lastPoint.y)

        return path
    }
}
