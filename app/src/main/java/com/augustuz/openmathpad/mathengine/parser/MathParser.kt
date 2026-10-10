package com.augustuz.openmathpad.mathengine.parser

import com.augustuz.openmathpad.mathengine.lexer.MathLexer
import com.augustuz.openmathpad.mathengine.model.EvaluationContext
import com.augustuz.openmathpad.mathengine.model.ExprNode
import com.augustuz.openmathpad.mathengine.model.MathErrorType
import com.augustuz.openmathpad.mathengine.model.Token
import com.augustuz.openmathpad.mathengine.model.TokenType

class MathParser(
    private val constants: Map<String, Double> = EvaluationContext.DEFAULT_CONSTANTS
) {

    class ParserException(
        message: String,
        val errorType: MathErrorType,
        val position: Int? = null
    ) : Exception(message)

    fun parse(tokens: List<Token>): ExprNode {
        val nonEofTokens = tokens.filter { it.type !is TokenType.EOF }
        if (nonEofTokens.isEmpty()) {
            throw ParserException("Empty expression", MathErrorType.EMPTY_EXPRESSION)
        }

        val state = ParserState(tokens)
        val ast = parseExpression(state, minPrecedence = 1)

        if (state.currentToken().type !is TokenType.EOF) {
            val token = state.currentToken()
            throw ParserException(
                "Unexpected token '${token.type}' after valid expression",
                MathErrorType.SYNTAX_ERROR,
                token.position
            )
        }

        return ast
    }

    private class ParserState(private val tokens: List<Token>) {
        var cursor: Int = 0

        fun currentToken(): Token {
            return if (cursor < tokens.size) tokens[cursor] else tokens.last()
        }

        fun advance(): Token {
            val tok = currentToken()
            if (cursor < tokens.size - 1) {
                cursor++
            }
            return tok
        }
    }

    private fun parseExpression(state: ParserState, minPrecedence: Int): ExprNode {
        var left = parseUnary(state)

        while (true) {
            val current = state.currentToken()
            val op = current.type as? TokenType.Operator ?: break

            if (op.precedence < minPrecedence) break

            state.advance() // consume operator

            val nextMinPrecedence = if (op.isRightAssociative) op.precedence else op.precedence + 1
            val right = parseExpression(state, nextMinPrecedence)

            left = ExprNode.BinaryOpNode(
                left = left,
                operator = op.symbol,
                right = right
            )
        }

        return left
    }

    private fun parseUnary(state: ParserState): ExprNode {
        val current = state.currentToken()
        val op = current.type as? TokenType.Operator

        if (op != null && (op.symbol == "-" || op.symbol == "+")) {
            state.advance()
            val operand = parseUnary(state)
            return ExprNode.UnaryOpNode(
                operator = op.symbol,
                operand = operand,
                isPrefix = true
            )
        }

        return parsePostfix(state)
    }

    private fun parsePostfix(state: ParserState): ExprNode {
        var expr = parsePrimary(state)

        while (true) {
            val current = state.currentToken()
            if (current.type is TokenType.Factorial) {
                state.advance()
                expr = ExprNode.UnaryOpNode(
                    operator = "!",
                    operand = expr,
                    isPrefix = false
                )
            } else {
                break
            }
        }

        return expr
    }

    private fun parsePrimary(state: ParserState): ExprNode {
        val token = state.currentToken()

        return when (val type = token.type) {
            is TokenType.Number -> {
                state.advance()
                ExprNode.NumberNode(type.value)
            }

            is TokenType.Identifier -> {
                state.advance()
                val idName = type.name
                val nextToken = state.currentToken()

                val isOpeningParen = nextToken.type is TokenType.LeftParen ||
                        nextToken.type is TokenType.LeftBracket ||
                        nextToken.type is TokenType.LeftBrace

                val isKnownFunc = MathLexer.KNOWN_FUNCTIONS.contains(idName.lowercase())

                if (isOpeningParen || isKnownFunc) {
                    if (isOpeningParen) {
                        state.advance() // consume opening paren
                        val args = mutableListOf<ExprNode>()

                        if (!isClosingParen(state.currentToken().type)) {
                            while (true) {
                                args.add(parseExpression(state, minPrecedence = 1))
                                if (state.currentToken().type is TokenType.Comma) {
                                    state.advance() // consume comma
                                } else {
                                    break
                                }
                            }
                        }

                        val closingToken = state.currentToken()
                        if (!isClosingParen(closingToken.type)) {
                            throw ParserException(
                                "Missing closing parenthesis for function '$idName'",
                                MathErrorType.UNMATCHED_PARENTHESES,
                                closingToken.position
                            )
                        }
                        state.advance() // consume closing paren

                        ExprNode.FunctionCallNode(idName, args)
                    } else {
                        // Function without paren, e.g. "sin x" -> parse next primary as argument
                        val arg = parseUnary(state)
                        ExprNode.FunctionCallNode(idName, listOf(arg))
                    }
                } else if (constants.containsKey(idName)) {
                    ExprNode.ConstantNode(idName, constants[idName]!!)
                } else {
                    ExprNode.VariableNode(idName)
                }
            }

            is TokenType.LeftParen, is TokenType.LeftBracket, is TokenType.LeftBrace -> {
                val openToken = state.advance()
                val expr = parseExpression(state, minPrecedence = 1)
                val closeToken = state.currentToken()

                if (!isMatchingPair(openToken.type, closeToken.type)) {
                    throw ParserException(
                        "Unmatched parenthesis or bracket at position ${closeToken.position}",
                        MathErrorType.UNMATCHED_PARENTHESES,
                        closeToken.position
                    )
                }
                state.advance() // consume closing paren
                expr
            }

            else -> {
                throw ParserException(
                    "Unexpected token '${token.type}' at position ${token.position}",
                    MathErrorType.SYNTAX_ERROR,
                    token.position
                )
            }
        }
    }

    private fun isClosingParen(type: TokenType): Boolean {
        return type is TokenType.RightParen || type is TokenType.RightBracket || type is TokenType.RightBrace
    }

    private fun isMatchingPair(open: TokenType, close: TokenType): Boolean {
        return (open is TokenType.LeftParen && close is TokenType.RightParen) ||
                (open is TokenType.LeftBracket && close is TokenType.RightBracket) ||
                (open is TokenType.LeftBrace && close is TokenType.RightBrace)
    }
}
