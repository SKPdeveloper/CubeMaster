@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.cubemaster.presentation.demolition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cubemaster.core.model.*
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors

@Composable
fun DemolitionScreen(
    roomId: String,
    onBack: () -> Unit,
    viewModel: DemolitionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf<DemolitionKind?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = "Демонтаж — ${state.room?.name ?: ""}",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Кнопки для додавання завдань
            Text("Додати роботи:", style = MaterialTheme.typography.titleSmall)

            val demolitionKinds = listOf(
                DemolitionKind.WallRemoval to "Знесення стін",
                DemolitionKind.OpeningCut to "Прорізання прорізів",
                DemolitionKind.PlasterRemoval to "Демонтаж штукатурки",
                DemolitionKind.TileRemoval to "Демонтаж плитки",
                DemolitionKind.ScreedRemoval to "Демонтаж стяжки",
                DemolitionKind.FlooringRemoval to "Демонтаж підлогового покриття",
                DemolitionKind.PaintRemoval to "Видалення фарби"
            )

            demolitionKinds.forEach { (kind, label) ->
                OutlinedButton(
                    onClick = { showAddDialog = kind },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }

            OrnamentalDivider()

            // Зведення
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Зведення демонтажу", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Обсяг сміття: ${String.format("%.2f", state.totalDebrisM3)} м³",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Трудовитрати: ${String.format("%.1f", state.totalLaborHours)} люд·год",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Контейнери 8 м³: ${state.containersCount} шт.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            WarningCard("Перед демонтажем несучих конструкцій обов'язково перевірте наявність дозволу на перепланування. ДБН В.1.2-14:2018")

            val attachments by viewModel.observeAttachments().collectAsStateWithLifecycle(initialValue = emptyList())
            AttachmentsSection(
                attachments = attachments,
                onAddPhoto = { uri -> viewModel.addPhoto(uri) },
                onAddPdf = { uri -> viewModel.addPdf(uri) },
                onAddNote = { text -> viewModel.addNote(text) },
                onDelete = { viewModel.deleteAttachment(it) }
            )
        }
    }

    showAddDialog?.let { kind ->
        DemolitionAddDialog(
            kind = kind,
            onDismiss = { showAddDialog = null },
            viewModel = viewModel
        )
    }
}

