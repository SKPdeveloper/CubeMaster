package com.example.cubemaster.presentation.projects

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
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    Scaffold(
        topBar = {
            CubeMasterTopBar(
                title = "КубМайстер",
                actions = {
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
                state.projects.isEmpty() -> EmptyState("Немає проєктів. Натисніть + щоб створити.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectClick(project.id) },
                            onSummary = { onSummaryClick(project.id) },
                            onDelete = { projectToDelete = project }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onConfirm = { title, address ->
                viewModel.createProject(title, address)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
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

    state.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onSummary: () -> Unit,
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
                }
                Row {
                    SyncIndicator(project.syncState)
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
private fun CreateProjectDialog(onConfirm: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новий проєкт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), address.trim().takeIf { it.isNotEmpty() }) },
                enabled = title.isNotBlank()
            ) { Text("Створити") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
