package com.augustuz.openmathpad.mathengine.evaluator

import com.augustuz.openmathpad.mathengine.model.AngleMode
import com.augustuz.openmathpad.mathengine.model.EvaluationContext
import com.augustuz.openmathpad.mathengine.model.EvaluationStep
import com.augustuz.openmathpad.mathengine.model.ExprNode
import com.augustuz.openmathpad.mathengine.model.MathErrorType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

class MathEvaluator {

    class EvaluatorException(
        message: String,
        val errorType: MathErrorType
    ) : Exception(message)

    data class EvaluationOutput(
        val resultValue: Double,
        val formattedResult: String,
        val symbolicRepresentation: String?,
        val steps: List<EvaluationStep>
    )

    fun evaluate(ast: ExprNode, context: EvaluationContext = EvaluationContext()): EvaluationOutput {
        val steps = mutableListOf<EvaluationStep>()
        val rawValue = evaluateNode(ast, context, steps)
        val cleanedValue = cleanTolerance(rawValue, context.config.doubleTolerance)

        val formattedResult = formatResult(cleanedValue, context.config.maxDecimalPlaces, context.config.formatScientificThreshold)
        val symbolic = findSymbolicRepresentation(cleanedValue, context.config.doubleTolerance)

        return EvaluationOutput(
            resultValue = cleanedValue,
            formattedResult = formattedResult,
            symbolicRepresentation = symbolic,
            steps = steps
        )
    }

    private fun evaluateNode(
        node: ExprNode,
        context: EvaluationContext,
        steps: MutableList<EvaluationStep>
    ): Double {
        val valResult = when (node) {
            is ExprNode.NumberNode -> node.value

            is ExprNode.ConstantNode -> {
                context.constants[node.name]
                    ?: context.variables[node.name]
                    ?: node.value
            }

            is ExprNode.VariableNode -> {
                context.variables[node.name]
                    ?: context.constants[node.name]
                    ?: throw EvaluatorException(
                        "Undefined variable '${node.name}'",
                        MathErrorType.UNDEFINED_VARIABLE
                    )
            }

            is ExprNode.UnaryOpNode -> {
                val operandVal = evaluateNode(node.operand, context, steps)
                when (node.operator) {
                    "+" -> operandVal
                    "-" -> -operandVal
                    "!" -> calculateFactorial(operandVal)
                    else -> throw EvaluatorException("Unknown unary operator '${node.operator}'", MathErrorType.EVALUATION_ERROR)
                }
            }

            is ExprNode.BinaryOpNode -> {
                val leftVal = evaluateNode(node.left, context, steps)
                val rightVal = evaluateNode(node.right, context, steps)

                when (node.operator) {
                    "+" -> leftVal + rightVal
                    "-" -> leftVal - rightVal
                    "*" -> leftVal * rightVal
                    "/" -> {
                        if (abs(rightVal) < context.config.doubleTolerance) {
                            throw EvaluatorException("Division by zero", MathErrorType.DIVISION_BY_ZERO)
                        }
                        leftVal / rightVal
                    }
                    "%" -> {
                        if (abs(rightVal) < context.config.doubleTolerance) {
                            throw EvaluatorException("Modulo by zero", MathErrorType.DIVISION_BY_ZERO)
                        }
                        leftVal % rightVal
                    }
                    "^" -> {
                        if (leftVal < 0 && floor(rightVal) != rightVal) {
                            throw EvaluatorException("Complex number result not supported", MathErrorType.DOMAIN_ERROR)
                        }
                        leftVal.pow(rightVal)
                    }
                    else -> throw EvaluatorException("Unknown binary operator '${node.operator}'", MathErrorType.EVALUATION_ERROR)
                }
            }

            is ExprNode.FunctionCallNode -> {
                evaluateFunction(node, context, steps)
            }
        }

        val stepNum = steps.size + 1
        val exprStr = node.toExpressionString()
        val desc = "Evaluated $exprStr -> ${formatResult(valResult, 6, 1e10)}"
        steps.add(EvaluationStep(stepNum, desc, exprStr, valResult))

        return valResult
    }

