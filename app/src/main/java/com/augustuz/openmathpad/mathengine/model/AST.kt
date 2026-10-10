package com.augustuz.openmathpad.mathengine.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

sealed interface ExprNode {
    fun toExpressionString(): String

    data class NumberNode(val value: Double) : ExprNode {
        override fun toExpressionString(): String {
            return if (value == value.toLong().toDouble() && !value.isInfinite() && !value.isNaN()) {
                value.toLong().toString()
            } else {
                val symbols = DecimalFormatSymbols(Locale.US)
                val format = DecimalFormat("#.##########", symbols)
                format.format(value)
            }
        }
    }

    data class ConstantNode(val name: String, val value: Double) : ExprNode {
        override fun toExpressionString(): String = name
    }

    data class VariableNode(val name: String) : ExprNode {
        override fun toExpressionString(): String = name
    }

    data class UnaryOpNode(
        val operator: String,
        val operand: ExprNode,
        val isPrefix: Boolean = true
    ) : ExprNode {
        override fun toExpressionString(): String =
            if (isPrefix) "$operator(${operand.toExpressionString()})"
            else "(${operand.toExpressionString()})$operator"
    }

    data class BinaryOpNode(
        val left: ExprNode,
        val operator: String,
        val right: ExprNode
    ) : ExprNode {
        override fun toExpressionString(): String =
            "(${left.toExpressionString()} $operator ${right.toExpressionString()})"
    }

    data class FunctionCallNode(
        val functionName: String,
        val arguments: List<ExprNode>
    ) : ExprNode {
        override fun toExpressionString(): String =
            "$functionName(${arguments.joinToString(", ") { it.toExpressionString() }})"
    }
}
