package com.example.cubemaster.presentation.projects

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
import com.cubemaster.core.model.Project
import com.cubemaster.core.model.SyncState
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onProjectClick: (String) -> Unit,
    onSummaryClick: (String) -> Unit,
    onCatalogClick: () -> Unit,
    onProfileClick: () -> Unit,
    onHelpClick: () -> Unit,
    openCreateDialogOnStart: Boolean = false,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(openCreateDialogOnStart) }
    var projectToEdit by remember { mutableStateOf<Project?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
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
                title = "КубМайстер",
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Довідка")
                    }
                    IconButton(onClick = onCatalogClick) {
                        Icon(Icons.Default.List, contentDescription = "Каталог матеріалів")
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Default.Person, contentDescription = "Профіль")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = CubeMasterColors.red
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новий проєкт", tint = CubeMasterColors.white)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> LoadingOverlay()
                state.projects.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("Тут з'явиться ваш перший об'єкт.\nНатисніть +, щоб додати проєкт і порахувати кубатуру матеріалів.")
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectClick(project.id) },
                            onSummary = { onSummaryClick(project.id) },
                            onEdit = { projectToEdit = project },
                            onDelete = { projectToDelete = project }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ProjectDialog(
            title = "Новий проєкт",
            confirmLabel = "Створити",
            onConfirm = { title, address, area ->
                viewModel.createProject(title, address, area)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    projectToEdit?.let { project ->
        ProjectDialog(
            title = "Редагувати проєкт",
            confirmLabel = "Зберегти",
            initialTitle = project.title,
            initialAddress = project.address.orEmpty(),
            initialAreaM2 = project.documentedAreaM2,
            onConfirm = { title, address, area ->
                viewModel.updateProject(project, title, address, area)
                projectToEdit = null
            },
            onDismiss = { projectToEdit = null }
        )
    }

    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Видалити проєкт?") },
            text = { Text("«${project.title}» та всі кімнати будуть видалені назавжди.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(project)
                    projectToDelete = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = CubeMasterColors.error)) {
                    Text("Видалити")
                }
            },
            dismissButton = { TextButton(onClick = { projectToDelete = null }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onSummary: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    project.address?.let { addr ->
                        Text(
                            text = addr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    project.documentedAreaM2?.let { area ->
                        Text(
                            text = "Площа за документами: ${String.format("%.2f", area)} м²",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    SyncIndicator(project.syncState)
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Редагувати", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onSummary) {
                        Icon(Icons.Default.Calculate, contentDescription = "Зведення", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Видалити", tint = CubeMasterColors.error.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())
            Text(
                text = "Оновлено: ${fmt.format(project.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncIndicator(syncState: SyncState) {
    val (color, tooltip) = when (syncState) {
        SyncState.Synced -> CubeMasterColors.success to "Синхронізовано"
        SyncState.PendingUpload -> CubeMasterColors.warning to "Очікує синхронізації"
        SyncState.Conflict -> CubeMasterColors.error to "Конфлікт синхронізації"
    }
    Icon(
        imageVector = when (syncState) {
            SyncState.Synced -> Icons.Default.CloudDone
            SyncState.PendingUpload -> Icons.Default.CloudUpload
            SyncState.Conflict -> Icons.Default.CloudOff
        },
        contentDescription = tooltip,
        tint = color,
        modifier = Modifier.size(18.dp).padding(top = 4.dp)
    )
}

@Composable
private fun ProjectDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String, String?, Double?) -> Unit,
    onDismiss: () -> Unit,
    initialTitle: String = "",
    initialAddress: String = "",
    initialAreaM2: Double? = null
) {
    var titleValue by remember { mutableStateOf(initialTitle) }
    var address by remember { mutableStateOf(initialAddress) }
    var areaM2 by remember { mutableStateOf(initialAreaM2?.let { String.format("%.2f", it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = titleValue,
                    onValueChange = { titleValue = it },
                    label = { Text("Назва об'єкта") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адреса (необов'язково)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                NumberInputField(
                    value = areaM2,
                    onValueChange = { areaM2 = it },
                    label = "Площа за документами (необов'язково)",
                    unit = "м²",
                    helperText = "З техпаспорта чи договору — застосунок звірить її з сумою площ кімнат"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        titleValue.trim(),
                        address.trim().takeIf { it.isNotEmpty() },
                        areaM2.trim().replace(",", ".").toDoubleOrNull()
                    )
                },
                enabled = titleValue.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
