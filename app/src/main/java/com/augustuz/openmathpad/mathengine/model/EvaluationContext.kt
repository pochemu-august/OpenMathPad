package com.augustuz.openmathpad.mathengine.model

enum class AngleMode {
    RADIANS,
    DEGREES
}

data class MathEngineConfig(
    val angleMode: AngleMode = AngleMode.RADIANS,
    val enableImplicitMultiplication: Boolean = true,
    val maxDecimalPlaces: Int = 10,
    val formatScientificThreshold: Double = 1e10,
    val doubleTolerance: Double = 1e-12
)

data class EvaluationContext(
    val variables: Map<String, Double> = emptyMap(),
    val constants: Map<String, Double> = DEFAULT_CONSTANTS,
    val config: MathEngineConfig = MathEngineConfig()
) {
    fun withVariable(name: String, value: Double): EvaluationContext {
        return copy(variables = variables + (name to value))
    }

    fun withVariables(newVariables: Map<String, Double>): EvaluationContext {
        return copy(variables = variables + newVariables)
    }

    companion object {
        val DEFAULT_CONSTANTS: Map<String, Double> = mapOf(
            "pi" to Math.PI,
            "π" to Math.PI,
            "PI" to Math.PI,
            "e" to Math.E,
            "E" to Math.E,
            "tau" to (2.0 * Math.PI),
            "τ" to (2.0 * Math.PI),
            "phi" to ((1.0 + Math.sqrt(5.0)) / 2.0),
            "ϕ" to ((1.0 + Math.sqrt(5.0)) / 2.0),
            "φ" to ((1.0 + Math.sqrt(5.0)) / 2.0)
        )
    }
}
