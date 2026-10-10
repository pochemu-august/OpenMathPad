package com.augustuz.openmathpad.mathengine

import com.augustuz.openmathpad.mathengine.evaluator.MathEvaluator
import com.augustuz.openmathpad.mathengine.latex.LatexSanitizer
import com.augustuz.openmathpad.mathengine.lexer.MathLexer
import com.augustuz.openmathpad.mathengine.model.AngleMode
import com.augustuz.openmathpad.mathengine.model.EvaluationContext
import com.augustuz.openmathpad.mathengine.model.ExprNode
import com.augustuz.openmathpad.mathengine.model.MathErrorType
import com.augustuz.openmathpad.mathengine.model.MathResult
import com.augustuz.openmathpad.mathengine.model.Token
import com.augustuz.openmathpad.mathengine.parser.MathParser

class MathEngine(
    private val evaluator: MathEvaluator = MathEvaluator()
) {

    /**
     * Evaluates a mathematical expression (standard arithmetic, trigonometric functions,
     * logarithms, exponents, constants, LaTeX inputs, and variables).
     */
    fun evaluate(
        expression: String,
        context: EvaluationContext = EvaluationContext()
    ): MathResult {
        if (expression.isBlank()) {
            return MathResult.Failure(
                message = "Expression is empty",
                errorType = MathErrorType.EMPTY_EXPRESSION,
                rawExpression = expression
            )
        }

        val sanitized = sanitizeLatex(expression)
        if (sanitized.isBlank()) {
            return MathResult.Failure(
                message = "Expression is empty after sanitization",
                errorType = MathErrorType.EMPTY_EXPRESSION,
                rawExpression = expression
            )
        }

        return try {
            val lexer = MathLexer(enableImplicitMultiplication = context.config.enableImplicitMultiplication)
            val tokens = lexer.tokenize(sanitized)

            val parser = MathParser(constants = context.constants)
            val ast = parser.parse(tokens)

            val output = evaluator.evaluate(ast, context)

            MathResult.Success(
                value = output.resultValue,
                formattedResult = output.formattedResult,
                symbolicRepresentation = output.symbolicRepresentation,
                rawExpression = expression,
                sanitizedExpression = sanitized,
                ast = ast,
                evaluationSteps = output.steps
            )
        } catch (e: MathLexer.LexerException) {
            MathResult.Failure(
                message = e.message ?: "Lexer error",
                errorType = MathErrorType.SYNTAX_ERROR,
                position = e.position,
                rawExpression = expression
            )
        } catch (e: MathParser.ParserException) {
            MathResult.Failure(
                message = e.message ?: "Parser error",
                errorType = e.errorType,
                position = e.position,
                rawExpression = expression
            )
        } catch (e: MathEvaluator.EvaluatorException) {
            MathResult.Failure(
                message = e.message ?: "Evaluation error",
                errorType = e.errorType,
                rawExpression = expression
            )
        } catch (e: Exception) {
            MathResult.Failure(
                message = e.message ?: "Unexpected calculation error",
                errorType = MathErrorType.EVALUATION_ERROR,
                rawExpression = expression
            )
        }
    }

    /**
     * Helper method to evaluate expression given variable key-value pairs.
     */
    fun evaluateWithVariables(
        expression: String,
        variables: Map<String, Double>,
        angleMode: AngleMode = AngleMode.RADIANS
    ): MathResult {
        val context = EvaluationContext(
            variables = variables,
            config = com.augustuz.openmathpad.mathengine.model.MathEngineConfig(angleMode = angleMode)
        )
        return evaluate(expression, context)
    }

    /**
     * Parses an expression string directly to an AST ExprNode.
     */
    fun parseAst(
        expression: String,
        context: EvaluationContext = EvaluationContext()
    ): ExprNode {
        val sanitized = sanitizeLatex(expression)
        val lexer = MathLexer(enableImplicitMultiplication = context.config.enableImplicitMultiplication)
        val tokens = lexer.tokenize(sanitized)
        val parser = MathParser(constants = context.constants)
        return parser.parse(tokens)
    }

    /**
     * Normalizes LaTeX/OCR raw math string.
     */
    fun sanitizeLatex(expression: String): String {
        return LatexSanitizer.sanitize(expression)
    }

    /**
     * Tokenizes math string into List<Token>.
     */
    fun tokenize(expression: String, enableImplicitMultiplication: Boolean = true): List<Token> {
        val sanitized = sanitizeLatex(expression)
        val lexer = MathLexer(enableImplicitMultiplication)
        return lexer.tokenize(sanitized)
    }
}