@Composable
private fun DemolitionAddDialog(
    kind: DemolitionKind,
    onDismiss: () -> Unit,
    viewModel: DemolitionViewModel
) {
    when (kind) {
        DemolitionKind.WallRemoval -> WallRemovalDialog(onDismiss, viewModel)
        DemolitionKind.PlasterRemoval -> PlasterRemovalDialog(onDismiss, viewModel)
        DemolitionKind.TileRemoval -> SimpleAreaDialog("Демонтаж плитки", onDismiss) { area ->
            viewModel.addTileRemoval(area); onDismiss()
        }
        DemolitionKind.ScreedRemoval -> ScreedRemovalDialog(onDismiss, viewModel)
        DemolitionKind.FlooringRemoval -> SimpleAreaDialog("Демонтаж покриття підлоги", onDismiss) { area ->
            viewModel.addFlooringRemoval(area); onDismiss()
        }
        DemolitionKind.PaintRemoval -> PaintRemovalDialog(onDismiss, viewModel)
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Незабаром") },
            text = { Text("Цей тип ще в розробці") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
    }
}

@Composable
private fun WallRemovalDialog(onDismiss: () -> Unit, viewModel: DemolitionViewModel) {
    var length by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var thickness by remember { mutableStateOf("") }
    var material by remember { mutableStateOf(WallMaterial.Brick) }
    var powered by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text("Знесення стін") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberInputField(length, { length = it }, "Довжина", "м")
                NumberInputField(height, { height = it }, "Висота", "м")
                NumberInputField(thickness, { thickness = it }, "Товщина", "мм")
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded, { expanded = it }) {
                    OutlinedTextField(
                        material.nameUa, {}, readOnly = true, label = { Text("Матеріал") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        WallMaterial.entries.forEach { m ->
                            DropdownMenuItem(text = { Text(m.nameUa) }, onClick = { material = m; expanded = false })
                        }
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(powered, { powered = it })
                    Text("З відбійним молотком")
                }
                if (material == WallMaterial.ReinforcedConcrete) {
                    WarningCard("Залізобетон: обов'язкова перевірка несучої функції!")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.addWallRemoval(
                    length.toDoubleOrNull() ?: 0.0,
                    height.toDoubleOrNull() ?: 0.0,
                    thickness.toDoubleOrNull() ?: 0.0,
                    material, powered
                )
                onDismiss()
            }) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun PlasterRemovalDialog(onDismiss: () -> Unit, viewModel: DemolitionViewModel) {
    var area by remember { mutableStateOf("") }
    var isGypsum by remember { mutableStateOf(true) }
    var isCeiling by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text("Демонтаж штукатурки") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberInputField(area, { area = it }, "Площа", "м²")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(isGypsum, { isGypsum = it })
                    Text("Гіпсова штукатурка (не ЦПС)")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(isCeiling, { isCeiling = it })
                    Text("Стеля (×0.8 від продуктивності)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.addPlasterRemoval(area.toDoubleOrNull() ?: 0.0, isGypsum, isCeiling)
                onDismiss()
            }) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun ScreedRemovalDialog(onDismiss: () -> Unit, viewModel: DemolitionViewModel) {
    var area by remember { mutableStateOf("") }
    var thickness by remember { mutableStateOf("50") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text("Демонтаж стяжки") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberInputField(area, { area = it }, "Площа", "м²")
                NumberInputField(thickness, { thickness = it }, "Товщина стяжки", "мм")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.addScreedRemoval(area.toDoubleOrNull() ?: 0.0, thickness.toDoubleOrNull() ?: 50.0)
                onDismiss()
            }) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun PaintRemovalDialog(onDismiss: () -> Unit, viewModel: DemolitionViewModel) {
    var area by remember { mutableStateOf("") }
    var paintType by remember { mutableStateOf(PaintType.Unknown) }
    var method by remember { mutableStateOf(PaintRemovalMethod.MechanicalGrinder) }
    var layers by remember { mutableStateOf("2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text("Видалення фарби") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberInputField(area, { area = it }, "Площа", "м²")
                Text("Тип фарби:", style = MaterialTheme.typography.labelMedium)
                PaintType.entries.forEach { t ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(paintType == t, { paintType = t })
                        Text(paintTypeLabel(t))
                    }
                }
                Text("Метод видалення:", style = MaterialTheme.typography.labelMedium)
                PaintRemovalMethod.entries.forEach { m ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(method == m, { method = m })
                        Text(methodLabel(m))
                    }
                }
                NumberInputField(layers, { layers = it }, "К-сть шарів (орієнтовно)", "")
                if (paintType == PaintType.Unknown) {
                    WarningCard("Невідомий тип фарби — розрахунок за найважчим сценарієм")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.addPaintRemoval(PaintRemovalParams(
                    area.toDoubleOrNull() ?: 0.0, paintType,
                    PaintSubstrate.Plaster, method, layers.toIntOrNull() ?: 2
                ))
                onDismiss()
            }) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun SimpleAreaDialog(title: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var area by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(title) },
        text = {
            NumberInputField(area, { area = it }, "Площа", "м²")
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(area.toDoubleOrNull() ?: 0.0) }) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

private fun paintTypeLabel(t: PaintType) = when (t) {
    PaintType.WaterBased -> "Водоемульсійна"
    PaintType.OilBased -> "Олійна"
    PaintType.EnamelAlkyd -> "Алкідна емаль"
    PaintType.Unknown -> "Невідомо (гірший сценарій)"
}

private fun methodLabel(m: PaintRemovalMethod) = when (m) {
    PaintRemovalMethod.MechanicalGrinder -> "Шліфмашина"
    PaintRemovalMethod.HeatGun -> "Будівельний фен"
    PaintRemovalMethod.ChemicalStripper -> "Хімічна змивка"
    PaintRemovalMethod.Combined -> "Комбінований"
}
