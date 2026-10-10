package com.augustuz.openmathpad.mathengine

import com.augustuz.openmathpad.mathengine.latex.LatexSanitizer
import org.junit.Assert.assertEquals
import org.junit.Test

class LatexSanitizerTest {

    @Test
    fun testFractions() {
        val input = "\\frac{1}{2}"
        val sanitized = LatexSanitizer.sanitize(input)
        assertEquals("((1)/(2))", sanitized)
    }

    @Test
    fun testNestedFractions() {
        val input = "\\frac{\\frac{1}{2}}{3}"
        val sanitized = LatexSanitizer.sanitize(input)
        assertEquals("((((1)/(2)))/(3))", sanitized)
    }

    @Test
    fun testRoots() {
        val sqrtInput = "\\sqrt{16}"
        assertEquals("sqrt(16)", LatexSanitizer.sanitize(sqrtInput))

        val nthRootInput = "\\sqrt[3]{27}"
        assertEquals("((27)^(1/(3)))", LatexSanitizer.sanitize(nthRootInput))
    }

    @Test
    fun testOperatorsAndBrackets() {
        val input = "2 \\times 3 + 4 \\div 2 - \\left( 1 + 1 \\right)"
        val sanitized = LatexSanitizer.sanitize(input)
        assertEquals("2 * 3 + 4 / 2 - ( 1 + 1 )", sanitized)
    }

    @Test
    fun testGreekAndTrig() {
        val input = "\\sin(\\pi) + \\cos(\\theta)"
        val sanitized = LatexSanitizer.sanitize(input)
        assertEquals("sin ( pi ) + cos ( theta )", sanitized)
    }

    @Test
    fun testSuperscript() {
        val input = "x^{2} + y³"
        val sanitized = LatexSanitizer.sanitize(input)
        assertEquals("x^(2) + y^(3)", sanitized)
    }
}
