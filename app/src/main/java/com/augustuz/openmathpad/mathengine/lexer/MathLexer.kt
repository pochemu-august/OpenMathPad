package com.augustuz.openmathpad.mathengine.lexer

import com.augustuz.openmathpad.mathengine.model.Token
import com.augustuz.openmathpad.mathengine.model.TokenType

class MathLexer(
    private val enableImplicitMultiplication: Boolean = true
) {

    companion object {
        val KNOWN_FUNCTIONS = setOf(
            "sin", "cos", "tan",
            "asin", "arcsin", "acos", "arccos", "atan", "arctan",
            "sinh", "cosh", "tanh",
            "sqrt", "cbrt",
            "log", "log10", "log2", "ln", "exp",
            "abs", "ceil", "floor", "round", "sign",
            "min", "max", "clamp", "fact", "factorial"
        )
    }

    class LexerException(message: String, val position: Int) : Exception(message)

    fun tokenize(input: String): List<Token> {
        val rawTokens = mutableListOf<Token>()
        var i = 0
        val len = input.length

        while (i < len) {
            val ch = input[i]

            // Skip whitespace
            if (ch.isWhitespace()) {
                i++
                continue
            }

            val pos = i

            // 1. Numbers (including floating point & scientific notation e.g., 1.2e3, 0.5)
            if (ch.isDigit() || (ch == '.' && i + 1 < len && input[i + 1].isDigit())) {
                var numStr = ""
                var hasDot = false
                var hasE = false

                while (i < len) {
                    val c = input[i]
                    if (c.isDigit()) {
                        numStr += c
                        i++
                    } else if (c == '.' && !hasDot && !hasE) {
                        hasDot = true
                        numStr += c
                        i++
                    } else if ((c == 'e' || c == 'E') && !hasE) {
                        // Lookahead to ensure it's scientific notation (e.g. 1e10 or 1e-5)
                        if (i + 1 < len && (input[i + 1].isDigit() || input[i + 1] == '+' || input[i + 1] == '-')) {
                            hasE = true
                            numStr += c
                            i++
                            if (i < len && (input[i] == '+' || input[i] == '-')) {
                                numStr += input[i]
                                i++
                            }
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }

                val value = numStr.toDoubleOrNull()
                    ?: throw LexerException("Invalid number format '$numStr'", pos)
                rawTokens.add(Token(TokenType.Number(value, numStr), pos))
                continue
            }

            // 2. Identifiers (functions, constants, variables)
            if (ch.isLetter() || ch == '_' || ch == 'π' || ch == 'τ' || ch == 'ϕ' || ch == 'φ') {
                var idStr = ""
                while (i < len) {
                    val c = input[i]
                    if (c.isLetterOrDigit() || c == '_' || c == 'π' || c == 'τ' || c == 'ϕ' || c == 'φ') {
                        idStr += c
                        i++
                    } else {
                        break
                    }
                }
                rawTokens.add(Token(TokenType.Identifier(idStr), pos))
                continue
            }

            // 3. Operators & Punctuation
            when (ch) {
                '+' -> {
                    rawTokens.add(Token(TokenType.Operator("+", precedence = 1, isRightAssociative = false), pos))
                    i++
                }
                '-' -> {
                    rawTokens.add(Token(TokenType.Operator("-", precedence = 1, isRightAssociative = false), pos))
                    i++
                }
                '*' -> {
                    rawTokens.add(Token(TokenType.Operator("*", precedence = 2, isRightAssociative = false), pos))
                    i++
                }
                '/' -> {
                    rawTokens.add(Token(TokenType.Operator("/", precedence = 2, isRightAssociative = false), pos))
                    i++
                }
                '%' -> {
                    rawTokens.add(Token(TokenType.Operator("%", precedence = 2, isRightAssociative = false), pos))
                    i++
                }
                '^' -> {
                    rawTokens.add(Token(TokenType.Operator("^", precedence = 4, isRightAssociative = true), pos))
                    i++
                }
                '!' -> {
                    rawTokens.add(Token(TokenType.Factorial, pos))
                    i++
                }
                '(' -> {
                    rawTokens.add(Token(TokenType.LeftParen, pos))
                    i++
                }
                ')' -> {
                    rawTokens.add(Token(TokenType.RightParen, pos))
                    i++
                }
                '[' -> {
                    rawTokens.add(Token(TokenType.LeftBracket, pos))
                    i++
                }
                ']' -> {
                    rawTokens.add(Token(TokenType.RightBracket, pos))
                    i++
                }
                '{' -> {
                    rawTokens.add(Token(TokenType.LeftBrace, pos))
                    i++
                }
                '}' -> {
                    rawTokens.add(Token(TokenType.RightBrace, pos))
                    i++
                }
                ',' -> {
                    rawTokens.add(Token(TokenType.Comma, pos))
                    i++
                }
                else -> {
                    throw LexerException("Unexpected character '$ch'", pos)
                }
            }
        }

        rawTokens.add(Token(TokenType.EOF, len))

        return if (enableImplicitMultiplication) {
            insertImplicitMultiplication(rawTokens)
        } else {
            rawTokens
        }
    }

    private fun insertImplicitMultiplication(tokens: List<Token>): List<Token> {
        if (tokens.size <= 1) return tokens

        val result = mutableListOf<Token>()
        for (idx in 0 until tokens.size - 1) {
            val t1 = tokens[idx]
            val t2 = tokens[idx + 1]

            result.add(t1)

            if (shouldInsertMultiplication(t1, t2)) {
                result.add(
                    Token(
                        TokenType.Operator("*", precedence = 2, isRightAssociative = false),
                        t2.position
                    )
                )
            }
        }
        result.add(tokens.last())
        return result
    }

    private fun shouldInsertMultiplication(t1: Token, t2: Token): Boolean {
        val t1CanEndOperand = when (t1.type) {
            is TokenType.Number -> true
            is TokenType.Identifier -> true
            is TokenType.RightParen -> true
            is TokenType.RightBracket -> true
            is TokenType.RightBrace -> true
            is TokenType.Factorial -> true
            else -> false
        }

        val t2CanStartOperand = when (t2.type) {
            is TokenType.Number -> true
            is TokenType.Identifier -> {
                // If t1 is an identifier and t2 is an identifier, insert multiplication unless t1 is a known function and t2 starts function arguments
                true
            }
            is TokenType.LeftParen -> {
                // If t1 is an identifier that is a KNOWN function name, e.g. sin(...), do NOT insert multiplication!
                if (t1.type is TokenType.Identifier) {
                    !KNOWN_FUNCTIONS.contains(t1.type.name.lowercase())
                } else {
                    true
                }
            }
            is TokenType.LeftBracket -> true
            is TokenType.LeftBrace -> true
            else -> false
        }

        return t1CanEndOperand && t2CanStartOperand
    }
}
