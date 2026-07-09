@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.cubemaster.presentation.layers

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cubemaster.core.model.*
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun LayersScreen(
    roomId: String,
    surfaceId: String,
    onBack: () -> Unit,
    viewModel: LayersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    var showAddLayerDialog by remember { mutableStateOf(false) }
    var showPresetsSheet by remember { mutableStateOf(false) }
    var showWaterproofingWarning by remember { mutableStateOf(false) }
    var layerToEditThickness by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Попередження про відсутність гідроізоляції
    LaunchedEffect(state.waterproofingRequired, state.waterproofingPresent) {
        if (state.waterproofingRequired && !state.waterproofingPresent && !state.isLoading) {
            showWaterproofingWarning = true
        }
    }

    Scaffold(
        topBar = {
            CubeMasterTopBar(
                title = surfaceKindTitle(state.surface?.kind),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showPresetsSheet = true }) {
                        Icon(Icons.Default.LibraryBooks, contentDescription = "Пресети")
                    }
                    IconButton(onClick = { showAddLayerDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Додати шар")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingOverlay()
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize().hazeSource(hazeState)) {

            // Заголовок поверхні
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Площа: ${String.format("%.2f", state.surfaceAreaM2)} м²",
                        style = MaterialTheme.typography.titleSmall)
                    if (state.surface != null) {
                        LayerStackIndicator(
                            layers = state.surface!!.layers,
                            modifier = Modifier.width(120.dp).height(10.dp)
                        )
                    }
                }
            }

            if (state.waterproofingRequired && !state.waterproofingPresent) {
                WarningCard(
                    message = "Гідроізоляція відсутня у вологому приміщенні. Рекомендовано додати шар гідроізоляції.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            OrnamentalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (state.calculatedLayers.isEmpty()) {
                EmptyState(
                    "Шари пирога ще не додано.\nНатисніть + або оберіть пресет.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.calculatedLayers, key = { it.layer.id }) { item ->
                        LayerCard(
                            item = item,
                            onMoveUp = { viewModel.moveLayerUp(item.layer.id) },
                            onMoveDown = { viewModel.moveLayerDown(item.layer.id) },
                            onDelete = { viewModel.removeLayer(item.layer.id) },
                            onEditThickness = { layerToEditThickness = item.layer.id to (item.layer.thicknessMm?.toString() ?: "") },
                            onTogglePorous = { viewModel.setLayerPorous(item.layer.id, it) },
                            onToggleDiagonal = { viewModel.setLayerDiagonal(item.layer.id, it) },
                            hazeState = hazeState
                        )
                    }
                }
            }

            // Підсумок по матеріалах
            if (state.calculatedLayers.isNotEmpty()) {
                SummaryFooter(state, hazeState)
            }
        }
    }

    if (showAddLayerDialog) {
        AddLayerDialog(
            onConfirm = { type, thickness, sku ->
                viewModel.addLayer(type, thickness, sku)
                showAddLayerDialog = false
            },
            onDismiss = { showAddLayerDialog = false }
        )
    }

    if (showPresetsSheet) {
        PresetsBottomSheet(
            presets = viewModel.presets.filter { it.surfaceKind == state.surface?.kind },
            onSelect = { preset ->
                viewModel.addLayerFromPreset(preset)
                showPresetsSheet = false
            },
            onDismiss = { showPresetsSheet = false }
        )
    }

    layerToEditThickness?.let { (layerId, current) ->
        EditThicknessDialog(
            current = current,
            onConfirm = { mm ->
                viewModel.updateLayerThickness(layerId, mm)
                layerToEditThickness = null
            },
            onDismiss = { layerToEditThickness = null }
        )
    }

    if (showWaterproofingWarning) {
        AlertDialog(
            onDismissRequest = { showWaterproofingWarning = false },
            title = { Text("Гідроізоляція відсутня") },
            text = { Text("Цей тип приміщення потребує гідроізоляції підлоги. Продовжити без неї?") },
            confirmButton = { TextButton(onClick = { showWaterproofingWarning = false }) { Text("Продовжити") } },
            dismissButton = {
                TextButton(onClick = {
                    showWaterproofingWarning = false
                    viewModel.addLayer(LayerType.WaterproofingCoating, null, "WATERPROOF_COATING")
                }) { Text("Додати гідроізоляцію") }
            }
        )
    }
}

