package com.example.cubemaster.presentation.geometry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cubemaster.core.geometry.Vertex
import com.cubemaster.core.geometry.ClosureStatus
import com.cubemaster.core.geometry.distance
import com.cubemaster.core.model.*
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun GeometryScreen(
    roomId: String,
    onLayersClick: (String) -> Unit,
    onDemolitionClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: GeometryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showOpeningDialog by remember { mutableStateOf<Int?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
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
                title = state.room?.name ?: "Геометрія",
                onBack = onBack,
                actions = {
                    if (state.hasUnsavedChanges) {
                        TextButton(onClick = { viewModel.saveGeometry() }) {
                            Text("Зберегти", color = CubeMasterColors.red)
                        }
                    }
                }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Вкладки: Геометрія / Поверхні / Прорізи
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Геометрія") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Поверхні") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Прорізи") })
            }

            when (selectedTab) {
                0 -> GeometryTab(state, viewModel)
                1 -> SurfacesTab(state, onLayersClick, viewModel)
                2 -> OpeningsTab(state, onOpeningAdd = { edgeIndex -> showOpeningDialog = edgeIndex }, onDelete = { viewModel.deleteOpening(it) })
            }

            // Кнопка демонтажу
            OutlinedButton(
                onClick = onDemolitionClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Construction, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Демонтаж")
            }

            // Попередження нев'язки
            state.closureWarning?.let { WarningCard(it) }
        }
    }

    showOpeningDialog?.let { edgeIndex ->
        AddOpeningDialog(
            edgeIndex = edgeIndex,
            onConfirm = { kind, w, h, sill ->
                viewModel.addOpening(edgeIndex, kind, w, h, sill)
                showOpeningDialog = null
            },
            onDismiss = { showOpeningDialog = null }
        )
    }
}

