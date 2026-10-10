package com.augustuz.openmathpad.mathengine

import com.augustuz.openmathpad.mathengine.model.AngleMode
import com.augustuz.openmathpad.mathengine.model.EvaluationContext
import com.augustuz.openmathpad.mathengine.model.MathErrorType
import com.augustuz.openmathpad.mathengine.model.MathResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MathEngineTest {

    private lateinit var engine: MathEngine

    @Before
    fun setUp() {
        engine = MathEngine()
    }

    @Test
    fun testBasicArithmetic() {
        val res1 = engine.evaluate("2 + 3 * 4") as MathResult.Success
        assertEquals(14.0, res1.value, 1e-9)
        assertEquals("14", res1.formattedResult)

        val res2 = engine.evaluate("(10 - 2) / 4") as MathResult.Success
        assertEquals(2.0, res2.value, 1e-9)

        val res3 = engine.evaluate("5 % 2") as MathResult.Success
        assertEquals(1.0, res3.value, 1e-9)

        val res4 = engine.evaluate("2^3^2") as MathResult.Success // Right-associative 2^(3^2) = 2^9 = 512
        assertEquals(512.0, res4.value, 1e-9)
    }

    @Test
    fun testNegativeAndDecimals() {
        val res1 = engine.evaluate("-5.5 + 2.5") as MathResult.Success
        assertEquals(-3.0, res1.value, 1e-9)

        val res2 = engine.evaluate("-(-3)") as MathResult.Success
        assertEquals(3.0, res2.value, 1e-9)
    }

    @Test
    fun testFactorial() {
        val res1 = engine.evaluate("5!") as MathResult.Success
        assertEquals(120.0, res1.value, 1e-9)

        val res2 = engine.evaluate("(2 + 3)!") as MathResult.Success
        assertEquals(120.0, res2.value, 1e-9)
    }

    @Test
    fun testConstants() {
        val resPi = engine.evaluate("pi") as MathResult.Success
        assertEquals(Math.PI, resPi.value, 1e-9)
        assertEquals("π", resPi.symbolicRepresentation)

        val resE = engine.evaluate("e") as MathResult.Success
        assertEquals(Math.E, resE.value, 1e-9)
        assertEquals("e", resE.symbolicRepresentation)

        val resTau = engine.evaluate("tau") as MathResult.Success
        assertEquals(2.0 * Math.PI, resTau.value, 1e-9)
    }

    @Test
    fun testImplicitMultiplication() {
        val res1 = engine.evaluate("2pi") as MathResult.Success
        assertEquals(2.0 * Math.PI, res1.value, 1e-9)

        val res2 = engine.evaluate("2(3 + 4)") as MathResult.Success
        assertEquals(14.0, res2.value, 1e-9)

        val res3 = engine.evaluate("(2 + 3)(4 + 5)") as MathResult.Success
        assertEquals(45.0, res3.value, 1e-9)

        val res4 = engine.evaluateWithVariables("5x", mapOf("x" to 3.0)) as MathResult.Success
        assertEquals(15.0, res4.value, 1e-9)
    }

    @Test
    fun testTrigonometricFunctionsInRadiansAndDegrees() {
        // Radians mode (default)
        val resRad = engine.evaluate("sin(pi / 2)") as MathResult.Success
        assertEquals(1.0, resRad.value, 1e-9)

        val resCos = engine.evaluate("cos(pi)") as MathResult.Success
        assertEquals(-1.0, resCos.value, 1e-9)

        // Degrees mode
        val degContext = EvaluationContext(
            config = com.augustuz.openmathpad.mathengine.model.MathEngineConfig(angleMode = AngleMode.DEGREES)
        )
        val resDeg = engine.evaluate("sin(90)", degContext) as MathResult.Success
        assertEquals(1.0, resDeg.value, 1e-9)

        val resCosDeg = engine.evaluate("cos(180)", degContext) as MathResult.Success
        assertEquals(-1.0, resCosDeg.value, 1e-9)
    }

    @Test
    fun testMathematicalFunctions() {
        val resSqrt = engine.evaluate("sqrt(16)") as MathResult.Success
        assertEquals(4.0, resSqrt.value, 1e-9)

        val resCbrt = engine.evaluate("cbrt(27)") as MathResult.Success
        assertEquals(3.0, resCbrt.value, 1e-9)

        val resLog = engine.evaluate("log(100)") as MathResult.Success
        assertEquals(2.0, resLog.value, 1e-9)

        val resLn = engine.evaluate("ln(e)") as MathResult.Success
        assertEquals(1.0, resLn.value, 1e-9)

        val resAbs = engine.evaluate("abs(-7.5)") as MathResult.Success
        assertEquals(7.5, resAbs.value, 1e-9)

        val resMinMax = engine.evaluate("max(10, min(5, 20))") as MathResult.Success
        assertEquals(10.0, resMinMax.value, 1e-9)

        val resClamp = engine.evaluate("clamp(15, 0, 10)") as MathResult.Success
        assertEquals(10.0, resClamp.value, 1e-9)
    }

    @Test
    fun testVariables() {
        val vars = mapOf("a" to 3.0, "b" to 4.0)
        val res = engine.evaluateWithVariables("sqrt(a^2 + b^2)", vars) as MathResult.Success
        assertEquals(5.0, res.value, 1e-9)
    }

    @Test
    fun testLatexEvaluation() {
        val resFrac = engine.evaluate("\\frac{1}{2} + \\frac{1}{4}") as MathResult.Success
        assertEquals(0.75, resFrac.value, 1e-9)
        assertEquals("3/4", resFrac.symbolicRepresentation)

        val resSqrtLatex = engine.evaluate("\\sqrt{16} + \\sqrt[3]{8}") as MathResult.Success
        assertEquals(6.0, resSqrtLatex.value, 1e-9)
    }

    @Test
    fun testDivisionByZeroError() {
        val failure = engine.evaluate("10 / 0") as MathResult.Failure
        assertEquals(MathErrorType.DIVISION_BY_ZERO, failure.errorType)
    }

    @Test
    fun testDomainError() {
        val failure = engine.evaluate("sqrt(-4)") as MathResult.Failure
        assertEquals(MathErrorType.DOMAIN_ERROR, failure.errorType)
    }

    @Test
    fun testUnmatchedParenthesesError() {
        val failure = engine.evaluate("(2 + 3 * 4") as MathResult.Failure
        assertEquals(MathErrorType.UNMATCHED_PARENTHESES, failure.errorType)
    }

    @Test
    fun testUndefinedVariableError() {
        val failure = engine.evaluate("x + 5") as MathResult.Failure
        assertEquals(MathErrorType.UNDEFINED_VARIABLE, failure.errorType)
    }

    @Test
    fun testEmptyExpressionError() {
        val failure = engine.evaluate("   ") as MathResult.Failure
        assertEquals(MathErrorType.EMPTY_EXPRESSION, failure.errorType)
    }

    @Test
    fun testEvaluationSteps() {
        val res = engine.evaluate("2 + 3 * 4") as MathResult.Success
        assertTrue(res.evaluationSteps.isNotEmpty())
        assertNotNull(res.ast)
    }
}
