package com.augustuz.openmathpad.canvas.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
) {
    /**
     * Lazily cached TextStyle to eliminate object allocations inside DrawScope loops.
     */
    val textStyle: TextStyle by lazy {
        TextStyle(
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}
