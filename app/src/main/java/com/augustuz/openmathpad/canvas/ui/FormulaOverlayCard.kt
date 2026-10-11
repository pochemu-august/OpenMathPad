package com.augustuz.openmathpad.canvas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.augustuz.openmathpad.canvas.state.CanvasUiState
import com.augustuz.openmathpad.mathengine.model.AngleMode
import com.augustuz.openmathpad.mathengine.model.EvaluationStep
import com.augustuz.openmathpad.mathengine.model.MathResult
import com.augustuz.openmathpad.ui.theme.OpenMathPadTheme

/**
 * Formula & Calculation Overlay UI.
 * Displays editable formula expression, computed result (decimal/symbolic/error),
 * calculation steps, variable state, angle mode toggle, and quick copy/insert options.
 */
@Composable
fun FormulaOverlayCard(
    uiState: CanvasUiState,
    onFormulaChange: (String) -> Unit,
    onEvaluateFormula: (String?) -> Unit,
    onRecognizeStrokes: () -> Unit = {},
    onToggleExpanded: () -> Unit,
    onCloseOverlay: () -> Unit,
    onSetAngleMode: (AngleMode) -> Unit,
    onSetVariable: (String, Double) -> Unit,
    onRemoveVariable: (String) -> Unit,
    onInsertResultToCanvas: (String) -> Unit,
    onClearHistory: () -> Unit,
    onConvertToTypeset: () -> Unit = {},
    onToggleTypesetMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedToClipboard by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Steps, 1: Variables, 2: History

    Card(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = "Math Engine",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Formula & Calculation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Math Engine Active",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Angle Mode Toggle Chip (RAD / DEG)
                    val currentAngleMode = uiState.evaluationContext.config.angleMode
                    FilterChip(
                        selected = true,
                        onClick = {
                            val nextMode = if (currentAngleMode == AngleMode.RADIANS) AngleMode.DEGREES else AngleMode.RADIANS
                            onSetAngleMode(nextMode)
                        },
                        label = {
                            Text(
                                text = if (currentAngleMode == AngleMode.RADIANS) "RAD" else "DEG",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )

                    // Expand / Collapse Details Button
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            imageVector = if (uiState.isOverlayExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (uiState.isOverlayExpanded) "Collapse Details" else "Expand Details"
                        )
                    }

                    // Close Overlay Button
                    IconButton(onClick = onCloseOverlay) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Overlay"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Formula Text Field with Calculation Actions
            OutlinedTextField(
                value = uiState.formulaText,
                onValueChange = onFormulaChange,
                label = { Text("Mathematical Expression / LaTeX") },
                placeholder = { Text("e.g. 2 * pi * 5 or sin(pi / 3)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onEvaluateFormula(null) }),
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Evaluate Trigger Button
                        IconButton(
                            onClick = { onEvaluateFormula(null) },
                            enabled = uiState.formulaText.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Calculate Formula",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Computed Result Section
            uiState.currentResult?.let { result ->
                ResultDisplaySection(
                    result = result,
                    copiedToClipboard = copiedToClipboard,
                    onCopyResult = { formattedText ->
                        clipboardManager.setText(AnnotatedString(formattedText))
                        copiedToClipboard = true
                    },
                    onInsertToCanvas = { resultText ->
                        onInsertResultToCanvas(resultText)
                    },
                    onConvertToTypeset = onConvertToTypeset,
                    onToggleTypesetMode = onToggleTypesetMode
                )
            }

            // Expanded Section: Steps, Variable State, and Calculation History
            AnimatedVisibility(
                visible = uiState.isOverlayExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Tab Row
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Steps") },
                            icon = { Icon(Icons.Default.Functions, contentDescription = "Steps", modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Variables (${uiState.evaluationContext.variables.size})") },
                            icon = { Icon(Icons.Default.Tune, contentDescription = "Variables", modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("History (${uiState.calculationHistory.size})") },
                            icon = { Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(18.dp)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.heightIn(max = 240.dp)) {
                        when (selectedTab) {
                            0 -> CalculationStepsTab(result = uiState.currentResult)
                            1 -> VariableStateTab(
                                variables = uiState.evaluationContext.variables,
                                onSetVariable = onSetVariable,
                                onRemoveVariable = onRemoveVariable
                            )
                            2 -> CalculationHistoryTab(
                                history = uiState.calculationHistory,
                                onSelectExpression = { expr -> onEvaluateFormula(expr) },
                                onClearHistory = onClearHistory
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultDisplaySection(
    result: MathResult,
    copiedToClipboard: Boolean,
    isTypesetReplaceMode: Boolean = true,
    onCopyResult: (String) -> Unit,
    onInsertToCanvas: (String) -> Unit,
    onConvertToTypeset: () -> Unit = {},
    onToggleTypesetMode: () -> Unit = {}
) {
    when (result) {
        is MathResult.Success -> {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Result",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        result.symbolicRepresentation?.let { symbolic ->
                            AssistChip(
                                onClick = {},
                                label = { Text(symbolic, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val resultText = if (result.formattedResult.startsWith("=") || result.formattedResult.contains("=")) {
                        result.formattedResult
                    } else {
                        "= ${result.formattedResult}"
                    }

                    Text(
                        text = resultText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Copy Button
                        OutlinedButton(
                            onClick = { onCopyResult(result.formattedResult) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (copiedToClipboard) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Result",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (copiedToClipboard) "Copied!" else "Copy", fontSize = 12.sp)
                        }

                        // Convert to Typeset Button
                        OutlinedButton(
                            onClick = onConvertToTypeset,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Convert to Typeset",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Typeset", fontSize = 12.sp)
                        }

                        // Insert to Canvas Button
                        val insertText = if (result.formattedResult.contains("=")) {
                            result.formattedResult
                        } else {
                            "${result.rawExpression} = ${result.formattedResult}"
                        }
                        Button(
                            onClick = { onInsertToCanvas(insertText) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PostAdd,
                                contentDescription = "Insert into Canvas",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Insert", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Typeset Mode Toggle Chip
                    FilterChip(
                        selected = isTypesetReplaceMode,
                        onClick = onToggleTypesetMode,
                        label = {
                            Text(
                                text = if (isTypesetReplaceMode) "Mode: Replace Strokes" else "Mode: Overlay Typeset",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        is MathResult.Failure -> {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Calculation Error",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(result.errorType.name, fontSize = 10.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                labelColor = MaterialTheme.colorScheme.onError
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = result.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculationStepsTab(result: MathResult?) {
    if (result !is MathResult.Success || result.evaluationSteps.isEmpty()) {
        Text(
            text = "No intermediate calculation steps available.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp)
        )
    } else {
        val steps = result.evaluationSteps
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(steps) { step ->
                CalculationStepRow(step = step)
            }
        }
    }
}

@Composable
private fun CalculationStepRow(step: EvaluationStep) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${step.stepNumber}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = step.expressionState,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun VariableStateTab(
    variables: Map<String, Double>,
    onSetVariable: (String, Double) -> Unit,
    onRemoveVariable: (String) -> Unit
) {
    var varName by remember { mutableStateOf("") }
    var varValue by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // New Variable Input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = varName,
                onValueChange = { varName = it },
                label = { Text("Var (e.g. x)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = varValue,
                onValueChange = { varValue = it },
                label = { Text("Val (e.g. 5)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val doubleVal = varValue.toDoubleOrNull()
                    if (varName.isNotBlank() && doubleVal != null) {
                        onSetVariable(varName.trim(), doubleVal)
                        varName = ""
                        varValue = ""
                    }
                },
                enabled = varName.isNotBlank() && varValue.toDoubleOrNull() != null
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Variable")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (variables.isEmpty()) {
            Text(
                text = "No custom variables assigned.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(variables.toList()) { (name, value) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$name = $value",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { onRemoveVariable(name) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove Variable",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculationHistoryTab(
    history: List<MathResult>,
    onSelectExpression: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    if (history.isEmpty()) {
        Text(
            text = "Calculation history is empty.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp)
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClearHistory) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear History",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history) { res ->
                if (res is MathResult.Success) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectExpression(res.rawExpression) }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = res.rawExpression,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "= ${res.formattedResult}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Draw,
                                contentDescription = "Use Expression",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FormulaOverlayCardPreview() {
    OpenMathPadTheme {
        FormulaOverlayCard(
            uiState = CanvasUiState(
                formulaText = "2 * pi * 5",
                currentResult = MathResult.Success(
                    value = 31.41592653589793,
                    formattedResult = "31.4159265359",
                    symbolicRepresentation = "10*π",
                    rawExpression = "2 * pi * 5",
                    sanitizedExpression = "2 * pi * 5",
                    ast = com.augustuz.openmathpad.mathengine.model.ExprNode.NumberNode(31.4159)
                )
            ),
            onFormulaChange = {},
            onEvaluateFormula = {},
            onRecognizeStrokes = {},
            onToggleExpanded = {},
            onCloseOverlay = {},
            onSetAngleMode = {},
            onSetVariable = { _, _ -> },
            onRemoveVariable = {},
            onInsertResultToCanvas = {},
            onClearHistory = {}
        )
    }
}
