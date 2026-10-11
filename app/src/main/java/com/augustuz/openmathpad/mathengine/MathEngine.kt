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

        // Equation handling (e.g. 2x + 5 = 15, 3 * ? = 9, x^2 - 4 = 0)
        if (sanitized.contains("=")) {
            val parts = sanitized.split("=")
            if (parts.size == 2) {
                val lhsPart = parts[0].trim()
                val rhsPart = parts[1].trim()
                if (rhsPart.isEmpty() && lhsPart.isNotEmpty()) {
                    return evaluate(lhsPart, context)
                }
                if (lhsPart.isEmpty() && rhsPart.isNotEmpty()) {
                    return evaluate(rhsPart, context)
                }
                if (lhsPart.isNotEmpty() && rhsPart.isNotEmpty()) {
                    val equationResult = trySolveEquation(lhsPart, rhsPart, expression, sanitized, context)
                    if (equationResult != null) {
                        return equationResult
                    }
                }
            }
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
     * Solves linear and quadratic algebraic equations with a single unknown variable (e.g. x, y, ?).
     */
    private fun trySolveEquation(
        lhsStr: String,
        rhsStr: String,
        rawExpression: String,
        sanitizedExpression: String,
        context: EvaluationContext
    ): MathResult? {
        return try {
            val lexer = MathLexer(enableImplicitMultiplication = context.config.enableImplicitMultiplication)
            val parser = MathParser(constants = context.constants)

            val lhsTokens = lexer.tokenize(lhsStr)
            val rhsTokens = lexer.tokenize(rhsStr)
            val lhsAst = parser.parse(lhsTokens)
            val rhsAst = parser.parse(rhsTokens)

            val variables = (extractVariables(lhsAst, context.constants) + extractVariables(rhsAst, context.constants))

            if (variables.isEmpty()) {
                // Arithmetic identity comparison e.g. "2 + 3 = 5"
                val lhsVal = evaluator.evaluate(lhsAst, context).resultValue
                val rhsVal = evaluator.evaluate(rhsAst, context).resultValue
                val isEqual = kotlin.math.abs(lhsVal - rhsVal) <= context.config.doubleTolerance
                val boolStr = if (isEqual) "True" else "False"
                return MathResult.Success(
                    value = if (isEqual) 1.0 else 0.0,
                    formattedResult = boolStr,
                    symbolicRepresentation = boolStr,
                    rawExpression = rawExpression,
                    sanitizedExpression = sanitizedExpression,
                    ast = ExprNode.BinaryOpNode(lhsAst, "=", rhsAst),
                    evaluationSteps = listOf(
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(1, "Evaluate LHS", "$lhsStr = ${formatNumber(lhsVal)}"),
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(2, "Evaluate RHS", "$rhsStr = ${formatNumber(rhsVal)}"),
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(3, "Comparison", if (isEqual) "$lhsVal = $rhsVal ($boolStr)" else "$lhsVal ≠ $rhsVal ($boolStr)")
                    )
                )
            }

            // Pick target unknown variable (priority: '?' or first unknown)
            val varName = if (variables.contains("?")) "?" else variables.first()

            fun evalDiff(v: Double): Double {
                val evalContext = context.withVariable(varName, v)
                val l = evaluator.evaluate(lhsAst, evalContext).resultValue
                val r = evaluator.evaluate(rhsAst, evalContext).resultValue
                return l - r
            }

            // Polynomial coefficient extraction for a*v^2 + b*v + c = 0
            val c = evalDiff(0.0)
            val v1 = evalDiff(1.0)
            val vMinus1 = evalDiff(-1.0)
            val a = (v1 + vMinus1 - 2.0 * c) / 2.0
            val b = (v1 - vMinus1) / 2.0

            // Verify quadratic/linear degree at test points v=2 and v=3
            val v2 = evalDiff(2.0)
            val expectedV2 = 4.0 * a + 2.0 * b + c
            if (kotlin.math.abs(v2 - expectedV2) > 1e-3) {
                return null // Non-quadratic / higher degree equation
            }

            val cleanA = if (kotlin.math.abs(a) < 1e-7) 0.0 else a
            val cleanB = if (kotlin.math.abs(b) < 1e-7) 0.0 else b
            val cleanC = if (kotlin.math.abs(c) < 1e-7) 0.0 else c

            if (cleanA == 0.0 && cleanB == 0.0) {
                return if (kotlin.math.abs(cleanC) < 1e-7) {
                    MathResult.Success(
                        value = 1.0,
                        formattedResult = "Identity",
                        symbolicRepresentation = "Identity",
                        rawExpression = rawExpression,
                        sanitizedExpression = sanitizedExpression,
                        ast = ExprNode.BinaryOpNode(lhsAst, "=", rhsAst)
                    )
                } else {
                    MathResult.Failure(
                        message = "No solution for $varName (contradiction)",
                        errorType = MathErrorType.EVALUATION_ERROR,
                        rawExpression = rawExpression
                    )
                }
            }

            if (cleanA == 0.0) {
                // Linear equation: b*v + c = 0 => v = -c / b
                val root = cleanTolerance(-cleanC / cleanB, context.config.doubleTolerance)
                val formattedRoot = formatNumber(root)
                val resultString = "$varName = $formattedRoot"

                val steps = listOf(
                    com.augustuz.openmathpad.mathengine.model.EvaluationStep(1, "Equation", "$lhsStr = $rhsStr"),
                    com.augustuz.openmathpad.mathengine.model.EvaluationStep(2, "Standard linear form", "${formatTerm(cleanB, varName)} + ${formatNumber(cleanC)} = 0"),
                    com.augustuz.openmathpad.mathengine.model.EvaluationStep(3, "Solve for $varName", resultString)
                )

                return MathResult.Success(
                    value = root,
                    formattedResult = resultString,
                    symbolicRepresentation = resultString,
                    rawExpression = rawExpression,
                    sanitizedExpression = sanitizedExpression,
                    ast = ExprNode.BinaryOpNode(lhsAst, "=", rhsAst),
                    evaluationSteps = steps
                )
            } else {
                // Quadratic equation: a*v^2 + b*v + c = 0
                val D = cleanB * cleanB - 4.0 * cleanA * cleanC
                if (D > 1e-7) {
                    val sqrtD = kotlin.math.sqrt(D)
                    val r1 = cleanTolerance((-cleanB + sqrtD) / (2.0 * cleanA), context.config.doubleTolerance)
                    val r2 = cleanTolerance((-cleanB - sqrtD) / (2.0 * cleanA), context.config.doubleTolerance)
                    val f1 = formatNumber(r1)
                    val f2 = formatNumber(r2)
                    val resultString = "$varName = $f1, $varName = $f2"

                    val steps = listOf(
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(1, "Equation", "$lhsStr = $rhsStr"),
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(2, "Standard quadratic form", "${formatTerm(cleanA, "$varName^2")} + ${formatTerm(cleanB, varName)} + ${formatNumber(cleanC)} = 0"),
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(3, "Discriminant", "D = b^2 - 4ac = ${formatNumber(D)}"),
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(4, "Roots", resultString)
                    )

                    return MathResult.Success(
                        value = r1,
                        formattedResult = resultString,
                        symbolicRepresentation = resultString,
                        rawExpression = rawExpression,
                        sanitizedExpression = sanitizedExpression,
                        ast = ExprNode.BinaryOpNode(lhsAst, "=", rhsAst),
                        evaluationSteps = steps
                    )
                } else if (kotlin.math.abs(D) <= 1e-7) {
                    val r = cleanTolerance(-cleanB / (2.0 * cleanA), context.config.doubleTolerance)
                    val f = formatNumber(r)
                    val resultString = "$varName = $f"

                    val steps = listOf(
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(1, "Equation", "$lhsStr = $rhsStr"),
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(2, "Standard quadratic form", "${formatTerm(cleanA, "$varName^2")} + ${formatTerm(cleanB, varName)} + ${formatNumber(cleanC)} = 0"),
                        com.augustuz.openmathpad.mathengine.model.EvaluationStep(3, "Repeated root (D = 0)", resultString)
                    )

                    return MathResult.Success(
                        value = r,
                        formattedResult = resultString,
                        symbolicRepresentation = resultString,
                        rawExpression = rawExpression,
                        sanitizedExpression = sanitizedExpression,
                        ast = ExprNode.BinaryOpNode(lhsAst, "=", rhsAst),
                        evaluationSteps = steps
                    )
                } else {
                    return MathResult.Failure(
                        message = "No real solutions (D = ${formatNumber(D)} < 0)",
                        errorType = MathErrorType.EVALUATION_ERROR,
                        rawExpression = rawExpression
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractVariables(node: ExprNode, constants: Map<String, Double>): Set<String> {
        val set = mutableSetOf<String>()
        fun traverse(n: ExprNode) {
            when (n) {
                is ExprNode.VariableNode -> {
                    if (!constants.containsKey(n.name.lowercase())) {
                        set.add(n.name)
                    }
                }
                is ExprNode.UnaryOpNode -> traverse(n.operand)
                is ExprNode.BinaryOpNode -> {
                    traverse(n.left)
                    traverse(n.right)
                }
                is ExprNode.FunctionCallNode -> n.arguments.forEach { traverse(it) }
                else -> {}
            }
        }
        traverse(node)
        return set
    }

    private fun formatTerm(coeff: Double, variable: String): String {
        return when (coeff) {
            1.0 -> variable
            -1.0 -> "-$variable"
            else -> "${formatNumber(coeff)}*$variable"
        }
    }

    private fun cleanTolerance(value: Double, tolerance: Double): Double {
        val rounded = kotlin.math.round(value)
        return if (kotlin.math.abs(value - rounded) < tolerance) rounded else value
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble() && !value.isInfinite() && !value.isNaN()) {
            value.toLong().toString()
        } else {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.US)
            val format = java.text.DecimalFormat("#.##########", symbols)
            format.format(value)
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
