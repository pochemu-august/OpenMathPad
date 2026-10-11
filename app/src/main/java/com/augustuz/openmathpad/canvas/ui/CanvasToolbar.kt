package com.augustuz.openmathpad.canvas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.augustuz.openmathpad.canvas.model.CanvasTool
import com.augustuz.openmathpad.canvas.model.GeometricShape
import com.augustuz.openmathpad.canvas.state.CanvasUiState
import kotlin.math.roundToInt

// Gitpod Modern Developer Palette
val PresetPenColorsLight = listOf(
    Color(0xFF111827), // Gitpod Pitch Black (Default)
    Color(0xFF1D4ED8), // Deep Royal Blue
    Color(0xFFC62828), // Crimson Red
    Color(0xFF059669), // Emerald Green
    Color(0xFF7C3AED), // Vibrant Violet
    Color(0xFFFF8800)  // Gitpod Orange
)

val PresetPenColorsDark = listOf(
    Color(0xFFFFFFFF), // Crisp White (Default in Dark Mode)
    Color(0xFF38BDF8), // Vibrant Cyan
    Color(0xFFFFAE33), // Gitpod Orange
    Color(0xFF4ADE80), // Pastel Mint Green
    Color(0xFFF472B6), // Pastel Rose Pink
    Color(0xFFA78BFA)  // Pastel Lavender
)

val PresetPenColors = PresetPenColorsLight

val PresetPenWidths = listOf(
    1.5f to "Thin 1.5",
    2.5f to "Normal 2.5",
    4.0f to "Thick 4.0"
)

// Semi-Transparent Highlighter Palette (45% alpha)
val PresetHighlighterColors = listOf(
    Color(0xFFFFEB3B).copy(alpha = 0.45f), // Fluorescent Yellow
    Color(0xFF69F0AE).copy(alpha = 0.45f), // Neon Green
    Color(0xFFFF80AB).copy(alpha = 0.45f), // Soft Pink
    Color(0xFF80D8FF).copy(alpha = 0.45f), // Pastel Blue
    Color(0xFFFFB74D).copy(alpha = 0.45f)  // Warm Orange
)

val PresetHighlighterWidths = listOf(
    12.0f to "Thin 12",
    20.0f to "Normal 20",
    32.0f to "Thick 32"
)

/**
 * Modern floating pill toolbar for GeoGebra-inspired Math Scratchpad:
 * Pen, Layered Highlighter, Eraser, Geometric Shapes (Line, Arrow, Box, Circle),
 * Squared Paper Grid, Cartesian Axes (XY), Task Image Import, Theme Toggle, Undo/Redo/Clear.
 */
