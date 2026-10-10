package com.augustuz.openmathpad.mathengine.model

enum class MathErrorType {
    SYNTAX_ERROR,
    UNMATCHED_PARENTHESES,
    UNKNOWN_IDENTIFIER,
    UNDEFINED_VARIABLE,
    DIVISION_BY_ZERO,
    DOMAIN_ERROR,
    INVALID_ARGUMENTS,
    EMPTY_EXPRESSION,
    EVALUATION_ERROR
}

data class EvaluationStep(
    val stepNumber: Int,
    val description: String,
    val expressionState: String,
    val resultValue: Double? = null
)

sealed interface MathResult {
    data class Success(
        val value: Double,
        val formattedResult: String,
        val symbolicRepresentation: String? = null,
        val rawExpression: String,
        val sanitizedExpression: String,
        val ast: ExprNode,
        val evaluationSteps: List<EvaluationStep> = emptyList()
    ) : MathResult

    data class Failure(
        val message: String,
        val errorType: MathErrorType,
        val position: Int? = null,
        val rawExpression: String
    ) : MathResult
}
