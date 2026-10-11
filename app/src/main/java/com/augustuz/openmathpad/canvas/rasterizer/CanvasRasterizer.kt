package com.augustuz.openmathpad.canvas.rasterizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint

/**
 * Result container for canvas stroke rasterization.
 *
 * @param bitmap Rendered monochrome bitmap (black strokes on white background), or null if blank.
 * @param bounds Exact bounding box of stroke points with padding applied.
 * @param isBlank True if strokes are empty or contain no drawable points.
 * @param resultPlacementPoint Coordinates situated immediately to the right of expression bounds.
 * @param minX Raw padded minimum X.
 * @param minY Raw padded minimum Y.
 * @param maxX Raw padded maximum X.
 * @param maxY Raw padded maximum Y.
 * @param placementPointX Padded placement point X coordinate.
 * @param placementPointY Padded placement point Y coordinate.
 */
data class RasterizedStrokeResult(
    val bitmap: Bitmap?,
    val bounds: RectF,
    val isBlank: Boolean,
    val resultPlacementPoint: PointF,
    val minX: Float = 0f,
    val minY: Float = 0f,
    val maxX: Float = 0f,
    val maxY: Float = 0f,
    val placementPointX: Float = 0f,
    val placementPointY: Float = 0f
)

/**
 * Utility for rasterizing drawn vector canvas strokes into normalized bitmap tensors for OCR,
 * and computing bounding boxes & expression result placement points.
 */
class CanvasRasterizer(
    val targetWidth: Int = 512,
    val targetHeight: Int = 128,
    val paddingDp: Float = 16f,
    val resultMarginDp: Float = 24f,
    val bitmapMarginPx: Float = 16f,
    val strokeWidthPx: Float = 6f
) {

    /**
     * Computes bounding box and rasterizes [strokes] onto a normalized bitmap.
     *
     * Alias for [rasterize].
     */
    fun rasterizeStrokes(strokes: List<DrawingStroke>): RasterizedStrokeResult = rasterize(strokes)

    /**
     * Computes bounding box and rasterizes [strokes] onto a normalized bitmap.
     *
     * @param strokes List of drawn strokes from canvas.
     * @return [RasterizedStrokeResult] containing bounds, placement point, and rendered bitmap.
     */
    fun rasterize(strokes: List<DrawingStroke>): RasterizedStrokeResult {
        val activePoints = strokes.flatMap { it.points }
        if (activePoints.isEmpty()) {
            return RasterizedStrokeResult(
                bitmap = null,
                bounds = RectF(0f, 0f, 0f, 0f),
                isBlank = true,
                resultPlacementPoint = PointF(0f, 0f)
            )
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (p in activePoints) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }

        if (maxX <= minX || maxY <= minY) {
            return RasterizedStrokeResult(
                bitmap = null,
                bounds = RectF(0f, 0f, 0f, 0f),
                isBlank = true,
                resultPlacementPoint = PointF(0f, 0f)
            )
        }

        val paddedMinX = minX - paddingDp
        val paddedMinY = minY - paddingDp
        val paddedMaxX = maxX + paddingDp
        val paddedMaxY = maxY + paddingDp

        val rawWidth = paddedMaxX - paddedMinX
        val rawHeight = paddedMaxY - paddedMinY

        val resultPlacementX = paddedMaxX + resultMarginDp
        val resultPlacementY = paddedMinY + (rawHeight / 2f)

        val resultPlacementPoint = PointF(resultPlacementX, resultPlacementY)
        val bounds = RectF(paddedMinX, paddedMinY, paddedMaxX, paddedMaxY)

        if (rawWidth <= 0f || rawHeight <= 0f) {
            return RasterizedStrokeResult(
                bitmap = null,
                bounds = bounds,
                isBlank = true,
                resultPlacementPoint = resultPlacementPoint,
                minX = paddedMinX,
                minY = paddedMinY,
                maxX = paddedMaxX,
                maxY = paddedMaxY,
                placementPointX = resultPlacementX,
                placementPointY = resultPlacementY
            )
        }

        val bitmap: Bitmap? = try {
            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)

            val availWidth = maxOf(1f, targetWidth - (2 * bitmapMarginPx))
            val availHeight = maxOf(1f, targetHeight - (2 * bitmapMarginPx))

            val scale = minOf(
                if (rawWidth > 0f) availWidth / rawWidth else 1f,
                if (rawHeight > 0f) availHeight / rawHeight else 1f
            )

            val scaledWidth = rawWidth * scale
            val scaledHeight = rawHeight * scale

            val offsetX = bitmapMarginPx + (availWidth - scaledWidth) / 2f
            val offsetY = bitmapMarginPx + (availHeight - scaledHeight) / 2f

            val transformMatrix = Matrix().apply {
                postTranslate(-paddedMinX, -paddedMinY)
                postScale(scale, scale)
                postTranslate(offsetX, offsetY)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = strokeWidthPx
            }

            for (stroke in strokes) {
                if (stroke.points.isEmpty()) continue
                val originalPath = buildAndroidPath(stroke.points)
                val transformedPath = Path()
                originalPath.transform(transformMatrix, transformedPath)
                canvas.drawPath(transformedPath, paint)
            }
            bmp
        } catch (_: Throwable) {
            null
        }

        return RasterizedStrokeResult(
            bitmap = bitmap,
            bounds = bounds,
            isBlank = false,
            resultPlacementPoint = resultPlacementPoint,
            minX = paddedMinX,
            minY = paddedMinY,
            maxX = paddedMaxX,
            maxY = paddedMaxY,
            placementPointX = resultPlacementX,
            placementPointY = resultPlacementY
        )
    }

    private fun buildAndroidPath(points: List<StrokePoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path

        if (points.size == 1) {
            val p = points[0]
            val radius = maxOf(p.width / 2f, 2f)
            path.addOval(p.x - radius, p.y - radius, p.x + radius, p.y + radius, Path.Direction.CW)
            return path
        }

        val p0 = points[0]
        path.moveTo(p0.x, p0.y)

        if (points.size == 2) {
            val p1 = points[1]
            path.lineTo(p1.x, p1.y)
            return path
        }

        val firstMidX = (p0.x + points[1].x) / 2f
        val firstMidY = (p0.y + points[1].y) / 2f
        path.lineTo(firstMidX, firstMidY)

        for (i in 1 until points.size - 1) {
            val pCurr = points[i]
            val pNext = points[i + 1]
            val midX = (pCurr.x + pNext.x) / 2f
            val midY = (pCurr.y + pNext.y) / 2f
            path.quadTo(pCurr.x, pCurr.y, midX, midY)
        }

        val lastPoint = points.last()
        path.lineTo(lastPoint.x, lastPoint.y)

        return path
    }
}
