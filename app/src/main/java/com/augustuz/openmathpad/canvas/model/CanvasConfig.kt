package com.augustuz.openmathpad.canvas.model

import androidx.compose.ui.graphics.Color

/**
 * Geometric shape modes for GeoGebra-style geometry drawing.
 */
enum class GeometricShape {
    NONE,
    LINE,
    VECTOR_ARROW,
    RECTANGLE,
    CIRCLE
}

/**
 * Configuration state for canvas tools, geometry snapping, and grid rendering.
 *
 * @param activeTool Tool currently selected by user (PEN, HIGHLIGHTER, ERASER).
 * @param activeShape Active geometric shape mode (NONE, LINE, VECTOR_ARROW, RECTANGLE, CIRCLE).
 * @param penColor Color for Pen tool (default: classic school Royal Blue #1565C0).
 * @param highlighterColor Color for Highlighter tool (default: fluorescent yellow with 45% alpha).
 * @param penWidth Base stroke width for Pen (default 2.0f for crisp ballpoint inking).
 * @param highlighterWidth Base stroke width for Highlighter (default 20.0f).
 * @param eraserRadius Touch radius for Stroke Eraser hit testing.
 * @param touchModeEnabled When false, non-stylus touches (finger/palm) are rejected.
 *                         When true, finger touch drawing is allowed.
 * @param rulerModeEnabled When true, all drawn strokes snap to straight lines.
 * @param showGrid When true, renders classic squared notebook paper (тетрадь в клеточку).
 * @param showCoordinateAxes When true, renders a Cartesian coordinate system with arrows, ticks, and origin.
 * @param isDarkMode When true, renders canvas in dark mode; when false, clean white school notebook paper.
 */
data class CanvasConfig(
    val activeTool: CanvasTool = CanvasTool.PEN,
    val activeShape: GeometricShape = GeometricShape.NONE,
    val penColor: Color = AdaptiveInkColor.Default,
    val highlighterColor: Color = Color(0xFFFFEB3B).copy(alpha = 0.45f), // Fluorescent Yellow
    val penWidth: Float = 1.5f,
    val highlighterWidth: Float = 20.0f,
    val eraserRadius: Float = 24.0f,
    val touchModeEnabled: Boolean = false,
    val rulerModeEnabled: Boolean = false,
    val showGrid: Boolean = true,
    val showCoordinateAxes: Boolean = false,
    val isDarkMode: Boolean = false
)
