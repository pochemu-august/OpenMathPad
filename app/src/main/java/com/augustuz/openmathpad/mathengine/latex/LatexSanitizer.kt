package com.augustuz.openmathpad.mathengine.latex

object LatexSanitizer {

    /**
     * Sanitizes and translates a LaTeX or formatted math string into a standard
     * mathematical expression suitable for the MathLexer and MathParser.
     */
    fun sanitize(input: String): String {
        if (input.isBlank()) return ""

        var s = input.trim()

        // Remove surrounding math delimiters if present: $$...$$, $...$, \[...\], \(...\)
        s = s.removePrefix("$$").removeSuffix("$$")
        s = s.removePrefix("$").removeSuffix("$")
        s = s.removePrefix("\\[").removeSuffix("\\]")
        s = s.removePrefix("\\(").removeSuffix("\\)")

        // Replace text/mathrm macros
        s = removeTextMacros(s)

        // Standardize multiplication & division symbols
        s = s.replace("\\times", " * ")
            .replace("\\cdotp", " * ")
            .replace("\\cdot", " * ")
            .replace("\\ast", " * ")
            .replace("·", " * ")
            .replace("×", " * ")
            .replace("\\div", " / ")
            .replace("÷", " / ")

        // Standardize brackets & sizing commands
        s = s.replace("\\left(", "(")
            .replace("\\right)", ")")
            .replace("\\left[", "[")
            .replace("\\right]", "]")
            .replace("\\left\\{", "{")
            .replace("\\right\\}", "}")
            .replace("\\left|", " abs(")
            .replace("\\right|", ") ")
            .replace("\\{", "(")
            .replace("\\}", ")")

        // Standardize Greek letters & constants
        s = s.replace("\\pi", " pi ")
            .replace("\\tau", " tau ")
            .replace("\\phi", " phi ")
            .replace("\\theta", " theta ")
            .replace("π", " pi ")
            .replace("τ", " tau ")
            .replace("ϕ", " phi ")
            .replace("φ", " phi ")

        // Standardize functions
        s = s.replace("\\arcsin", " asin ")
            .replace("\\arccos", " acos ")
            .replace("\\arctan", " atan ")
            .replace("\\sin", " sin ")
            .replace("\\cos", " cos ")
            .replace("\\tan", " tan ")
            .replace("\\sinh", " sinh ")
            .replace("\\cosh", " cosh ")
            .replace("\\tanh", " tanh ")
            .replace("\\sqrt", "\\sqrt") // Processed below
            .replace("\\ln", " ln ")
            .replace("\\log", " log ")

        // Process fractions recursively (\frac and \dfrac)
        s = processFractions(s)

        // Process square roots and n-th roots (\sqrt[n]{x} and \sqrt{x})
        s = processRoots(s)

        // Process superscript expressions: x^{y} -> x^(y)
        s = processSuperscripts(s)

        // Convert unicode superscripts e.g. x² -> x^(2)
        s = processUnicodeSuperscripts(s)

        // Clean up double spaces or residual backslashes
        s = s.replace(Regex("\\\\([a-zA-Z]+)"), " $1 ") // fallback for any unhandled \func
        s = s.replace(Regex("\\s+"), " ").trim()

        return s
    }

    private fun removeTextMacros(input: String): String {
        var s = input
        val textRegex = Regex("\\\\(text|mathrm|mb|mathbf|mathtt|mathit)\\{([^}]*)\\}")
        while (textRegex.containsMatchIn(s)) {
            s = textRegex.replace(s, "$2")
        }
        return s
    }

    private fun processFractions(input: String): String {
        var s = input
        while (true) {
            val fracIndex = findFracIndex(s)
            if (fracIndex == -1) break

            val prefix = s.substring(0, fracIndex)
            val rest = s.substring(fracIndex)

            // Find starting index of frac command (\frac or \dfrac)
            val cmdLen = if (rest.startsWith("\\dfrac")) 6 else 5
            var idx = cmdLen

            // Skip whitespace
            while (idx < rest.length && rest[idx].isWhitespace()) idx++

            // Read numerator block
            val (num, numEndIdx) = readBlock(rest, idx)
            if (num == null) break

            idx = numEndIdx
            while (idx < rest.length && rest[idx].isWhitespace()) idx++

            // Read denominator block
            val (den, denEndIdx) = readBlock(rest, idx)
            if (den == null) break

            val replacement = "(($num)/($den))"
            s = prefix + replacement + rest.substring(denEndIdx)
        }
        return s
    }

