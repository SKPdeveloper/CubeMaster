@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.cubemaster.presentation.estimate

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cubemaster.core.model.MeasurementUnit
import com.cubemaster.core.model.shortLabelUa
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun EstimateScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: EstimateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    val context = LocalContext.current
    var showAddMaterial by remember { mutableStateOf(false) }
    var showAddLabor by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.exportUrl) {
        state.exportUrl?.let { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            viewModel.clearExportUrl()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CubeMasterTopBar(
                title = "Кошторис",
                onBack = onBack,
                actions = {
                    if (state.isGeneratingPdf) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp))
                    } else {
                        IconButton(onClick = { viewModel.generatePdf() }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF")
                        }
                        IconButton(onClick = { viewModel.generateXlsx() }) {
                            Icon(Icons.Default.TableChart, contentDescription = "XLSX")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().hazeSource(hazeState).imePadding()) {

            // Поле націнки
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Прорабська націнка:", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberInputField(
                            value = state.markupPercent.toInt().toString(),
                            onValueChange = { v -> v.replace(",", ".").toDoubleOrNull()?.let { viewModel.updateMarkup(it) } },
                            label = "",
                            unit = "%",
                            modifier = Modifier.width(80.dp)
                        )
                    }
                }
            }

            // Підсумок
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Загальна сума:", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${state.grandTotal.toUaStringRound()} грн",
                        style = MaterialTheme.typography.titleLarge,
                        color = CubeMasterColors.gold
                    )
                }
            }

            OrnamentalDivider()

            if (state.lines.isEmpty()) {
                EmptyState("Додайте рядки матеріалів або робіт")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Матеріали
                    val materials = state.lines.filter { it.lineType == "Material" }
                    val labor = state.lines.filter { it.lineType == "Labor" }

                    if (materials.isNotEmpty()) {
                        item { Text("Матеріали", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(materials, key = { it.id }) { line ->
                            EstimateLineRow(
                                line = line,
                                markup = state.markupPercent,
                                onToggleMarkup = { viewModel.toggleMarkup(line.id) },
                                onDelete = { viewModel.removeLine(line.id) },
                                hazeState = hazeState
                            )
                        }
                    }

                    if (labor.isNotEmpty()) {
                        item { Text("Роботи", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(labor, key = { it.id }) { line ->
                            EstimateLineRow(
                                line = line,
                                markup = state.markupPercent,
                                onToggleMarkup = { viewModel.toggleMarkup(line.id) },
                                onDelete = { viewModel.removeLine(line.id) },
                                hazeState = hazeState
                            )
                        }
                    }
                }
            }

            // Кнопки додавання
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showAddMaterial = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Inventory, null); Spacer(Modifier.width(4.dp)); Text("Матеріал")
                }
                OutlinedButton(onClick = { showAddLabor = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Engineering, null); Spacer(Modifier.width(4.dp)); Text("Робота")
                }
            }
        }
    }

    if (showAddMaterial) {
        AddLineDialog(
            title = "Додати матеріал",
            isLabor = false,
            onConfirm = { desc, qty, unit, price ->
                viewModel.addMaterialLine(desc, qty, unit, price)
                showAddMaterial = false
            },
            onDismiss = { showAddMaterial = false }
        )
    }

    if (showAddLabor) {
        AddLineDialog(
            title = "Додати роботу",
            isLabor = true,
            onConfirm = { desc, qty, _, price ->
                viewModel.addLaborLine(desc, qty, price)
                showAddLabor = false
            },
            onDismiss = { showAddLabor = false }
        )
    }
}

@Composable
private fun EstimateLineRow(
    line: EstimateLineUi,
    markup: Double,
    onToggleMarkup: () -> Unit,
    onDelete: () -> Unit,
    hazeState: HazeState?
) {
    val lineTotal = line.qty * line.unitPrice
    val totalWithMarkup = if (line.applyMarkup && markup > 0) lineTotal * (1 + markup / 100) else lineTotal

    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.description, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${line.qty.toUaString()} ${line.unitLabel} × ${line.unitPrice.toUaString()} грн",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (line.priceSource != "Manual") {
                    Text(
                        "Джерело: ${line.priceSource.lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${totalWithMarkup.toUaStringRound()} грн",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.applyMarkup) CubeMasterColors.gold else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = line.applyMarkup,
                        onCheckedChange = { onToggleMarkup() },
                        modifier = Modifier.size(24.dp)
                    )
                    Text("+%", style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = CubeMasterColors.error.copy(0.7f))
            }
        }
    }
}

@Composable
private fun AddLineDialog(
    title: String,
    isLabor: Boolean,
    onConfirm: (String, Double, MeasurementUnit, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(MeasurementUnit.M2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(if (isLabor) "Назва роботи" else "Назва матеріалу") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isLabor) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        OutlinedTextField(
                            unit.shortLabelUa(), {}, readOnly = true, label = { Text("Одиниця") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            MeasurementUnit.entries.forEach { u ->
                                DropdownMenuItem(text = { Text(u.shortLabelUa()) }, onClick = { unit = u; expanded = false })
                            }
                        }
                    }
                }
                NumberInputField(qty, { qty = it }, "Кількість", if (isLabor) "м²" else "")
                NumberInputField(price, { price = it }, "Ціна за одиницю", "грн")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(description, qty.replace(",", ".").toDoubleOrNull() ?: 0.0, unit, price.replace(",", ".").toDoubleOrNull() ?: 0.0)
                },
                enabled = description.isNotBlank()
            ) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
