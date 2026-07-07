package com.example.cubemaster.presentation.geometry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cubemaster.core.geometry.ClosureStatus
import com.cubemaster.core.geometry.Vertex
import com.cubemaster.core.geometry.distance
import com.cubemaster.core.geometry.validateWallOpenings
import com.cubemaster.core.model.*
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors

@Composable
fun GeometryScreen(
    roomId: String,
    onLayersClick: (String) -> Unit,
    onDemolitionClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: GeometryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openingDialogRequest by remember { mutableStateOf<OpeningDialogRequest?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    val onOpeningEdit: (String) -> Unit = { openingId ->
        state.openings.firstOrNull { it.id == openingId }?.let { opening ->
            openingDialogRequest = OpeningDialogRequest(opening.wallEdgeIndex, opening.offsetMm, opening)
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
                0 -> GeometryTab(
                    state = state,
                    viewModel = viewModel,
                    onWallTap = { wallIndex, offsetMm -> openingDialogRequest = OpeningDialogRequest(wallIndex, offsetMm) },
                    onOpeningTap = onOpeningEdit
                )
                1 -> SurfacesTab(state, onLayersClick, viewModel)
                2 -> OpeningsTab(
                    state = state,
                    onOpeningAdd = { wallIndex -> openingDialogRequest = OpeningDialogRequest(wallIndex, 0) },
                    onOpeningEdit = onOpeningEdit,
                    onDelete = { viewModel.deleteOpening(it) }
                )
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
            state.openingWarning?.let { WarningCard("Прорізи: $it") }
        }
    }

    openingDialogRequest?.let { request ->
        val wallLen = wallLengthMm(state.polygonVertices, request.wallIndex)
        val otherOpenings = state.openings.filter { it.wallEdgeIndex == request.wallIndex && it.id != request.editing?.id }
        AddOpeningDialog(
            wallIndex = request.wallIndex,
            wallLengthMm = wallLen,
            initialOffsetMm = request.offsetMm,
            otherOpeningsOnWall = otherOpenings,
            editing = request.editing,
            onConfirm = { kind, w, h, sill, offsetMm ->
                val editing = request.editing
                if (editing != null) {
                    viewModel.updateOpening(editing.id, request.wallIndex, offsetMm, kind, w, h, sill)
                } else {
                    viewModel.addOpening(request.wallIndex, offsetMm, kind, w, h, sill)
                }
                openingDialogRequest = null
            },
            onDelete = request.editing?.let { editing ->
                { viewModel.deleteOpening(editing.id); openingDialogRequest = null }
            },
            onDismiss = { openingDialogRequest = null }
        )
    }
}

private data class OpeningDialogRequest(
    val wallIndex: Int,
    val offsetMm: Int,
    val editing: Opening? = null
)

private fun wallLengthMm(vertices: List<Vertex>, wallIndex: Int): Int {
    val n = vertices.size
    if (n == 0 || wallIndex !in 0 until n) return 0
    return Math.round(distance(vertices[wallIndex], vertices[(wallIndex + 1) % n]) * 1000.0).toInt()
}

@Composable
private fun GeometryTab(
    state: GeometryUiState,
    viewModel: GeometryViewModel,
    onWallTap: (wallIndex: Int, offsetMm: Int) -> Unit,
    onOpeningTap: (openingId: String) -> Unit
) {
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
                    helperText = "Довжина цієї стіни",
                    modifier = Modifier.weight(1f)
                )
                NumberInputField(
                    value = edge.angleDeg,
                    onValueChange = { viewModel.setEdgeAngle(i, it) },
                    label = "Кут",
                    unit = "°",
                    helperText = "Внутрішній кут, 90° — прямий",
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

    // План кімнати — тепер завжди, і для прямокутника, і для довільного контуру.
    // Тап по вільній стіні додає проріз, тап/перетягування наявного прорізу — редагує його позицію.
    if (state.polygonVertices.isNotEmpty()) {
        Text(
            "Тапніть по стіні, щоб додати проріз. Перетягніть наявний проріз, щоб змістити його вздовж стіни.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RoomPlanCanvas(
            rooms = listOf(
                PlacedRoom(
                    roomId = state.room?.id ?: "",
                    label = state.room?.name ?: "",
                    vertices = state.polygonVertices,
                    openings = state.openings,
                    status = if (state.polygonResult?.selfIntersects == true) ClosureStatus.Error
                        else state.polygonResult?.status ?: ClosureStatus.Ok
                )
            ),
            mode = PlanInteractionMode.SingleRoomEdit,
            onWallTap = { _, wallIndex, offsetMm -> onWallTap(wallIndex, offsetMm) },
            onOpeningDrag = { openingId, newOffsetMm -> viewModel.moveOpening(openingId, newOffsetMm) },
            onOpeningTap = onOpeningTap,
            modifier = Modifier.fillMaxWidth().height(240.dp)
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
                            modifier = Modifier.width(80.dp).height(12.dp)
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
    onOpeningEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val edgeCount = if (state.isRectangle) 4 else state.edges.size
    for (i in 0 until edgeCount) {
        val wallLen = wallLengthMm(state.polygonVertices, i)
        val openingsOnEdge = state.openings.filter { it.wallEdgeIndex == i }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Стіна ${i + 1} · $wallLen мм", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { onOpeningAdd(i) }) {
                        Icon(Icons.Default.Add, contentDescription = "Додати проріз")
                    }
                }
                if (openingsOnEdge.isNotEmpty()) {
                    WallElevationStrip(wallLengthMm = wallLen, openings = openingsOnEdge)
                }
                if (openingsOnEdge.isEmpty()) {
                    Text("Прорізів немає", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    openingsOnEdge.forEach { opening ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).clickable { onOpeningEdit(opening.id) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(openingColor(opening.kind), CircleShape)
                                )
                                Text(
                                    "${openingKindLabel(opening.kind)}: ${opening.widthMm}×${opening.heightMm} мм, відступ ${opening.offsetMm} мм",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { onDelete(opening.id) }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Мініатюрна "елевація стіни" — горизонтальна смужка довжиною стіни з кольоровими блоками
// прорізів у їхній справжній відносній позиції (offsetMm/widthMm), а не просто текст у списку.
@Composable
private fun WallElevationStrip(wallLengthMm: Int, openings: List<Opening>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(20.dp)) {
        if (wallLengthMm <= 0) return@Canvas
        val barY = size.height / 2f
        drawLine(
            CubeMasterColors.graphiteMid.copy(alpha = 0.4f),
            Offset(0f, barY), Offset(size.width, barY),
            strokeWidth = 3.dp.toPx()
        )
        openings.forEach { o ->
            val startX = (o.offsetMm / wallLengthMm.toFloat()).coerceIn(0f, 1f) * size.width
            val endX = ((o.offsetMm + o.widthMm) / wallLengthMm.toFloat()).coerceIn(0f, 1f) * size.width
            drawLine(
                openingColor(o.kind),
                Offset(startX, barY), Offset(endX, barY),
                strokeWidth = 8.dp.toPx()
            )
        }
    }
}

private fun surfaceKindLabel(kind: SurfaceKind, edgeIndex: Int?) = when (kind) {
    SurfaceKind.Floor -> "Підлога"
    SurfaceKind.Ceiling -> "Стеля"
    SurfaceKind.Wall -> "Стіна ${(edgeIndex ?: 0) + 1}"
}

@Composable
private fun AddOpeningDialog(
    wallIndex: Int,
    wallLengthMm: Int,
    initialOffsetMm: Int,
    otherOpeningsOnWall: List<Opening>,
    editing: Opening?,
    onConfirm: (OpeningKind, widthMm: Int, heightMm: Int, sillMm: Int, offsetMm: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(editing?.kind ?: OpeningKind.Window) }
    var width by remember { mutableStateOf((editing?.widthMm ?: 1200).toString()) }
    var height by remember { mutableStateOf((editing?.heightMm ?: 1400).toString()) }
    var sill by remember { mutableStateOf((editing?.sillHeightMm ?: 800).toString()) }
    var offset by remember { mutableStateOf((editing?.offsetMm ?: initialOffsetMm).toString()) }

    val widthInt = width.toIntOrNull() ?: 0
    val heightInt = height.toIntOrNull() ?: 0
    val offsetInt = offset.toIntOrNull() ?: 0
    val candidate = Opening("__candidate__", "", wallIndex, kind, widthInt, heightInt, 0, offsetInt)
    val problems = validateWallOpenings(wallLengthMm, otherOpeningsOnWall + candidate)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(if (editing != null) "Редагувати проріз на стіні ${wallIndex + 1}" else "Проріз на стіні ${wallIndex + 1}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Довжина стіни: $wallLengthMm мм", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OpeningKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(openingKindLabel(k)) }
                        )
                    }
                }
                NumberInputField(
                    offset, { offset = it }, "Відступ від кута", "мм",
                    helperText = "Відстань від початку стіни (лівого краю на плані) до отвору"
                )
                NumberInputField(width, { width = it }, "Ширина", "мм")
                NumberInputField(height, { height = it }, "Висота", "мм")
                if (kind == OpeningKind.Window) {
                    NumberInputField(
                        sill, { sill = it }, "Підвіконня", "мм",
                        helperText = "Висота від підлоги до низу вікна"
                    )
                }
                problems.forEach { problem ->
                    Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(kind, widthInt, heightInt, if (kind == OpeningKind.Window) sill.toIntOrNull() ?: 800 else 0, offsetInt)
                },
                enabled = problems.isEmpty() && widthInt > 0 && heightInt > 0
            ) { Text(if (editing != null) "Зберегти" else "Додати") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Видалити", color = CubeMasterColors.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Скасувати") }
            }
        }
    )
}
