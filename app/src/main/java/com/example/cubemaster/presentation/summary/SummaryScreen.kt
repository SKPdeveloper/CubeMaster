package com.example.cubemaster.presentation.summary

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
import com.cubemaster.core.model.MeasurementUnit
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors

@Composable
fun SummaryScreen(
    projectId: String,
    onEstimateClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filterRoom by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CubeMasterTopBar(
                title = "Зведення — ${state.projectTitle}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onEstimateClick(projectId) }) {
                        Icon(Icons.Default.AttachMoney, contentDescription = "Кошторис")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingOverlay()
            return@Scaffold
        }

        if (state.materialLines.isEmpty()) {
            EmptyState("Немає матеріалів. Додайте шари до поверхонь кімнат.")
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            // Шапка
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Загальна маса матеріалів:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.0f", state.totalMassKg)} кг",
                            style = MaterialTheme.typography.titleLarge,
                            color = CubeMasterColors.gold)
                    }
                    Text("${state.materialLines.size} позицій",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OrnamentalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.materialLines, key = { it.sku }) { line ->
                    MaterialSummaryRow(line)
                }
            }
        }
    }
}

@Composable
private fun MaterialSummaryRow(line: SummaryMaterialLine) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.nameUa, style = MaterialTheme.typography.bodyMedium)
                Text(
                    line.roomNames.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${String.format("%.2f", line.totalQty)} ${unitShortLabel(line.unit)}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = com.example.cubemaster.ui.theme.TabularNumberStyle.fontFamily
                    ),
                    color = CubeMasterColors.gold
                )
            }
        }
    }
}

private fun unitShortLabel(unit: MeasurementUnit) = when (unit) {
    MeasurementUnit.Kg -> "кг"
    MeasurementUnit.Bag25 -> "міш.25"
    MeasurementUnit.Bag50 -> "міш.50"
    MeasurementUnit.M2 -> "м²"
    MeasurementUnit.M3 -> "м³"
    MeasurementUnit.Pcs -> "шт."
    MeasurementUnit.L -> "л"
    MeasurementUnit.M -> "м"
    MeasurementUnit.Roll -> "рул."
}