    private fun evaluateFunction(
        node: ExprNode.FunctionCallNode,
        context: EvaluationContext,
        steps: MutableList<EvaluationStep>
    ): Double {
        val funcName = node.functionName.lowercase()
        val argVals = node.arguments.map { evaluateNode(it, context, steps) }

        if (argVals.isEmpty() && funcName != "rand") {
            throw EvaluatorException("Function '$funcName' requires arguments", MathErrorType.INVALID_ARGUMENTS)
        }

        return when (funcName) {
            "sin" -> {
                val rad = toRadians(argVals[0], context.config.angleMode)
                sin(rad)
            }
            "cos" -> {
                val rad = toRadians(argVals[0], context.config.angleMode)
                cos(rad)
            }
            "tan" -> {
                val rad = toRadians(argVals[0], context.config.angleMode)
                val cosVal = cos(rad)
                if (abs(cosVal) < context.config.doubleTolerance) {
                    throw EvaluatorException("Tangent undefined at ${argVals[0]}", MathErrorType.DOMAIN_ERROR)
                }
                tan(rad)
            }
            "asin", "arcsin" -> {
                val arg = argVals[0]
                if (arg < -1.0 || arg > 1.0) {
                    throw EvaluatorException("arcsin domain error for value $arg (must be in [-1, 1])", MathErrorType.DOMAIN_ERROR)
                }
                val rad = asin(arg)
                fromRadians(rad, context.config.angleMode)
            }
            "acos", "arccos" -> {
                val arg = argVals[0]
                if (arg < -1.0 || arg > 1.0) {
                    throw EvaluatorException("arccos domain error for value $arg (must be in [-1, 1])", MathErrorType.DOMAIN_ERROR)
                }
                val rad = acos(arg)
                fromRadians(rad, context.config.angleMode)
            }
            "atan", "arctan" -> {
                val rad = atan(argVals[0])
                fromRadians(rad, context.config.angleMode)
            }
            "sinh" -> sinh(argVals[0])
            "cosh" -> cosh(argVals[0])
            "tanh" -> tanh(argVals[0])
            "sqrt" -> {
                val arg = argVals[0]
                if (arg < 0) {
                    throw EvaluatorException("Square root undefined for negative number $arg", MathErrorType.DOMAIN_ERROR)
                }
                sqrt(arg)
            }
            "cbrt" -> cbrt(argVals[0])
            "log", "log10" -> {
                val arg = argVals[0]
                if (arg <= 0) {
                    throw EvaluatorException("Logarithm undefined for non-positive number $arg", MathErrorType.DOMAIN_ERROR)
                }
                log10(arg)
            }
            "ln" -> {
                val arg = argVals[0]
                if (arg <= 0) {
                    throw EvaluatorException("Natural log undefined for non-positive number $arg", MathErrorType.DOMAIN_ERROR)
                }
                ln(arg)
            }
            "log2" -> {
                val arg = argVals[0]
                if (arg <= 0) {
                    throw EvaluatorException("Log2 undefined for non-positive number $arg", MathErrorType.DOMAIN_ERROR)
                }
                ln(arg) / ln(2.0)
            }
            "exp" -> exp(argVals[0])
            "abs" -> abs(argVals[0])
            "ceil" -> ceil(argVals[0])
            "floor" -> floor(argVals[0])
            "round" -> round(argVals[0])
            "sign" -> sign(argVals[0])
            "min" -> {
                if (argVals.size < 2) throw EvaluatorException("min requires at least 2 arguments", MathErrorType.INVALID_ARGUMENTS)
                argVals.minOrNull()!!
            }
            "max" -> {
                if (argVals.size < 2) throw EvaluatorException("max requires at least 2 arguments", MathErrorType.INVALID_ARGUMENTS)
                argVals.maxOrNull()!!
            }
            "clamp" -> {
                if (argVals.size < 3) throw EvaluatorException("clamp requires 3 arguments (val, min, max)", MathErrorType.INVALID_ARGUMENTS)
                argVals[0].coerceIn(argVals[1], argVals[2])
            }
            "fact", "factorial" -> calculateFactorial(argVals[0])
            else -> throw EvaluatorException("Unknown function '$funcName'", MathErrorType.UNKNOWN_IDENTIFIER)
        }
    }

    private fun toRadians(angle: Double, mode: AngleMode): Double {
        return if (mode == AngleMode.DEGREES) Math.toRadians(angle) else angle
    }

    private fun fromRadians(rad: Double, mode: AngleMode): Double {
        return if (mode == AngleMode.DEGREES) Math.toDegrees(rad) else rad
    }

    private fun calculateFactorial(n: Double): Double {
        if (n < 0 || floor(n) != n) {
            throw EvaluatorException("Factorial is only defined for non-negative integers", MathErrorType.DOMAIN_ERROR)
        }
        val intN = n.toInt()
        if (intN > 170) {
            throw EvaluatorException("Factorial overflow for n = $intN", MathErrorType.DOMAIN_ERROR)
        }
        var res = 1.0
        for (i in 2..intN) {
            res *= i
        }
        return res
    }

    private fun cleanTolerance(value: Double, tolerance: Double): Double {
        if (value.isNaN() || value.isInfinite()) return value
        val nearestLong = value.roundToLong()
        return if (abs(value - nearestLong) < tolerance) {
            nearestLong.toDouble()
        } else {
            value
        }
    }

    private fun formatResult(value: Double, maxDecimalPlaces: Int, scientificThreshold: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

        val absVal = abs(value)
        if (absVal >= scientificThreshold || (absVal > 0 && absVal < 1e-6)) {
            val symbols = DecimalFormatSymbols(Locale.US)
            val fmt = DecimalFormat("0.######E0", symbols)
            return fmt.format(value)
        }

        if (value == value.toLong().toDouble()) {
            return value.toLong().toString()
        }

        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = "#." + "#".repeat(maxDecimalPlaces)
        val fmt = DecimalFormat(pattern, symbols)
        return fmt.format(value)
    }

    private fun findSymbolicRepresentation(value: Double, tolerance: Double): String? {
        if (value.isNaN() || value.isInfinite()) return null

        // Check common mathematical constants
        if (abs(value - Math.PI) < tolerance) return "π"
        if (abs(value - 2 * Math.PI) < tolerance) return "2π"
        if (abs(value - Math.E) < tolerance) return "e"
        if (abs(value - ((1 + sqrt(5.0)) / 2.0)) < tolerance) return "ϕ"

        // Check exact fraction P/Q for Q in 2..100
        if (value != value.toLong().toDouble() && abs(value) < 1000) {
            for (q in 2..100) {
                val p = (value * q).roundToLong()
                if (abs(value - p.toDouble() / q) < tolerance) {
                    val gcd = gcd(abs(p), q.toLong())
                    val num = p / gcd
                    val den = q / gcd
                    return "$num/$den"
                }
            }
        }

        return null
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }
}
