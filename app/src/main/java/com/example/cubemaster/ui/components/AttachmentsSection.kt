package com.example.cubemaster.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.cubemaster.core.model.Attachment
import com.cubemaster.core.model.AttachmentKind
import com.example.cubemaster.ui.theme.CubeMasterColors
import java.io.File

@Composable
fun AttachmentsSection(
    attachments: List<Attachment>,
    onAddPhoto: (Uri) -> Unit,
    onAddPdf: (Uri) -> Unit,
    onAddNote: (String) -> Unit,
    onDelete: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let(onAddPhoto)
    }
    fun launchCamera() {
        val uri = createCameraOutputUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onAddPhoto)
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAddPdf)
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = !expanded }
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Вкладення" + if (attachments.isNotEmpty()) " (${attachments.size})" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(attachments, key = { it.id }) { attachment ->
                    AttachmentThumbnail(attachment = attachment, onDelete = { onDelete(attachment) })
                }
                item {
                    AddAttachmentButton(onClick = { showAddMenu = true })
                }
            }
        }
    }

    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text("Додати вкладення") },
            text = {
                Column {
                    TextButton(onClick = {
                        showAddMenu = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Зняти фото")
                    }
                    TextButton(onClick = {
                        showAddMenu = false
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Обрати з галереї")
                    }
                    TextButton(onClick = {
                        showAddMenu = false
                        pdfLauncher.launch(arrayOf("application/pdf"))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Додати PDF")
                    }
                    TextButton(onClick = {
                        showAddMenu = false
                        showNoteDialog = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Notes, null); Spacer(Modifier.width(8.dp)); Text("Текстова примітка")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddMenu = false }) { Text("Закрити") } }
        )
    }

    if (showNoteDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            modifier = Modifier.imePadding(),
            properties = DialogProperties(decorFitsSystemWindows = false),
            title = { Text("Текстова примітка") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Опис") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onAddNote(text.trim()); showNoteDialog = false },
                    enabled = text.isNotBlank()
                ) { Text("Додати") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun AttachmentThumbnail(attachment: Attachment, onDelete: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when (attachment.kind) {
            AttachmentKind.Photo -> AsyncImage(
                model = attachment.fileUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            AttachmentKind.Pdf -> Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = "PDF",
                modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
            AttachmentKind.Note -> Icon(
                Icons.Default.Notes,
                contentDescription = "Примітка",
                modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 8.dp))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Видалити", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AddAttachmentButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Add, contentDescription = "Додати вкладення", tint = CubeMasterColors.red)
    }
}

// context.filesDir, а не cacheDir — кеш ОС може очистити будь-якої миті,
// в т.ч. поки відкрита зовнішня камера, і файл зникне до моменту завантаження.
internal fun createCameraOutputUri(context: Context): Uri {
    val dir = File(context.filesDir, "attachments").apply { mkdirs() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
