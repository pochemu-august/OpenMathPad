package com.augustuz.openmathpad.mathengine

import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.model.StrokePoint
import com.augustuz.openmathpad.mathengine.model.MathResult
import com.augustuz.openmathpad.mathengine.ocr.OnnxMathOcrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnnxMathOcrEngineTest {

    private lateinit var engine: OnnxMathOcrEngine

    @Before
    fun setUp() {
        engine = OnnxMathOcrEngine()
    }

    @Test
    fun engine_uninitialized_defaultsToHeuristicMode() {
        assertTrue("When no model file/asset is provided, engine must default to heuristic mode", engine.isHeuristicMode)
    }

    @Test
    fun recognizeStrokes_emptyStrokes_returnsEmptyResult() {
        val result = engine.recognizeStrokes(emptyList())

        assertEquals("", result.latexExpression)
        assertEquals(1.0f, result.confidenceScore, 0.001f)
        assertTrue(result.isHeuristicFallback)
    }

    @Test
    fun recognizeStrokes_withSingleStroke_returnsHeuristicLatexAndBounds() {
        val stroke = DrawingStroke(
            points = listOf(
                StrokePoint(10f, 10f),
                StrokePoint(50f, 50f)
            )
        )

        val result = engine.recognizeStrokes(listOf(stroke))

        assertTrue(result.isHeuristicFallback)
        assertEquals("sqrt(16) + 3^2", result.latexExpression)
        assertNotNull(result.boundingBox)
        assertEquals(-6f, result.boundingBox?.top)
    }

    @Test
    fun recognizeStrokes_heuristicFallbackExecution_mapsStrokeCountsToExpressions() {
        val dummyPoint = listOf(StrokePoint(0f, 0f), StrokePoint(10f, 10f))
        val stroke1 = DrawingStroke(points = dummyPoint)
        val stroke2 = DrawingStroke(points = dummyPoint)
        val stroke3 = DrawingStroke(points = dummyPoint)
        val stroke4 = DrawingStroke(points = dummyPoint)
        val stroke5 = DrawingStroke(points = dummyPoint)

        val res1 = engine.recognizeStrokes(listOf(stroke1))
        assertEquals("sqrt(16) + 3^2", res1.latexExpression)

        val res2 = engine.recognizeStrokes(listOf(stroke1, stroke2))
        assertEquals("2 * pi * 5", res2.latexExpression)

        val res3 = engine.recognizeStrokes(listOf(stroke1, stroke2, stroke3))
        assertEquals("sin(pi / 6) + cos(pi / 3)", res3.latexExpression)

        val res4 = engine.recognizeStrokes(listOf(stroke1, stroke2, stroke3, stroke4))
        assertEquals("x^2 + 3*x - 4", res4.latexExpression)

        val res5 = engine.recognizeStrokes(listOf(stroke1, stroke2, stroke3, stroke4, stroke5))
        assertEquals("integrate(x^2, 0, 5)", res5.latexExpression)
    }

    @Test
    fun processStrokesAndEvaluate_evaluatesLatexResult() {
        val stroke1 = DrawingStroke(points = listOf(StrokePoint(0f, 0f), StrokePoint(10f, 10f)))
        val stroke2 = DrawingStroke(points = listOf(StrokePoint(20f, 20f), StrokePoint(30f, 30f)))

        val ocrMathResult = engine.processStrokesAndEvaluate(listOf(stroke1, stroke2))

        assertEquals("2 * pi * 5", ocrMathResult.rawLatex)
        assertTrue(ocrMathResult.isReliableRecognition)
        assertTrue("Evaluation must succeed", ocrMathResult.mathResult is MathResult.Success)

        val success = ocrMathResult.mathResult as MathResult.Success
        assertEquals(31.41592653589793, success.value, 1e-4)
    }

    @Test
    fun initializeSession_invalidModelBytes_gracefullyFallsBackToHeuristic() {
        val invalidBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
        val success = engine.initializeSession(invalidBytes)

        assertFalse("Initialization with garbage bytes should return false", success)
        assertTrue("Engine must stay in heuristic mode after failed initialization", engine.isHeuristicMode)
    }
}