@Composable
fun CanvasToolbar(
    uiState: CanvasUiState,
    onToolSelected: (CanvasTool) -> Unit,
    onActiveShapeSelected: (GeometricShape) -> Unit = {},
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onTouchModeToggled: () -> Unit,
    onToggleRulerMode: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleCoordinateAxes: () -> Unit,
    onToggleDarkMode: () -> Unit = {},
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onAddImageClicked: () -> Unit = {},
    onResetZoom: () -> Unit = {}
) {
    val isDark = uiState.config.isDarkMode
    val primaryAccent = if (isDark) Color(0xFFFFAE33) else Color(0xFF1D4ED8)
    val chipSelectedContainer = if (isDark) Color(0xFF2E333D) else Color(0xFFEFF6FF)
    val chipSelectedContent = if (isDark) Color(0xFFFFAE33) else Color(0xFF1D4ED8)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1A1D24) else Color(0xFFFFFFFF)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isDark) Color(0xFF2E333D) else Color(0xFFE2E8F0)
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Core Tools, Shapes, Grid, Axes, Theme, Media, Canvas Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Pen Tool (Ballpoint Pen)
                FilterChip(
                    selected = uiState.config.activeTool == CanvasTool.PEN && uiState.config.activeShape == GeometricShape.NONE,
                    onClick = {
                        onToolSelected(CanvasTool.PEN)
                        onActiveShapeSelected(GeometricShape.NONE)
                    },
                    label = { Text("Pen") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "Ballpoint Pen"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedContainer,
                        selectedLabelColor = chipSelectedContent,
                        selectedLeadingIconColor = chipSelectedContent
                    )
                )

                // 2. Highlighter Tool (Layered Under Ink)
                FilterChip(
                    selected = uiState.config.activeTool == CanvasTool.HIGHLIGHTER,
                    onClick = {
                        onToolSelected(CanvasTool.HIGHLIGHTER)
                        onActiveShapeSelected(GeometricShape.NONE)
                    },
                    label = { Text("Highlighter") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = "Highlighter Tool"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isDark) Color(0xFF332B1A) else Color(0xFFFEF3C7),
                        selectedLabelColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                        selectedLeadingIconColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
                    )
                )

                // 3. Eraser Tool
                FilterChip(
                    selected = uiState.config.activeTool == CanvasTool.ERASER,
                    onClick = {
                        onToolSelected(CanvasTool.ERASER)
                        onActiveShapeSelected(GeometricShape.NONE)
                    },
                    label = { Text("Eraser") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Eraser Tool"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isDark) Color(0xFF3B1D24) else Color(0xFFFEE2E2),
                        selectedLabelColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                        selectedLeadingIconColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                    )
                )

                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 4. Geometric Shapes (GeoGebra Tools)
                FilterChip(
                    selected = uiState.config.activeShape == GeometricShape.LINE,
                    onClick = {
                        val next = if (uiState.config.activeShape == GeometricShape.LINE) GeometricShape.NONE else GeometricShape.LINE
                        onActiveShapeSelected(next)
                    },
                    label = { Text("Line") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = "Straight Line"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedContainer,
                        selectedLabelColor = chipSelectedContent,
                        selectedLeadingIconColor = chipSelectedContent
                    )
                )

                FilterChip(
                    selected = uiState.config.activeShape == GeometricShape.VECTOR_ARROW,
                    onClick = {
                        val next = if (uiState.config.activeShape == GeometricShape.VECTOR_ARROW) GeometricShape.NONE else GeometricShape.VECTOR_ARROW
                        onActiveShapeSelected(next)
                    },
                    label = { Text("Vector") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingFlat,
                            contentDescription = "Vector Arrow"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedContainer,
                        selectedLabelColor = chipSelectedContent,
                        selectedLeadingIconColor = chipSelectedContent
                    )
                )

                FilterChip(
                    selected = uiState.config.activeShape == GeometricShape.RECTANGLE,
                    onClick = {
                        val next = if (uiState.config.activeShape == GeometricShape.RECTANGLE) GeometricShape.NONE else GeometricShape.RECTANGLE
                        onActiveShapeSelected(next)
                    },
                    label = { Text("Box") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CropSquare,
                            contentDescription = "Rectangle Box"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedContainer,
                        selectedLabelColor = chipSelectedContent,
                        selectedLeadingIconColor = chipSelectedContent
                    )
                )

                FilterChip(
                    selected = uiState.config.activeShape == GeometricShape.CIRCLE,
                    onClick = {
                        val next = if (uiState.config.activeShape == GeometricShape.CIRCLE) GeometricShape.NONE else GeometricShape.CIRCLE
                        onActiveShapeSelected(next)
                    },
                    label = { Text("Circle") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Circle"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedContainer,
                        selectedLabelColor = chipSelectedContent,
                        selectedLeadingIconColor = chipSelectedContent
                    )
                )

                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 5. Classic Notebook Grid Toggle (Squared / Blank)
                FilterChip(
                    selected = uiState.config.showGrid,
                    onClick = onToggleGrid,
                    label = { Text(if (uiState.config.showGrid) "Grid" else "Blank") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (uiState.config.showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                            contentDescription = "Grid Toggle"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedContainer,
                        selectedLabelColor = chipSelectedContent,
                        selectedLeadingIconColor = chipSelectedContent
                    )
                )

                // 6. Cartesian Coordinate Axes Toggle (XY)
                FilterChip(
                    selected = uiState.config.showCoordinateAxes,
                    onClick = onToggleCoordinateAxes,
                    label = { Text("XY Axes") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Cartesian Coordinate Axes"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedContainer,
                        selectedLabelColor = chipSelectedContent,
                        selectedLeadingIconColor = chipSelectedContent
                    )
                )

                // 7. Dark / Light Theme Toggle
                IconButton(onClick = onToggleDarkMode) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Dark/Light Mode",
                        tint = if (isDark) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 8. Add Task / Image Insertion Button
                Button(
                    onClick = onAddImageClicked,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryAccent,
                        contentColor = if (isDark) Color(0xFF121417) else Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add Task Image",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Task",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                // 9. Quick Zoom Reset Button
                val zoomPct = (uiState.zoomScale * 100f).roundToInt()
                OutlinedButton(
                    onClick = onResetZoom,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = primaryAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Zoom to 100%",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$zoomPct%",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 10. Undo / Redo / Clear
                IconButton(onClick = onUndo, enabled = uiState.canUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo"
                    )
                }

                IconButton(onClick = onRedo, enabled = uiState.canRedo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo"
                    )
                }

                val hasContent = uiState.strokes.isNotEmpty() ||
                        uiState.canvasTextAnnotations.isNotEmpty() ||
                        uiState.images.isNotEmpty()

                IconButton(
                    onClick = onClear,
                    enabled = hasContent
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Canvas",
                        tint = if (hasContent) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }

            // Bottom Row: Dynamic Color Swatches, Thickness Presets, and Palm Rejection Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Color Swatches (Pen or Highlighter palette)
                AnimatedVisibility(
                    visible = uiState.config.activeTool != CanvasTool.ERASER,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val isHighlighter = uiState.config.activeTool == CanvasTool.HIGHLIGHTER
                    val colors = if (isHighlighter) PresetHighlighterColors else if (isDark) PresetPenColorsDark else PresetPenColorsLight
                    val currentColor = if (isHighlighter) uiState.config.highlighterColor else uiState.config.penColor

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { color ->
                            val isSelected = currentColor == color
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = primaryAccent,
                                                shape = CircleShape
                                            )
                                        } else {
                                            Modifier.border(
                                                width = 1.dp,
                                                color = if (isDark) Color(0xFF383C45) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            )
                                        }
                                    )
                                    .clickable { onColorSelected(color) }
                            )
                        }
                    }
                }

                // Stroke Width Selectors
                AnimatedVisibility(
                    visible = uiState.config.activeTool != CanvasTool.ERASER,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val isHighlighter = uiState.config.activeTool == CanvasTool.HIGHLIGHTER
                    val widths = if (isHighlighter) PresetHighlighterWidths else PresetPenWidths
                    val currentWidth = if (isHighlighter) uiState.config.highlighterWidth else uiState.config.penWidth

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        widths.forEach { (width, label) ->
                            val isSelected = currentWidth == width
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .clickable { onStrokeWidthChanged(width) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (uiState.config.activeTool != CanvasTool.ERASER) {
                    VerticalDivider(
                        modifier = Modifier
                            .height(24.dp)
                            .padding(horizontal = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                // Touch-Drawing Toggle Mode (Hardware Palm Rejection Mode)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (uiState.config.touchModeEnabled) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    modifier = Modifier.clickable { onTouchModeToggled() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.config.touchModeEnabled) Icons.Default.TouchApp else Icons.Default.DoNotDisturb,
                            contentDescription = "Touch Mode Toggle",
                            tint = if (uiState.config.touchModeEnabled) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(18.dp)
                        )

                        Text(
                            text = if (uiState.config.touchModeEnabled) "Touch On" else "Stylus Only (Palm Reject)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (uiState.config.touchModeEnabled) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )

                        Switch(
                            checked = uiState.config.touchModeEnabled,
                            onCheckedChange = { onTouchModeToggled() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 800)
@Composable
private fun CanvasToolbarPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CanvasToolbar(
                uiState = CanvasUiState(),
                onToolSelected = {},
                onColorSelected = {},
                onStrokeWidthChanged = {},
                onTouchModeToggled = {},
                onToggleRulerMode = {},
                onToggleGrid = {},
                onToggleCoordinateAxes = {},
                onUndo = {},
                onRedo = {},
                onClear = {}
            )
        }
    }
}
