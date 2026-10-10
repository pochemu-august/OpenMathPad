package com.augustuz.openmathpad.canvas.processing

import android.view.MotionEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.augustuz.openmathpad.canvas.model.StrokePoint

/**
 * MotionEvent historical point extractor and Midpoint Quadratic Bezier path generator.
 */
object BezierSmoother {

    /**
     * Extracts all historical and current points from a MotionEvent.
     *
     * @param event Native MotionEvent.
     * @param baseWidth Active base stroke width.
     * @param pressureSmoother PressureSmoother filter instance.
     * @return Chronological list of extracted StrokePoints.
     */
    fun extractPointsFromEvent(
        event: MotionEvent,
        baseWidth: Float,
        pressureSmoother: PressureSmoother
    ): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        val toolType = if (event.pointerCount > 0) event.getToolType(0) else MotionEvent.TOOL_TYPE_UNKNOWN
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

        // 1. Process historical points
        val historySize = event.historySize
        for (h in 0 until historySize) {
            val hX = event.getHistoricalX(h)
            val hY = event.getHistoricalY(h)
            val hPressure = event.getHistoricalPressure(h)
            val hTime = event.getHistoricalEventTime(h)

            val (smoothedPressure, width) = pressureSmoother.calculateWidth(hPressure, baseWidth, isStylus)
            points.add(StrokePoint(hX, hY, smoothedPressure, width, hTime))
        }

        // 2. Process current event point
        val cX = event.x
        val cY = event.y
        val cPressure = event.pressure
        val cTime = event.eventTime

        val (smoothedPressure, width) = pressureSmoother.calculateWidth(cPressure, baseWidth, isStylus)
        points.add(StrokePoint(cX, cY, smoothedPressure, width, cTime))

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
