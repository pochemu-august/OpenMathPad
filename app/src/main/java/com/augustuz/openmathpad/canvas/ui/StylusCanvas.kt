package com.augustuz.openmathpad.canvas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.augustuz.openmathpad.canvas.model.CanvasTextAnnotation
import com.augustuz.openmathpad.canvas.model.DrawingStroke
import com.augustuz.openmathpad.canvas.state.CanvasViewModel
import com.augustuz.openmathpad.canvas.state.InputDiagnostics
import com.augustuz.openmathpad.ui.theme.OpenMathPadTheme

/**
 * Tablet canvas component supporting stylus MotionEvent processing,
 * hardware palm rejection, pressure sensitivity scaling, Bezier path smoothing,
 * and adaptive Material 3 formula & calculation overlay.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StylusCanvas(
    modifier: Modifier = Modifier,
    viewModel: CanvasViewModel = viewModel(),
    showToolbar: Boolean = true,
    showDiagnostics: Boolean = true,
    backgroundColor: Color = Color(0xFFFAFAFA)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLargeTabletWidth = maxWidth >= 840.dp

        // Active Canvas Surface with cached hardware-accelerated grid background
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .drawWithCache {
                    val gridSize = 40.dp.toPx()
                    val dotRadius = 1.5.dp.toPx()
                    val dotColor = Color.LightGray.copy(alpha = 0.5f)
                    val points = ArrayList<Offset>()
                    var x = gridSize
                    while (x < size.width) {
                        var y = gridSize
                        while (y < size.height) {
                            points.add(Offset(x, y))
                            y += gridSize
                        }
                        x += gridSize
                    }

                    onDrawBehind {
                        drawPoints(
                            points = points,
                            pointMode = androidx.compose.ui.graphics.PointMode.Points,
                            color = dotColor,
                            strokeWidth = dotRadius * 2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                .pointerInteropFilter { motionEvent ->
                    viewModel.processMotionEvent(motionEvent)
                }
        ) {
            // Render all completed drawing strokes
            val strokes = uiState.strokes
            for (i in strokes.indices) {
                drawStroke(strokes[i])
            }

            // Render current active in-progress stroke
            uiState.currentActiveStroke?.let { stroke ->
                drawStroke(stroke)
            }

            // Render text annotations inserted from calculation overlay
            val annotations = uiState.canvasTextAnnotations
            for (i in annotations.indices) {
                drawTextAnnotation(textMeasurer, annotations[i])
            }
        }

        // Floating Expressive Toolbar
        if (showToolbar) {
            CanvasToolbar(
                uiState = uiState,
                onToolSelected = viewModel::setTool,
                onColorSelected = viewModel::setPenColor,
                onStrokeWidthChanged = viewModel::setPenWidth,
                onTouchModeToggled = viewModel::toggleTouchMode,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onClear = viewModel::clear,
                onCalculateClicked = {
                    if (uiState.isOverlayVisible) {
                        viewModel.recognizeAndEvaluateStrokes()
                    } else {
                        viewModel.setOverlayVisible(true)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // Formula & Calculation Overlay (Tablet Adaptive Side Panel vs Bottom Sheet Card)
        AnimatedVisibility(
            visible = uiState.isOverlayVisible,
            enter = if (isLargeTabletWidth) {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            } else {
                slideInVertically(initialOffsetY = { it }) + fadeIn()
            },
            exit = if (isLargeTabletWidth) {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            } else {
                slideOutVertically(targetOffsetY = { it }) + fadeOut()
            },
            modifier = if (isLargeTabletWidth) {
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 16.dp, bottom = 16.dp)
                    .width(420.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            }
        ) {
            FormulaOverlayCard(
                uiState = uiState,
                onFormulaChange = viewModel::setFormulaText,
                onEvaluateFormula = { viewModel.evaluateFormula(it) },
                onRecognizeStrokes = viewModel::recognizeAndEvaluateStrokes,
                onToggleExpanded = viewModel::toggleOverlayExpanded,
                onCloseOverlay = { viewModel.setOverlayVisible(false) },
                onSetAngleMode = viewModel::setAngleMode,
                onSetVariable = viewModel::setVariable,
                onRemoveVariable = viewModel::removeVariable,
                onInsertResultToCanvas = { text -> viewModel.insertResultToCanvas(text) },
                onClearHistory = viewModel::clearCalculationHistory
            )
        }

        // Input Telemetry & Palm Rejection Diagnostics Overlay
        if (showDiagnostics) {
            DiagnosticsBadge(
                diagnostics = uiState.diagnostics,
                touchModeEnabled = uiState.config.touchModeEnabled,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Renders text annotations inserted on canvas.
 */
private fun DrawScope.drawTextAnnotation(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    annotation: CanvasTextAnnotation
) {
    drawText(
        textMeasurer = textMeasurer,
        text = annotation.text,
        topLeft = Offset(annotation.x, annotation.y),
        style = annotation.textStyle
    )
}

/**
 * Renders a single stroke on the DrawScope canvas.
 */
private fun DrawScope.drawStroke(stroke: DrawingStroke) {
    if (stroke.points.isEmpty()) return

    val color = stroke.color
    val alpha = stroke.alpha

    if (stroke.points.size == 1) {
        val p = stroke.points[0]
        drawCircle(
            color = color,
            radius = p.width / 2f,
            center = Offset(p.x, p.y),
            alpha = alpha
        )
        return
    }

    if (stroke.isUniformWidth) {
        // Uniform stroke width path - uses cached path and cached Stroke style
        drawPath(
            path = stroke.path,
            color = color,
            alpha = alpha,
            style = stroke.strokeStyle
        )
    } else {
        // Pressure-varying stroke segment rendering
        val points = stroke.points
        var prevX = points[0].x
        var prevY = points[0].y
        var prevW = points[0].width

        for (i in 1 until points.size) {
            val curr = points[i]
            val avgWidth = (prevW + curr.width) / 2f

            drawLine(
                color = color,
                start = Offset(prevX, prevY),
                end = Offset(curr.x, curr.y),
                strokeWidth = avgWidth,
                cap = StrokeCap.Round,
                alpha = alpha
            )

            prevX = curr.x
            prevY = curr.y
            prevW = curr.width
        }
    }
}

/**
 * Draws a subtle dot grid pattern for math notebook feel.
 */
private fun DrawScope.drawGridBackground() {
    val gridSize = 40.dp.toPx()
    val dotRadius = 1.5.dp.toPx()
    val dotColor = Color.LightGray.copy(alpha = 0.5f)

    var x = gridSize
    while (x < size.width) {
        var y = gridSize
        while (y < size.height) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(x, y)
            )
            y += gridSize
        }
        x += gridSize
    }
}

/**
 * Telemetry badge providing visual feedback for hardware tool detection & palm rejection.
 */
@Composable
fun DiagnosticsBadge(
    diagnostics: InputDiagnostics,
    touchModeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tool: ${diagnostics.toolTypeName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (touchModeEnabled) " [Touch Mode]" else " [Stylus Only]",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (touchModeEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text = "Pressure: %.2f | Hist: %d".format(diagnostics.lastPressure, diagnostics.historicalPointsCount),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Accepted: %d | Rejected (Palm): %d".format(diagnostics.acceptedEventsCount, diagnostics.rejectedEventsCount),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (diagnostics.rejectedEventsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun StylusCanvasPreview() {
    OpenMathPadTheme {
        StylusCanvas()
    }
}
