package com.example.cubemaster.presentation.geometry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cubemaster.core.model.Edge
import com.example.cubemaster.ui.components.createCameraOutputUri
import com.example.cubemaster.ui.theme.CubeMasterColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FloorplanDrawFlow(
    onShapeConfirmed: (List<Edge>) -> Unit,
    onImagePicked: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var isPdf by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var calibratedScale by remember { mutableStateOf<Float?>(null) }
    var showPickMenu by remember { mutableStateOf(false) }
    var isDecoding by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun startDecoding(uri: Uri, pdf: Boolean) {
        pickedUri = uri
        isPdf = pdf
        isDecoding = true
        scope.launch {
            val decoded = decodeImageOrPdfFirstPage(context, uri, pdf)
            bitmap = decoded
            isDecoding = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { startDecoding(it, pdf = false) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { startDecoding(it, pdf = false) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { startDecoding(it, pdf = true) }
    }

    Column(modifier = modifier) {
        when {
            bitmap == null -> {
                OutlinedButton(
                    onClick = { showPickMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDecoding
                ) {
                    Icon(Icons.Default.Map, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDecoding) "Завантаження плану…" else "Додати план приміщення (фото або PDF)")
                }
            }
            calibratedScale == null -> {
                FloorplanCalibrationCanvas(
                    backgroundImage = bitmap!!,
                    onCalibrated = { scale ->
                        calibratedScale = scale
                        pickedUri?.let(onImagePicked)
                    }
                )
            }
            else -> {
                FreehandDrawCanvas(
                    onShapeConfirmed = onShapeConfirmed,
                    backgroundImage = bitmap,
                    calibratedPxPerMeter = calibratedScale
                )
            }
        }
    }

    if (showPickMenu) {
        AlertDialog(
            onDismissRequest = { showPickMenu = false },
            title = { Text("Додати план") },
            text = {
                Column {
                    TextButton(onClick = {
                        showPickMenu = false
                        val uri = createCameraOutputUri(context)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Зняти фото")
                    }
                    TextButton(onClick = {
                        showPickMenu = false
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Обрати з галереї")
                    }
                    TextButton(onClick = {
                        showPickMenu = false
                        pdfLauncher.launch(arrayOf("application/pdf"))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Додати PDF")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPickMenu = false }) { Text("Закрити") } }
        )
    }
}

@Composable
private fun FloorplanCalibrationCanvas(
    backgroundImage: ImageBitmap,
    onCalibrated: (pxPerMeter: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var lineStart by remember { mutableStateOf<Offset?>(null) }
    var lineEnd by remember { mutableStateOf<Offset?>(null) }
    var showLengthDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            "Проведіть лінію по будь-якому відрізку на плані з відомою довжиною (наприклад, ширина дверей), потім вкажіть її реальну довжину",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { lineStart = it; lineEnd = it },
                        onDrag = { change, _ -> lineEnd = change.position },
                        onDragEnd = {
                            val start = lineStart
                            val end = lineEnd
                            if (start != null && end != null && kotlin.math.hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()) > 20) {
                                showLengthDialog = true
                            }
                        }
                    )
                }
        ) {
            drawImage(
                image = backgroundImage,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
            val start = lineStart
            val end = lineEnd
            if (start != null && end != null) {
                drawLine(CubeMasterColors.red, start, end, strokeWidth = 5f)
                drawCircle(CubeMasterColors.red, 10f, start)
                drawCircle(CubeMasterColors.red, 10f, end)
            }
        }
    }

    if (showLengthDialog) {
        var lengthMm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLengthDialog = false },
            title = { Text("Реальна довжина лінії") },
            text = {
                OutlinedTextField(
                    value = lengthMm,
                    onValueChange = { lengthMm = it },
                    label = { Text("Довжина, мм") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mm = lengthMm.toIntOrNull()
                        val start = lineStart
                        val end = lineEnd
                        if (mm != null && mm > 0 && start != null && end != null) {
                            val linePx = kotlin.math.hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble())
                            val pxPerMeter = (linePx / (mm / 1000.0)).toFloat()
                            showLengthDialog = false
                            onCalibrated(pxPerMeter)
                        }
                    },
                    enabled = lengthMm.toIntOrNull() != null && lengthMm.toIntOrNull()!! > 0
                ) { Text("Далі") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLengthDialog = false
                    lineStart = null
                    lineEnd = null
                }) { Text("Перемалювати") }
            }
        )
    }
}

private suspend fun decodeImageOrPdfFirstPage(context: Context, uri: Uri, isPdf: Boolean): ImageBitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (isPdf) {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd: ParcelFileDescriptor ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp.asImageBitmap()
                        }
                    }
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }
        } catch (e: Exception) {
            null
        }
    }
