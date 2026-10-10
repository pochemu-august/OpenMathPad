package com.augustuz.openmathpad.canvas

import androidx.compose.ui.graphics.Color
import com.augustuz.openmathpad.canvas.model.CanvasTool
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.canvas.processing.StrokeEraser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeEraserTest {

    @Test
    fun strokeIntersectsPoint_detectsTouchWithinRadius() {
        val stroke = DrawingStroke(
            points = listOf(
                StrokePoint(x = 10f, y = 10f),
                StrokePoint(x = 100f, y = 10f)
            ),
            color = Color.Black,
            baseWidth = 4f,
            tool = CanvasTool.PEN
        )

        // Point directly on segment (50, 10)
        val intersectsOnLine = StrokeEraser.strokeIntersectsPoint(stroke, x = 50f, y = 10f, eraserRadius = 20f)
        assertTrue("Point on stroke line segment must intersect", intersectsOnLine)

        // Point near segment (50, 20) within 20px threshold
        val intersectsNear = StrokeEraser.strokeIntersectsPoint(stroke, x = 50f, y = 20f, eraserRadius = 20f)
        assertTrue("Point near stroke segment must intersect", intersectsNear)

        // Point far away (50, 100)
        val intersectsFar = StrokeEraser.strokeIntersectsPoint(stroke, x = 50f, y = 100f, eraserRadius = 20f)
        assertFalse("Point far from stroke segment must not intersect", intersectsFar)
    }

    @Test
    fun findStrokesIntersecting_returnsIntersectedStrokesOnly() {
        val stroke1 = DrawingStroke(
            points = listOf(StrokePoint(0f, 0f), StrokePoint(50f, 0f)),
            baseWidth = 5f
        )
        val stroke2 = DrawingStroke(
            points = listOf(StrokePoint(200f, 200f), StrokePoint(250f, 200f)),
            baseWidth = 5f
        )

        val strokes = listOf(stroke1, stroke2)
        val erased = StrokeEraser.findStrokesIntersecting(strokes, x = 25f, y = 2f, eraserRadius = 15f)

        assertEquals(1, erased.size)
        assertEquals(stroke1.id, erased[0].id)
    }
}
