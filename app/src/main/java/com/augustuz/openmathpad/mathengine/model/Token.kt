package com.augustuz.openmathpad.mathengine.model

sealed interface TokenType {
    data class Number(val value: Double, val rawText: String) : TokenType
    data class Identifier(val name: String) : TokenType
    data class Operator(val symbol: String, val precedence: Int, val isRightAssociative: Boolean) : TokenType
    object LeftParen : TokenType
    object RightParen : TokenType
    object LeftBracket : TokenType
    object RightBracket : TokenType
    object LeftBrace : TokenType
    object RightBrace : TokenType
    object Comma : TokenType
    object Factorial : TokenType
    object EOF : TokenType
}

data class Token(
    val type: TokenType,
    val position: Int
)
