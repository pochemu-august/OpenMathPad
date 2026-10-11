package com.augustuz.openmathpad.canvas.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.augustuz.openmathpad.canvas.state.CanvasViewModel

/**
 * Backward-compatible delegator to [InfiniteCanvas].
 */
@Composable
fun StylusCanvas(
    modifier: Modifier = Modifier,
    viewModel: CanvasViewModel = viewModel(),
    showToolbar: Boolean = true,
    showDiagnostics: Boolean = true,
    backgroundColor: Color = Color(0xFFFAFAFA)
) {
    InfiniteCanvas(
        modifier = modifier,
        viewModel = viewModel,
        showToolbar = showToolbar,
        showDiagnostics = showDiagnostics,
        backgroundColor = backgroundColor
    )
}
