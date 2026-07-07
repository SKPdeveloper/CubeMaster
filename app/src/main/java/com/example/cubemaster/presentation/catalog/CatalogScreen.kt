package com.example.cubemaster.presentation.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cubemaster.core.catalog.MaterialDefaults
import com.cubemaster.core.model.MaterialCatalogEntry
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors

@Composable
fun CatalogScreen(
    onBack: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expandedItem by remember { mutableStateOf<String?>(null) }
    var showRefreshDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CubeMasterTopBar(
                title = "Каталог матеріалів",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showRefreshDialog = true }) {
                        Icon(Icons.Default.Sync, contentDescription = "Оновити ціни")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {

            // Пошук
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::search,
                label = { Text("Пошук матеріалу") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.isLoading) {
                LoadingOverlay()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries, key = { it.sku }) { entry ->
                        MaterialEntryCard(
                            entry = entry,
                            isExpanded = expandedItem == entry.sku,
                            onToggle = {
                                expandedItem = if (expandedItem == entry.sku) null else entry.sku
                            },
                            latestPrice = state.prices[entry.sku],
                            onSetPrice = { price -> viewModel.setManualPrice(entry.sku, price) }
                        )
                    }
                }
            }
        }
    }

    if (showRefreshDialog) {
        AlertDialog(
            onDismissRequest = { showRefreshDialog = false },
            title = { Text("Оновити зовнішні ціни") },
            text = {
                Text("Буде виконано запит до зовнішніх джерел для отримання актуальних цін. " +
                    "Зверніть увагу: парсинг публічних сайтів без офіційного API виконується з обмеженою частотою.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.refreshExternalPrices()
                    showRefreshDialog = false
                }) { Text("Оновити") }
            },
            dismissButton = { TextButton(onClick = { showRefreshDialog = false }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun MaterialEntryCard(
    entry: MaterialCatalogEntry,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    latestPrice: Double?,
    onSetPrice: (Double) -> Unit
) {
    // Ключ на latestPrice — щоб поле оновилось, коли ціна довантажиться з БД
    // (наприклад, одразу після відкриття екрана чи після "Оновити ціни").
    var priceInput by remember(latestPrice) { mutableStateOf(latestPrice?.toString() ?: "") }

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onToggle) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.nameUa, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${entry.category.name} · ${entry.unit.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    latestPrice?.let {
                        Text("${it.toUaString()} грн", style = MaterialTheme.typography.labelSmall, color = CubeMasterColors.gold)
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null
                    )
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                ThinDivider()
                Spacer(Modifier.height(8.dp))

                // Норми витрати
                entry.consumptionNorm.kgPerM2PerMm?.let { norm ->
                    Text("Витрата: $norm кг/(м²·мм)", style = MaterialTheme.typography.bodySmall)
                }
                entry.consumptionNorm.minThicknessMm?.let { min ->
                    Text("Мін. товщина: $min мм", style = MaterialTheme.typography.bodySmall)
                }
                InfoCard(
                    "Норматив: ${entry.consumptionNorm.normativeReference}",
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (entry.consumptionNorm.minThicknessJustification.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.consumptionNorm.minThicknessJustification,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                // Ручне введення ціни
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberInputField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = "Моя ціна",
                        unit = "грн",
                        helperText = "Ваша реальна закупівельна ціна — заміщує ринкову в розрахунках",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { priceInput.replace(",", ".").toDoubleOrNull()?.let { onSetPrice(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = CubeMasterColors.red)
                    ) { Text("Зберегти") }
                }
            }
        }
    }
}