    private fun findFracIndex(s: String): Int {
        val i1 = s.indexOf("\\frac")
        val i2 = s.indexOf("\\dfrac")
        return when {
            i1 == -1 -> i2
            i2 == -1 -> i1
            else -> minOf(i1, i2)
        }
    }

    private fun processRoots(input: String): String {
        var s = input
        while (true) {
            val sqrtIndex = s.indexOf("\\sqrt")
            if (sqrtIndex == -1) break

            val prefix = s.substring(0, sqrtIndex)
            val rest = s.substring(sqrtIndex + 5) // after \sqrt

            var idx = 0
            while (idx < rest.length && rest[idx].isWhitespace()) idx++

            if (idx < rest.length && rest[idx] == '[') {
                // n-th root \sqrt[n]{x}
                val (rootDegree, degreeEndIdx) = readBracketBlock(rest, idx, '[', ']')
                if (rootDegree != null) {
                    idx = degreeEndIdx
                    while (idx < rest.length && rest[idx].isWhitespace()) idx++
                    val (radicand, radicandEndIdx) = readBlock(rest, idx)
                    if (radicand != null) {
                        val replacement = "(($radicand)^(1/($rootDegree)))"
                        s = prefix + replacement + rest.substring(radicandEndIdx)
                        continue
                    }
                }
            }

            // Standard square root \sqrt{x}
            val (radicand, radicandEndIdx) = readBlock(rest, idx)
            if (radicand != null) {
                val replacement = "sqrt($radicand)"
                s = prefix + replacement + rest.substring(radicandEndIdx)
            } else {
                // Fallback: replace \sqrt with sqrt
                s = prefix + "sqrt" + rest
            }
        }
        return s
    }

    private fun processSuperscripts(input: String): String {
        var s = input
        val caretBraceRegex = Regex("\\^\\{([^}]*)\\}")
        s = caretBraceRegex.replace(s, "^($1)")
        return s
    }

    private fun processUnicodeSuperscripts(input: String): String {
        val map = mapOf(
            '⁰' to "0", '¹' to "1", '²' to "2", '³' to "3", '⁴' to "4",
            '⁵' to "5", '⁶' to "6", '⁷' to "7", '⁸' to "8", '⁹' to "9",
            '⁺' to "+", '⁻' to "-"
        )
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (map.containsKey(ch)) {
                val superSb = StringBuilder()
                var j = i
                while (j < input.length && map.containsKey(input[j])) {
                    superSb.append(map[input[j]])
                    j++
                }
                sb.append("^(").append(superSb.toString()).append(")")
                i = j
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun readBlock(s: String, startIndex: Int): Pair<String?, Int> {
        if (startIndex >= s.length) return Pair(null, startIndex)
        val ch = s[startIndex]
        return if (ch == '{') {
            readBracketBlock(s, startIndex, '{', '}')
        } else {
            // Single character argument e.g. \frac 1 2
            Pair(ch.toString(), startIndex + 1)
        }
    }

    private fun readBracketBlock(s: String, startIndex: Int, openChar: Char, closeChar: Char): Pair<String?, Int> {
        if (startIndex >= s.length || s[startIndex] != openChar) return Pair(null, startIndex)

        var depth = 0
        var i = startIndex
        val bodyStart = startIndex + 1

        while (i < s.length) {
            val c = s[i]
            if (c == openChar) depth++
            else if (c == closeChar) {
                depth--
                if (depth == 0) {
                    val content = s.substring(bodyStart, i)
                    return Pair(content, i + 1)
                }
            }
            i++
        }

        return Pair(null, startIndex)
    }
}
