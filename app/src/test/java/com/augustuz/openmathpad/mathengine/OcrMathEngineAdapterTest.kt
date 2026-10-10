package com.augustuz.openmathpad.mathengine

import com.augustuz.openmathpad.mathengine.model.EvaluationContext
import com.augustuz.openmathpad.mathengine.model.MathResult
import com.augustuz.openmathpad.mathengine.ocr.OcrBoundingBox
import com.augustuz.openmathpad.mathengine.ocr.OcrMathEngineAdapter
import com.augustuz.openmathpad.mathengine.ocr.OcrMathInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OcrMathEngineAdapterTest {

    private lateinit var adapter: OcrMathEngineAdapter

    @Before
    fun setUp() {
        adapter = OcrMathEngineAdapter()
    }

    @Test
    fun testProcessOcrInputSuccess() {
        val input = OcrMathInput(
            latexExpression = "\\frac{a + b}{2}",
            confidenceScore = 0.95f,
            boundingBox = OcrBoundingBox(10f, 20f, 100f, 50f),
            recognizedSymbols = listOf("\\frac", "a", "+", "b", "2")
        )

        val context = EvaluationContext(variables = mapOf("a" to 10.0, "b" to 20.0))
        val ocrResult = adapter.processOcrOutput(input, context)

        assertTrue(ocrResult.isReliableRecognition)
        assertEquals("\\frac{a + b}{2}", ocrResult.rawLatex)
        assertEquals("((a + b)/(2))", ocrResult.sanitizedExpression)

        val mathResult = ocrResult.mathResult as MathResult.Success
        assertEquals(15.0, mathResult.value, 1e-9)
        assertEquals("15", mathResult.formattedResult)
    }

    @Test
    fun testLowConfidenceThreshold() {
        val input = OcrMathInput(
            latexExpression = "2 + 2",
            confidenceScore = 0.30f
        )

        val ocrResult = adapter.processOcrOutput(input)
        assertTrue(!ocrResult.isReliableRecognition)
    }
}
