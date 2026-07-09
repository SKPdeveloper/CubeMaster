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
import com.cubemaster.core.geometry.Vertex
import com.cubemaster.core.geometry.distance
import com.cubemaster.core.geometry.validateWallOpenings
import com.cubemaster.core.model.*
import com.example.cubemaster.ui.components.*
import com.example.cubemaster.ui.theme.CubeMasterColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun GeometryScreen(
    roomId: String,
    onLayersClick: (String) -> Unit,
    onDemolitionClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: GeometryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
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
                .hazeSource(hazeState)
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
                    hazeState = hazeState,
                    onWallTap = { wallIndex, offsetMm -> openingDialogRequest = OpeningDialogRequest(wallIndex, offsetMm) },
                    onOpeningTap = onOpeningEdit
                )
                1 -> SurfacesTab(state, onLayersClick, viewModel, hazeState)
                2 -> OpeningsTab(
                    state = state,
                    hazeState = hazeState,
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

            if (state.selfIntersects) {
                WarningCard("Стіни контуру перетинаються — виправте форму перед збереженням")
            }
            state.openingWarning?.let { WarningCard("Прорізи: $it") }
        }
    }

    openingDialogRequest?.let { request ->
        val wallLen = wallLengthMm(state.vertices, request.wallIndex)
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
    hazeState: HazeState?,
    onWallTap: (wallIndex: Int, offsetMm: Int) -> Unit,
    onOpeningTap: (openingId: String) -> Unit
) {
    var pendingRemoveVertex by remember { mutableStateOf<Int?>(null) }
    var showPhotoImport by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showPhotoImport = !showPhotoImport },
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (showPhotoImport) "Сховати імпорт фото плану" else "Імпортувати фото плану (замінить поточну форму)") }
    if (showPhotoImport) {
        FloorplanDrawFlow(
            onShapeConfirmed = { edges ->
                val vertices = buildVerticesFromEdges(edges)
                viewModel.applyInitialVertices(vertices)
                showPhotoImport = false
            },
            onImagePicked = { uri -> viewModel.addFloorplanPhoto(uri) },
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    if (state.vertices.size < 3) return

    Text(
        "Перетягніть вершину, щоб змінити форму. Довге натискання по стіні додає кут, довге натискання на вершині — прибирає її. Короткий тап по стіні додає проріз.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    RoomPlanCanvas(
        rooms = listOf(
            PlacedRoom(
                roomId = state.room?.id ?: "",
                label = state.room?.name ?: "",
                vertices = state.vertices,
                openings = state.openings,
                hasSelfIntersection = state.selfIntersects
            )
        ),
        mode = PlanInteractionMode.SingleRoomEdit,
        onWallTap = { _, wallIndex, offsetMm -> onWallTap(wallIndex, offsetMm) },
        onOpeningDrag = { openingId, newOffsetMm -> viewModel.moveOpening(openingId, newOffsetMm) },
        onOpeningTap = onOpeningTap,
        onVertexDrag = { _, index, newVertex -> viewModel.moveVertexAction(index, newVertex) },
        onWallLongPress = { _, wallIndex, atPoint -> viewModel.insertVertex(wallIndex, atPoint) },
        onVertexLongPress = { _, index -> pendingRemoveVertex = index },
        modifier = Modifier.fillMaxWidth().height(280.dp)
    )

    // Таблиця чисел — синхронна з канвасом в обидва боки
    state.edges.forEachIndexed { i, edge ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${i + 1}.", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(24.dp))
            NumberInputField(
                value = edge.lengthMm.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { viewModel.setEdgeLengthAction(i, it) } },
                label = "Довжина",
                unit = "мм",
                helperText = "Довжина цієї стіни",
                modifier = Modifier.weight(1f)
            )
            NumberInputField(
                value = String.format("%.1f", edge.interiorAngleDeg),
                onValueChange = { v -> v.replace(",", ".").toDoubleOrNull()?.let { viewModel.setInteriorAngleAction(i, it) } },
                label = "Кут",
                unit = "°",
                helperText = "Внутрішній кут у вершині на початку цієї стіни, 90° — прямий",
                modifier = Modifier.weight(1f)
            )
        }
    }
    OutlinedButton(onClick = { viewModel.addEdgeAtEnd() }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Стіна")
    }

    NumberInputField(
        value = state.heightMm,
        onValueChange = viewModel::setHeight,
        label = "Висота стелі",
        unit = "мм"
    )

    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Площа підлоги: ${String.format("%.2f", state.floorAreaM2)} м²", style = MaterialTheme.typography.bodyMedium)
            Text("Периметр: ${String.format("%.2f", state.perimeter)} м", style = MaterialTheme.typography.bodyMedium)
        }
    }

    pendingRemoveVertex?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingRemoveVertex = null },
            title = { Text("Прибрати кут?") },
            text = { Text("Дві сусідні стіни об'єднаються в одну.") },
            confirmButton = {
                TextButton(onClick = {
                    // Якщо видалення заблоковано (сусідній проріз або мінімум 3 стіни),
                    // viewModel.removeVertexAction сама виставляє state.error — той самий
                    // LaunchedEffect(state.error) на початку GeometryScreen покаже Snackbar,
                    // окремої обробки результату тут не потрібно.
                    viewModel.removeVertexAction(index)
                    pendingRemoveVertex = null
                }) { Text("Прибрати") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoveVertex = null }) { Text("Скасувати") } }
        )
    }
}

private fun buildVerticesFromEdges(edges: List<Edge>): List<Vertex> {
    val vertices = mutableListOf(Vertex(0.0, 0.0))
    var heading = 0.0
    var x = 0.0
    var y = 0.0
    for (i in edges.indices) {
        val lengthM = edges[i].lengthMm / 1000.0
        x += lengthM * kotlin.math.cos(heading)
        y += lengthM * kotlin.math.sin(heading)
        if (i < edges.size - 1) vertices.add(Vertex(x, y))
        if (i + 1 < edges.size) {
            val exterior = Math.PI - Math.toRadians(edges[(i + 1) % edges.size].interiorAngleDeg)
            heading -= exterior
        }
    }
    return vertices
}

@Composable
private fun SurfacesTab(
    state: GeometryUiState,
    onLayersClick: (String) -> Unit,
    viewModel: GeometryViewModel,
    hazeState: HazeState?
) {
    state.surfaces.forEach { surface ->
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState,
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
    hazeState: HazeState?,
    onOpeningAdd: (Int) -> Unit,
    onOpeningEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val edgeCount = state.edges.size
    for (i in 0 until edgeCount) {
        val wallLen = wallLengthMm(state.vertices, i)
        val openingsOnEdge = state.openings.filter { it.wallEdgeIndex == i }
        GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
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
