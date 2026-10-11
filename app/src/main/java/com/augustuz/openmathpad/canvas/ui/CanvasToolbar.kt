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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.RestartAlt
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
import com.augustuz.openmathpad.canvas.state.CanvasUiState
import kotlin.math.roundToInt

val PresetPenColors = listOf(
    Color(0xFF1E88E5), // Material Blue
    Color(0xFF212121), // Charcoal / Graphite
    Color(0xFFE53935), // Crimson Red
    Color(0xFF43A047), // Vivid Emerald Green
    Color(0xFF8E24AA), // Deep Violet
    Color(0xFFFB8C00)  // Amber Orange
)

val PresetStrokeWidths = listOf(
    3.0f to "Thin",
    6.0f to "Medium",
    12.0f to "Thick"
)

/**
 * Minimalist floating toolbar for Infinite Canvas:
 * Pen, Highlighter, Stroke Width, Color Palette, Undo, Redo, Clear,
 * Add Image / Task button, and Quick Zoom reset.
 */
@Composable
fun CanvasToolbar(
    uiState: CanvasUiState,
    onToolSelected: (CanvasTool) -> Unit,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onTouchModeToggled: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onAddImageClicked: () -> Unit = {},
    onResetZoom: () -> Unit = {},
    onCalculateClicked: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Tool Selection & Action Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pen Selector
                FilterChip(
                    selected = uiState.config.activeTool == CanvasTool.PEN,
                    onClick = { onToolSelected(CanvasTool.PEN) },
                    label = { Text("Pen") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "Pen Tool"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                // Highlighter Selector
                FilterChip(
                    selected = uiState.config.activeTool == CanvasTool.HIGHLIGHTER,
                    onClick = { onToolSelected(CanvasTool.HIGHLIGHTER) },
                    label = { Text("Highlighter") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = "Highlighter Tool"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )

                // Eraser Selector
                FilterChip(
                    selected = uiState.config.activeTool == CanvasTool.ERASER,
                    onClick = { onToolSelected(CanvasTool.ERASER) },
                    label = { Text("Eraser") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Eraser Tool"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )

                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Add Image / Task Button
                Button(
                    onClick = onAddImageClicked,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add Image or Task",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Task",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                // Quick Zoom Reset Button
                val zoomPct = (uiState.zoomScale * 100f).roundToInt()
                OutlinedButton(
                    onClick = onResetZoom,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
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

                // Math Overlay Calculator Toggle
                IconButton(onClick = onCalculateClicked) {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = "Math Calculator",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Undo / Redo / Clear
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

            // Bottom Row: Color Palette, Thickness, and Palm Rejection / Touch Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Color Swatches
                AnimatedVisibility(
                    visible = uiState.config.activeTool != CanvasTool.ERASER,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PresetPenColors.forEach { color ->
                            val isSelected = uiState.config.penColor == color
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            )
                                        } else {
                                            Modifier.border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PresetStrokeWidths.forEach { (width, label) ->
                            val isSelected = uiState.config.penWidth == width
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
                onUndo = {},
                onRedo = {},
                onClear = {}
            )
        }
    }
}
