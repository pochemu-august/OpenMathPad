package com.augustuz.openmathpad.canvas.model

import androidx.compose.ui.graphics.Color

/**
 * Configuration state for canvas tools and input processing.
 *
 * @param activeTool Tool currently selected by user.
 * @param penColor Color for Pen tool.
 * @param highlighterColor Color for Highlighter tool.
 * @param penWidth Base stroke width for Pen.
 * @param highlighterWidth Base stroke width for Highlighter.
 * @param eraserRadius Touch radius for Stroke Eraser hit testing.
 * @param touchModeEnabled When false, non-stylus touches (finger/palm) are rejected.
 *                         When true, finger touch drawing is allowed for testing.
 * @param pressureSensitivity Scaling multiplier for stylus pressure responsiveness.
 */
data class CanvasConfig(
    val activeTool: CanvasTool = CanvasTool.PEN,
    val penColor: Color = Color(0xFF1E88E5), // Material 3 Expressive Blue
    val highlighterColor: Color = Color(0xFFFFEB3B), // Bright Highlighter Yellow
    val penWidth: Float = 6.0f,
    val highlighterWidth: Float = 24.0f,
    val eraserRadius: Float = 32.0f,
    val touchModeEnabled: Boolean = false,
    val pressureSensitivity: Float = 1.0f
)
