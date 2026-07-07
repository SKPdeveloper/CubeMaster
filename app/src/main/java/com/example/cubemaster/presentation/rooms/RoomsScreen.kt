package com.example.cubemaster.presentation.rooms

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
import com.cubemaster.core.model.RoomType
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors

@Composable
fun RoomsScreen(
    projectId: String,
    onRoomClick: (String) -> Unit,
    onSummaryClick: () -> Unit,
    onEstimateClick: () -> Unit,
    onDocumentsClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: RoomsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
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
                title = state.project?.title ?: "Кімнати",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onSummaryClick) {
                        Icon(Icons.Default.Calculate, contentDescription = "Зведення")
                    }
                    IconButton(onClick = onEstimateClick) {
                        Icon(Icons.Default.AttachMoney, contentDescription = "Кошторис")
                    }
                    IconButton(onClick = onDocumentsClick) {
                        Icon(Icons.Default.Folder, contentDescription = "Документи проєкту")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = CubeMasterColors.red
            ) {
                Icon(Icons.Default.Add, contentDescription = "Додати кімнату", tint = CubeMasterColors.white)
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> LoadingOverlay()
                state.rooms.isEmpty() -> EmptyState("Немає кімнат. Натисніть + для додавання.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.areaWarning?.let { warning ->
                        item(key = "area_warning") { WarningCard(warning) }
                    }
                    items(state.rooms, key = { it.room.id }) { item ->
                        val attachments by viewModel.observeAttachments(item.room.id)
                            .collectAsStateWithLifecycle(initialValue = emptyList())
                        RoomCard(
                            item = item,
                            attachments = attachments,
                            onClick = { onRoomClick(item.room.id) },
                            onDelete = { viewModel.deleteRoom(item.room.id) },
                            onAddPhoto = { uri -> viewModel.addPhoto(item.room.id, uri) },
                            onAddPdf = { uri -> viewModel.addPdf(item.room.id, uri) },
                            onAddNote = { text -> viewModel.addNote(item.room.id, text) },
                            onDeleteAttachment = { viewModel.deleteAttachment(it) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            onConfirm = { name, type ->
                viewModel.createRoom(name, type)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun RoomCard(
    item: RoomUiItem,
    attachments: List<com.cubemaster.core.model.Attachment>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onAddPdf: (android.net.Uri) -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteAttachment: (com.cubemaster.core.model.Attachment) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.room.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Площа: ${String.format("%.2f", item.floorAreaM2)} м²  |  ${roomTypeLabel(item.room.roomType)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Видалити", tint = CubeMasterColors.error.copy(0.7f))
                }
            }
            if (item.surfaces.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val totalLayers = item.surfaces.sumOf { it.layers.size }
                LayerStackIndicator(
                    layers = item.surfaces.firstOrNull { it.kind.name == "Floor" }?.layers ?: emptyList(),
                    modifier = Modifier.fillMaxWidth().height(16.dp)
                )
                Spacer(Modifier.height(4.dp))
                CompletionBadge(totalLayers)
            }
            Spacer(Modifier.height(8.dp))
            AttachmentsSection(
                attachments = attachments,
                onAddPhoto = onAddPhoto,
                onAddPdf = onAddPdf,
                onAddNote = onAddNote,
                onDelete = onDeleteAttachment
            )
        }
    }
}

private fun roomTypeLabel(type: RoomType) = when (type) {
    RoomType.Living -> "Житлова"
    RoomType.Bathroom -> "Санвузол"
    RoomType.Kitchen -> "Кухня"
    RoomType.KitchenWetZone -> "Кухня (мокра зона)"
    RoomType.Balcony -> "Балкон/лоджія"
    RoomType.Technical -> "Технічне"
}

@Composable
private fun CreateRoomDialog(onConfirm: (String, RoomType) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(RoomType.Living) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text("Нова кімната") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Назва (наприклад: Кухня)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Тип приміщення:", style = MaterialTheme.typography.labelMedium)
                RoomType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(roomTypeLabel(type), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), selectedType) },
                enabled = name.isNotBlank()
            ) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
