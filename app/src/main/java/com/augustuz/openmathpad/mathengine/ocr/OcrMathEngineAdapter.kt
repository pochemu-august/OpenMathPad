package com.augustuz.openmathpad.mathengine.ocr

import com.augustuz.openmathpad.mathengine.MathEngine
import com.augustuz.openmathpad.mathengine.model.EvaluationContext
import com.augustuz.openmathpad.mathengine.model.MathResult

data class OcrBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class OcrMathInput(
    val latexExpression: String,
    val confidenceScore: Float = 1.0f,
    val boundingBox: OcrBoundingBox? = null,
    val recognizedSymbols: List<String> = emptyList()
)

data class OcrMathResult(
    val rawLatex: String,
    val sanitizedExpression: String,
    val confidenceScore: Float,
    val mathResult: MathResult,
    val isReliableRecognition: Boolean
)

class OcrMathEngineAdapter(
    private val mathEngine: MathEngine = MathEngine(),
    private val minimumConfidenceThreshold: Float = 0.5f
) {

    fun processOcrOutput(
        ocrInput: OcrMathInput,
        context: EvaluationContext = EvaluationContext()
    ): OcrMathResult {
        val result = mathEngine.evaluate(ocrInput.latexExpression, context)
        val sanitized = mathEngine.sanitizeLatex(ocrInput.latexExpression)
        val isReliable = ocrInput.confidenceScore >= minimumConfidenceThreshold

        return OcrMathResult(
            rawLatex = ocrInput.latexExpression,
            sanitizedExpression = sanitized,
            confidenceScore = ocrInput.confidenceScore,
            mathResult = result,
            isReliableRecognition = isReliable
        )
    }
}
