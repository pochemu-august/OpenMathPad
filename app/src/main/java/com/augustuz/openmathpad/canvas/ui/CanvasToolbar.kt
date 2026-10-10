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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Functions
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

val PresetPenColors = listOf(
    Color(0xFF1E88E5), // Material Blue
    Color(0xFF000000), // Pure Black
    Color(0xFFE53935), // Crimson Red
    Color(0xFF43A047), // Vivid Green
    Color(0xFF8E24AA), // Deep Purple
    Color(0xFFFB8C00)  // Amber Orange
)

val PresetStrokeWidths = listOf(
    3.0f to "Thin",
    6.0f to "Medium",
    12.0f to "Thick"
)

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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Tool Selection & Action Controls (Horizontally Scrollable)
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

                // Calculate Action Button
                Button(
                    onClick = onCalculateClicked,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = "Calculate Formula",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "Calculate",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 4.dp),
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

                IconButton(
                    onClick = onClear,
                    enabled = uiState.strokes.isNotEmpty() || uiState.canvasTextAnnotations.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Canvas",
                        tint = if (uiState.strokes.isNotEmpty() || uiState.canvasTextAnnotations.isNotEmpty()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }

            // Bottom Row: Color Palette, Thickness, and Touch Mode Toggle (Horizontally Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Color Swatches (if Pen or Highlighter active)
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
                                    .size(28.dp)
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

                // Touch-Drawing Toggle Mode (Hardware Palm Rejection Toggle)
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
                            text = if (uiState.config.touchModeEnabled) "Touch On" else "Stylus Only",
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

@Preview(showBackground = true, widthDp = 360, name = "Compact Width (Phone / Split Screen)")
@Composable
private fun CanvasToolbarCompactPreview() {
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
                onClear = {},
                onCalculateClicked = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 800, name = "Expanded Width (Tablet / Wide Screen)")
@Composable
private fun CanvasToolbarExpandedPreview() {
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
                onClear = {},
                onCalculateClicked = {}
            )
        }
    }
}
