package com.augustuz.openmathpad.canvas.model

import androidx.compose.ui.graphics.Color

/**
 * Text annotation rendered directly on the drawing canvas (e.g. inserted calculation results).
 */
data class CanvasTextAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val x: Float,
    val y: Float,
    val fontSizeSp: Float = 20f,
    val color: Color = Color(0xFF1E88E5)
)