private val POROUS_APPLICABLE_TYPES = setOf(LayerType.PrimerDeep, LayerType.PrimerContact)
private val DIAGONAL_APPLICABLE_TYPES = setOf(LayerType.FlooringLaminate, LayerType.FlooringParquet, LayerType.FlooringTile)

@Composable
private fun LayerCard(
    item: LayerCalculatedItem,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onEditThickness: () -> Unit,
    onTogglePorous: (Boolean) -> Unit,
    onToggleDiagonal: (Boolean) -> Unit,
    hazeState: HazeState?
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        layerTypeLabel(item.layer.layerType),
                        style = MaterialTheme.typography.titleSmall
                    )
                    item.layer.thicknessMm?.let { mm ->
                        Text("Товщина: ${mm} мм", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item.normativeRef?.let { ref ->
                        Text(ref, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (item.layer.layerType in POROUS_APPLICABLE_TYPES) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.layer.isPorous, onCheckedChange = onTogglePorous)
                            Text("Пориста основа (×1,5 витрати)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (item.layer.layerType in DIAGONAL_APPLICABLE_TYPES) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.layer.isDiagonal, onCheckedChange = onToggleDiagonal)
                            Text("Діагональна укладка (більший запас)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEditThickness, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = CubeMasterColors.error.copy(0.7f))
                    }
                }
            }

            // Результат розрахунку
            if (item.result.mixMassKg > 0 || item.result.additionalLines.isNotEmpty()) {
                ThinDivider(modifier = Modifier.padding(vertical = 6.dp))
                if (item.result.mixMassKg > 0) {
                    Text("${String.format("%.1f", item.result.mixMassKg)} кг" +
                        if (item.result.bagsCount > 0) " → ${item.result.bagsCount} мішк." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = CubeMasterColors.gold
                    )
                }
                item.result.additionalLines.forEach { line ->
                    Text("${line.descriptionUa}: ${String.format("%.2f", line.qty)} ${unitLabel(line.unit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Попередження
            item.warnings.forEach { warning ->
                Spacer(Modifier.height(4.dp))
                WarningCard(warning)
            }
        }
    }
}

@Composable
private fun SummaryFooter(state: LayersUiState, hazeState: HazeState?) {
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
        val totalKg = state.calculatedLayers.sumOf { it.result.mixMassKg }
        Column(modifier = Modifier.padding(16.dp)) {
            OrnamentalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Загальна маса матеріалів: ${String.format("%.1f", totalKg)} кг",
                style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun AddLayerDialog(
    onConfirm: (LayerType, Double?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(LayerType.PlasterGypsumManual) }
    var thickness by remember { mutableStateOf("") }
    val layersWithThickness = setOf(
        LayerType.ScreedCpsManual, LayerType.ScreedCp5Bags, LayerType.ScreedSelfLevelingCement,
        LayerType.ScreedSelfLevelingGypsum, LayerType.PlasterGypsumManual, LayerType.PlasterGypsumMachine,
        LayerType.PlasterCementSandManual, LayerType.WallPutty
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text("Додати шар") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = layerTypeLabel(selectedType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Тип шару") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        LayerType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(layerTypeLabel(type)) },
                                onClick = { selectedType = type; expanded = false }
                            )
                        }
                    }
                }
                if (selectedType in layersWithThickness) {
                    NumberInputField(thickness, { thickness = it }, "Товщина", "мм")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selectedType, thickness.replace(",", ".").toDoubleOrNull(), null)
            }) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun PresetsBottomSheet(
    presets: List<LayerPreset>,
    onSelect: (LayerPreset) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пресети") },
        text = {
            if (presets.isEmpty()) {
                Text("Немає доступних пресетів для цієї поверхні")
            } else {
                Column {
                    presets.forEach { preset ->
                        TextButton(onClick = { onSelect(preset) }, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(preset.nameUa, style = MaterialTheme.typography.bodyMedium)
                                Text("${preset.layers.size} шарів", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрити") } }
    )
}

@Composable
private fun EditThicknessDialog(current: String, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text("Товщина шару") },
        text = {
            NumberInputField(value, { value = it }, "Товщина", "мм")
        },
        confirmButton = {
            TextButton(onClick = { value.replace(",", ".").toDoubleOrNull()?.let { onConfirm(it) } }) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

private fun surfaceKindTitle(kind: SurfaceKind?) = when (kind) {
    SurfaceKind.Floor -> "Пиріг підлоги"
    SurfaceKind.Ceiling -> "Пиріг стелі"
    SurfaceKind.Wall -> "Пиріг стіни"
    null -> "Поверхня"
}

private fun unitLabel(unit: MeasurementUnit) = when (unit) {
    MeasurementUnit.Kg -> "кг"
    MeasurementUnit.Bag25 -> "міш."
    MeasurementUnit.Bag50 -> "міш."
    MeasurementUnit.M2 -> "м²"
    MeasurementUnit.M3 -> "м³"
    MeasurementUnit.Pcs -> "шт."
    MeasurementUnit.L -> "л"
    MeasurementUnit.M -> "м"
    MeasurementUnit.Roll -> "рул."
}

fun layerTypeLabel(type: LayerType) = when (type) {
    LayerType.ScreedCpsManual -> "Стяжка ЦПС (ручна)"
    LayerType.ScreedCp5Bags -> "Стяжка (суха суміш)"
    LayerType.ScreedSelfLevelingCement -> "Наливна підлога (цемент)"
    LayerType.ScreedSelfLevelingGypsum -> "Наливна підлога (гіпс)"
    LayerType.ScreedDryPrefab -> "Суха стяжка (керамзит+ГВЛ)"
    LayerType.PlasterGypsumManual -> "Гіпсова штукатурка (ручна)"
    LayerType.PlasterGypsumMachine -> "Гіпсова штукатурка (машинна)"
    LayerType.PlasterCementSandManual -> "ЦПС штукатурка (ручна)"
    LayerType.PlasterCementSandMachine -> "ЦПС штукатурка (машинна)"
    LayerType.PlasterDecorativeKoroid -> "Короїд декоративний"
    LayerType.PlasterVenetian -> "Венеціанська штукатурка"
    LayerType.PrimerDeep -> "Ґрунтовка глибокого проникнення"
    LayerType.PrimerContact -> "Ґрунтовка контактна (бетоноконтакт)"
    LayerType.WaterproofingCoating -> "Гідроізоляція обмазувальна"
    LayerType.InsulationLayer -> "Утеплення"
    LayerType.FlooringLaminate -> "Ламінат"
    LayerType.FlooringParquet -> "Паркет"
    LayerType.FlooringLinoleum -> "Лінолеум"
    LayerType.FlooringTile -> "Плитка керамічна"
    LayerType.FlooringSelfLevelingFinish -> "Наливна підлога декоративна"
    LayerType.WallPutty -> "Шпаклівка фінішна"
    LayerType.WallPaint -> "Фарба водоемульсійна"
    LayerType.WallWallpaper -> "Шпалери"
    LayerType.WallDrywall -> "Гіпсокартон"
    LayerType.WallTile -> "Плитка настінна"
    LayerType.CeilingStretch -> "Натяжна стеля"
    LayerType.CeilingDrywall -> "Гіпсокартон (стеля)"
    LayerType.CeilingPaint -> "Фарба (стеля)"
}