@Composable
private fun GeometryTab(state: GeometryUiState, viewModel: GeometryViewModel) {
    var drawMode by remember { mutableStateOf(false) }

    // Перемикач прямокутник/полігон/малювати
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = state.isRectangle && !drawMode,
            onClick = { drawMode = false; viewModel.setRectangleMode(true) },
            label = { Text("Прямокутник") }
        )
        FilterChip(
            selected = !state.isRectangle && !drawMode,
            onClick = { drawMode = false; viewModel.setRectangleMode(false) },
            label = { Text("Довільний контур") }
        )
        FilterChip(
            selected = drawMode,
            onClick = { drawMode = true },
            label = { Text("Малювати") }
        )
    }

    if (drawMode) {
        var useFloorplan by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !useFloorplan,
                onClick = { useFloorplan = false },
                label = { Text("Порожній аркуш") }
            )
            FilterChip(
                selected = useFloorplan,
                onClick = { useFloorplan = true },
                label = { Text("За фото плану") }
            )
        }
        Spacer(Modifier.height(8.dp))
        if (useFloorplan) {
            FloorplanDrawFlow(
                onShapeConfirmed = { edges ->
                    viewModel.applyDrawnEdges(edges)
                    drawMode = false
                },
                onImagePicked = { uri -> viewModel.addFloorplanPhoto(uri) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            FreehandDrawCanvas(
                onShapeConfirmed = { edges ->
                    viewModel.applyDrawnEdges(edges)
                    drawMode = false
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    if (state.isRectangle) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberInputField(
                value = state.widthMm,
                onValueChange = viewModel::setWidth,
                label = "Ширина",
                unit = "мм",
                modifier = Modifier.weight(1f)
            )
            NumberInputField(
                value = state.lengthMm,
                onValueChange = viewModel::setLength,
                label = "Довжина",
                unit = "мм",
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        // Таблиця ребер
        state.edges.forEachIndexed { i, edge ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}.", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(24.dp))
                NumberInputField(
                    value = edge.lengthMm,
                    onValueChange = { viewModel.setEdgeLength(i, it) },
                    label = "Довжина",
                    unit = "мм",
                    modifier = Modifier.weight(1f)
                )
                NumberInputField(
                    value = edge.angleDeg,
                    onValueChange = { viewModel.setEdgeAngle(i, it) },
                    label = "Кут",
                    unit = "°",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.addEdge() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Ребро")
            }
            OutlinedButton(
                onClick = { viewModel.removeEdge() },
                enabled = state.edges.size > 3,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Remove, null); Spacer(Modifier.width(4.dp)); Text("Прибрати")
            }
        }
    }

    // План кімнати — тепер завжди, і для прямокутника, і для довільного контуру
    if (state.polygonVertices.isNotEmpty()) {
        RoomPreview(
            vertices = state.polygonVertices,
            status = if (state.polygonResult?.selfIntersects == true) ClosureStatus.Error
                else state.polygonResult?.status ?: ClosureStatus.Ok,
            openings = state.openings,
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )
    }

    NumberInputField(
        value = state.heightMm,
        onValueChange = viewModel::setHeight,
        label = "Висота стелі",
        unit = "мм"
    )

    // Підсумок розмірів
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Площа підлоги: ${String.format("%.2f", state.floorAreaM2)} м²", style = MaterialTheme.typography.bodyMedium)
            Text("Периметр: ${String.format("%.2f", state.perimeter)} м", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SurfacesTab(state: GeometryUiState, onLayersClick: (String) -> Unit, viewModel: GeometryViewModel) {
    val surfaceKinds = listOf(SurfaceKind.Floor to "Підлога", SurfaceKind.Ceiling to "Стеля") +
        state.edges.indices.map { SurfaceKind.Wall to "Стіна ${it + 1}" }

    val uniqueKinds = listOf(
        SurfaceKind.Floor to "Підлога",
        SurfaceKind.Ceiling to "Стеля"
    ) + (state.surfaces.filter { it.kind == SurfaceKind.Wall }.mapIndexed { i, s ->
        SurfaceKind.Wall to "Стіна ${(s.wallEdgeIndex ?: i) + 1}"
    })

    state.surfaces.forEach { surface ->
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onLayersClick(surface.id) }
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = surfaceKindLabel(surface.kind, surface.wallEdgeIndex),
                            style = MaterialTheme.typography.titleSmall
                        )
                        CompletionBadge(surface.layers.size)
                    }
                    if (surface.layers.isNotEmpty()) {
                        LayerStackIndicator(
                            layers = surface.layers,
                            modifier = Modifier.width(80.dp).height(24.dp)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
                Spacer(Modifier.height(8.dp))
                val attachments by viewModel.observeAttachments(surface.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                AttachmentsSection(
                    attachments = attachments,
                    onAddPhoto = { uri -> viewModel.addPhoto(surface.id, uri) },
                    onAddPdf = { uri -> viewModel.addPdf(surface.id, uri) },
                    onAddNote = { text -> viewModel.addNote(surface.id, text) },
                    onDelete = { viewModel.deleteAttachment(it) }
                )
            }
        }
    }

    // Кнопка для додавання стінних поверхонь
    OutlinedButton(
        onClick = {
            val newWallIndex = state.surfaces.count { it.kind == SurfaceKind.Wall }
            val s = viewModel.ensureSurfaceExists(SurfaceKind.Wall)
            onLayersClick(s.id)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Додати стіну")
    }
}

@Composable
private fun OpeningsTab(
    state: GeometryUiState,
    onOpeningAdd: (Int) -> Unit,
    onDelete: (String) -> Unit
) {
    val edgeCount = if (state.isRectangle) 4 else state.edges.size
    for (i in 0 until edgeCount) {
        val openingsOnEdge = state.openings.filter { it.wallEdgeIndex == i }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Стіна ${i + 1}", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { onOpeningAdd(i) }) {
                Icon(Icons.Default.Add, contentDescription = "Додати проріз")
            }
        }
        if (openingsOnEdge.isEmpty()) {
            Text("Прорізів немає", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            openingsOnEdge.forEach { opening ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${openingKindLabel(opening.kind)}: ${opening.widthMm}×${opening.heightMm} мм",
                        style = MaterialTheme.typography.bodySmall
                    )
                    IconButton(onClick = { onDelete(opening.id) }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        ThinDivider()
    }
}

// План кімнати "згори" — контур, підписи довжин стін, прорізи (двері/вікна) на своїх стінах.
// Працює і для прямокутника, і для довільного контуру — обидва дають однаковий List<Vertex>.
@Composable
private fun RoomPreview(
    vertices: List<Vertex>,
    status: ClosureStatus,
    openings: List<Opening>,
    modifier: Modifier = Modifier
) {
    val strokeColor = when (status) {
        ClosureStatus.Ok -> CubeMasterColors.success
        ClosureStatus.WarningAutoFixed -> CubeMasterColors.warning
        ClosureStatus.Error -> CubeMasterColors.error
    }
    val fillColor = CubeMasterColors.gold
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val labelTextSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 11.sp.toPx() }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offset += pan
                }
            }
    ) {
        if (vertices.isEmpty()) return@Canvas
        val n = vertices.size
        val padding = 40f
        val minX = vertices.minOf { it.x }
        val maxX = vertices.maxOf { it.x }
        val minY = vertices.minOf { it.y }
        val maxY = vertices.maxOf { it.y }
        val rangeX = (maxX - minX).coerceAtLeast(0.001)
        val rangeY = (maxY - minY).coerceAtLeast(0.001)
        val scaleXY = minOf(
            (size.width - padding * 2) / rangeX,
            (size.height - padding * 2) / rangeY
        ) * scale

        fun toCanvas(v: Vertex): Offset {
            val x = padding + (v.x - minX) * scaleXY + offset.x
            val y = size.height - padding - (v.y - minY) * scaleXY + offset.y
            return Offset(x.toFloat(), y.toFloat())
        }

        val path = Path()
        val first = toCanvas(vertices.first())
        path.moveTo(first.x, first.y)
        vertices.drop(1).forEach { path.lineTo(toCanvas(it).x, toCanvas(it).y) }
        path.close()

        drawPath(path, fillColor.copy(alpha = 0.14f))
        val wallStrokePx = 3.dp.toPx()
        drawPath(path, strokeColor, style = Stroke(width = wallStrokePx))

        // Прорізи малюємо нейтральним "кресленським" кольором — не прив'язаним до статусу
        // нев'язки, щоб вікно/двері були однаково читабельні і на зеленому, і на червоному контурі.
        val openingColor = CubeMasterColors.graphite
        openings.forEach { opening ->
            val wallIdx = opening.wallEdgeIndex
            if (wallIdx !in 0 until n) return@forEach
            val p1 = vertices[wallIdx]
            val p2 = vertices[(wallIdx + 1) % n]
            val wallLenM = distance(p1, p2)
            if (wallLenM <= 0.0) return@forEach
            val openWidthM = (opening.widthMm / 1000.0).coerceAtMost(wallLenM * 0.9)
            val tHalf = (openWidthM / wallLenM) / 2
            val tStart = (0.5 - tHalf).coerceIn(0.0, 1.0)
            val tEnd = (0.5 + tHalf).coerceIn(0.0, 1.0)
            val gapStart = toCanvas(Vertex(p1.x + (p2.x - p1.x) * tStart, p1.y + (p2.y - p1.y) * tStart))
            val gapEnd = toCanvas(Vertex(p1.x + (p2.x - p1.x) * tEnd, p1.y + (p2.y - p1.y) * tEnd))

            // "Стерти" суцільну стіну під прорізом
            drawLine(bgColor, gapStart, gapEnd, strokeWidth = wallStrokePx + 3f)

            when (opening.kind) {
                OpeningKind.Window -> {
                    val dx = gapEnd.x - gapStart.x
                    val dy = gapEnd.y - gapStart.y
                    val len = hypot(dx, dy).coerceAtLeast(1f)
                    val perp = Offset(-dy / len, dx / len) * 5f
                    drawLine(openingColor, gapStart + perp, gapEnd + perp, strokeWidth = 2.dp.toPx())
                    drawLine(openingColor, gapStart - perp, gapEnd - perp, strokeWidth = 2.dp.toPx())
                }
                OpeningKind.Door -> {
                    val radius = hypot((gapEnd.x - gapStart.x), (gapEnd.y - gapStart.y))
                    val startAngleDeg = Math.toDegrees(
                        atan2((gapEnd.y - gapStart.y).toDouble(), (gapEnd.x - gapStart.x).toDouble())
                    ).toFloat()
                    drawLine(openingColor, gapStart, gapEnd, strokeWidth = 2.dp.toPx())
                    drawArc(
                        color = openingColor.copy(alpha = 0.6f),
                        startAngle = startAngleDeg,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(gapStart.x - radius, gapStart.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                OpeningKind.Passage -> Unit // лише розрив стіни, вже намальований вище
            }
        }

        // Підписи довжин стін — виносимо ЗОВНІ контуру (вздовж зовнішньої нормалі стіни),
        // щоб текст не накладався на прорізи, намальовані всередині стін.
        val centroid = Vertex(vertices.sumOf { it.x } / n, vertices.sumOf { it.y } / n)
        val centroidCanvas = toCanvas(centroid)
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val paint = android.graphics.Paint().apply {
            color = labelColor
            textSize = labelTextSizePx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        for (i in 0 until n) {
            val a = vertices[i]
            val b = vertices[(i + 1) % n]
            val lengthMm = Math.round(distance(a, b) * 1000.0)
            val mid = toCanvas(Vertex((a.x + b.x) / 2, (a.y + b.y) / 2))
            val outX = mid.x - centroidCanvas.x
            val outY = mid.y - centroidCanvas.y
            val outLen = hypot(outX, outY).coerceAtLeast(1f)
            val labelX = mid.x + (outX / outLen) * 16f
            val labelY = mid.y + (outY / outLen) * 16f
            nativeCanvas.drawText("$lengthMm мм", labelX, labelY, paint)
        }

        // Кутові точки
        vertices.forEach { v -> drawCircle(strokeColor, 4f, toCanvas(v)) }
    }
}

private fun surfaceKindLabel(kind: SurfaceKind, edgeIndex: Int?) = when (kind) {
    SurfaceKind.Floor -> "Підлога"
    SurfaceKind.Ceiling -> "Стеля"
    SurfaceKind.Wall -> "Стіна ${(edgeIndex ?: 0) + 1}"
}

private fun openingKindLabel(kind: OpeningKind) = when (kind) {
    OpeningKind.Window -> "Вікно"
    OpeningKind.Door -> "Двері"
    OpeningKind.Passage -> "Прохід"
}

@Composable
private fun AddOpeningDialog(
    edgeIndex: Int,
    onConfirm: (OpeningKind, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(OpeningKind.Window) }
    var width by remember { mutableStateOf("1200") }
    var height by remember { mutableStateOf("1400") }
    var sill by remember { mutableStateOf("800") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text("Проріз на стіні ${edgeIndex + 1}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OpeningKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(openingKindLabel(k)) }
                        )
                    }
                }
                NumberInputField(width, { width = it }, "Ширина", "мм")
                NumberInputField(height, { height = it }, "Висота", "мм")
                if (kind == OpeningKind.Window) {
                    NumberInputField(sill, { sill = it }, "Підвіконня", "мм")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    kind,
                    width.toIntOrNull() ?: 1200,
                    height.toIntOrNull() ?: 1400,
                    if (kind == OpeningKind.Window) sill.toIntOrNull() ?: 800 else 0
                )
            }) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
