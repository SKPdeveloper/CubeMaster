package com.example.cubemaster.presentation.geometry

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.geometry.*
import com.cubemaster.core.model.*
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.domain.presetLayersFor
import com.example.cubemaster.domain.repository.AttachmentRepository
import com.example.cubemaster.domain.repository.RoomRepository
import com.example.cubemaster.domain.repository.SurfaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GeometryUiState(
    val room: Room? = null,
    val openings: List<Opening> = emptyList(),
    val surfaces: List<Surface> = emptyList(),
    val vertices: List<Vertex> = emptyList(),
    val heightMm: String = "2700",
    val heightMode: HeightMode = HeightMode.Uniform,
    val floorAreaM2: Double = 0.0,
    val perimeter: Double = 0.0,
    val selfIntersects: Boolean = false,
    val openingWarning: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    // Похідна таблиця чисел (довжина+кут) для UI — рахується з vertices щоразу.
    val edges: List<Edge> get() = if (vertices.size >= 3) verticesToEdges(vertices) else emptyList()
}

@HiltViewModel
class GeometryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomRepo: RoomRepository,
    private val surfaceRepo: SurfaceRepository,
    private val attachmentRepo: AttachmentRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val roomId: String = savedStateHandle["roomId"]!!
    private val uid: String get() = auth.currentUserId ?: ""
    private val _state = MutableStateFlow(GeometryUiState())
    val state: StateFlow<GeometryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            roomRepo.observeRoom(roomId).filterNotNull().first().let { room ->
                _state.update { s ->
                    s.copy(
                        room = room,
                        vertices = (room.geometry as RoomGeometry.Polygon).vertices,
                        heightMm = room.heightMm?.toString() ?: s.heightMm,
                        heightMode = room.heightMode
                    )
                }
                recalculate()
            }
        }
        viewModelScope.launch {
            roomRepo.observeOpenings(roomId).collect { openings ->
                _state.update { it.copy(openings = openings) }
                recalculate()
            }
        }
        viewModelScope.launch {
            surfaceRepo.observeSurfaces(roomId).collect { surfaces ->
                _state.update { it.copy(surfaces = surfaces) }
            }
        }
    }

    fun setHeight(v: String) { _state.update { it.copy(heightMm = v, hasUnsavedChanges = true) } }

    fun moveVertexAction(index: Int, newPosition: Vertex) {
        _state.update { it.copy(vertices = moveVertex(it.vertices, index, newPosition), hasUnsavedChanges = true) }
        recalculate()
    }

    fun applyInitialVertices(vertices: List<Vertex>) {
        _state.update { it.copy(vertices = vertices, hasUnsavedChanges = true) }
        recalculate()
    }

    // Вставляє вершину на стіні edgeIndex і переносить прорізи: ті, що були
    // на edgeIndex, лишаються на ній, якщо їхній offset менший за відстань
    // до нової точки, інакше переїжджають на нову стіну (edgeIndex+1) з
    // відповідно скоригованим offset. Прорізи на стінах після edgeIndex
    // зсувають номер стіни на +1.
    fun insertVertex(edgeIndex: Int, atPoint: Vertex) {
        val s = _state.value
        val a = s.vertices.getOrNull(edgeIndex) ?: return
        val splitDistMm = Math.round(distance(a, atPoint) * 1000.0).toInt()
        val newVertices = insertVertexOnEdge(s.vertices, edgeIndex, atPoint)
        val straddling = s.openings.any { o ->
            o.wallEdgeIndex == edgeIndex && o.offsetMm < splitDistMm && o.offsetMm + o.widthMm > splitDistMm
        }
        _state.update {
            it.copy(
                vertices = newVertices,
                hasUnsavedChanges = true,
                error = if (straddling) "Проріз перетинає нову стіну — перевірте його розташування" else it.error
            )
        }
        viewModelScope.launch {
            s.openings.forEach { o ->
                when {
                    o.wallEdgeIndex < edgeIndex -> Unit
                    o.wallEdgeIndex == edgeIndex && o.offsetMm < splitDistMm -> Unit
                    o.wallEdgeIndex == edgeIndex -> roomRepo.upsertOpening(o.copy(wallEdgeIndex = edgeIndex + 1, offsetMm = o.offsetMm - splitDistMm))
                    else -> roomRepo.upsertOpening(o.copy(wallEdgeIndex = o.wallEdgeIndex + 1))
                }
            }
        }
        recalculate()
    }

    // Повертає false і не видаляє вершину, якщо на суміжній стіні (до або
    // після) є хоч один проріз — користувач має спершу прибрати прорізи.
    fun removeVertexAction(index: Int): Boolean {
        val s = _state.value
        val n = s.vertices.size
        val prevEdge = (index - 1 + n) % n
        val hasAdjacentOpening = s.openings.any { it.wallEdgeIndex == prevEdge || it.wallEdgeIndex == index }
        if (hasAdjacentOpening) {
            _state.update { it.copy(error = "Спершу приберіть проріз(и) на суміжній стіні") }
            return false
        }
        val newVertices = removeVertex(s.vertices, index) ?: run {
            _state.update { it.copy(error = "Кімната повинна мати щонайменше 3 стіни") }
            return false
        }
        _state.update { it.copy(vertices = newVertices, hasUnsavedChanges = true) }
        viewModelScope.launch {
            s.openings.forEach { o ->
                if (o.wallEdgeIndex > index) {
                    roomRepo.upsertOpening(o.copy(wallEdgeIndex = o.wallEdgeIndex - 1))
                }
            }
        }
        recalculate()
        return true
    }

    fun setEdgeLengthAction(edgeIndex: Int, newLengthMm: Int) {
        _state.update { it.copy(vertices = setEdgeLength(it.vertices, edgeIndex, newLengthMm), hasUnsavedChanges = true) }
        recalculate()
    }

    fun setInteriorAngleAction(vertexIndex: Int, newAngleDeg: Double) {
        _state.update { it.copy(vertices = setInteriorAngle(it.vertices, vertexIndex, newAngleDeg), hasUnsavedChanges = true) }
        recalculate()
    }

    // Додає нову вершину посередині останньої стіни — зручний спосіб додати
    // кут без точного довгого натискання по канвасу.
    fun addEdgeAtEnd() {
        val s = _state.value
        val n = s.vertices.size
        if (n < 3) return
        val a = s.vertices[n - 1]
        val b = s.vertices[0]
        val mid = Vertex((a.x + b.x) / 2, (a.y + b.y) / 2)
        insertVertex(n - 1, mid)
    }

    fun addOpening(wallEdgeIndex: Int, offsetMm: Int, kind: OpeningKind, widthMm: Int, heightMm: Int, sillMm: Int) {
        viewModelScope.launch {
            roomRepo.upsertOpening(
                Opening(UUID.randomUUID().toString(), roomId, wallEdgeIndex, kind, widthMm, heightMm, sillMm, offsetMm)
            )
        }
    }

    fun updateOpening(id: String, wallEdgeIndex: Int, offsetMm: Int, kind: OpeningKind, widthMm: Int, heightMm: Int, sillMm: Int) {
        viewModelScope.launch {
            roomRepo.upsertOpening(Opening(id, roomId, wallEdgeIndex, kind, widthMm, heightMm, sillMm, offsetMm))
        }
    }

    fun moveOpening(openingId: String, newOffsetMm: Int) {
        val existing = _state.value.openings.firstOrNull { it.id == openingId } ?: return
        viewModelScope.launch { roomRepo.upsertOpening(existing.copy(offsetMm = newOffsetMm)) }
    }

    fun deleteOpening(id: String) {
        viewModelScope.launch { roomRepo.deleteOpening(id) }
    }

    fun saveGeometry() {
        val s = _state.value
        if (s.selfIntersects) {
            _state.update { it.copy(error = "Стіни контуру перетинаються. Виправте форму перед збереженням.") }
            return
        }
        if (s.openingWarning != null) {
            _state.update { it.copy(error = "Виправте прорізи перед збереженням: ${s.openingWarning}") }
            return
        }
        val room = s.room ?: return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            roomRepo.updateRoom(
                room.copy(
                    geometry = RoomGeometry.Polygon(s.vertices),
                    heightMm = s.heightMm.toIntOrNull()
                )
            )
            _state.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
        }
    }

    private fun recalculate() {
        val s = _state.value
        if (s.vertices.size < 3) return
        _state.update {
            it.copy(
                floorAreaM2 = polygonAreaM2(it.vertices),
                perimeter = perimeterM(it.vertices),
                selfIntersects = hasSelfIntersection(it.vertices),
                openingWarning = computeOpeningWarning(it.vertices, it.openings)
            )
        }
    }

    private fun computeOpeningWarning(vertices: List<Vertex>, openings: List<Opening>): String? {
        val n = vertices.size
        if (n < 3) return null
        val problems = mutableListOf<String>()
        for (i in 0 until n) {
            val wallLenMm = Math.round(distance(vertices[i], vertices[(i + 1) % n]) * 1000.0).toInt()
            val wallOpenings = openings.filter { it.wallEdgeIndex == i }
            problems += validateWallOpenings(wallLenMm, wallOpenings)
        }
        return problems.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun ensureSurfaceExists(kind: SurfaceKind): Surface {
        val existing = _state.value.surfaces.firstOrNull { it.kind == kind }
        if (existing != null) return existing
        val roomType = _state.value.room?.roomType
        val layers = if (roomType != null) presetLayersFor(roomType, kind) else emptyList()
        val surface = Surface(UUID.randomUUID().toString(), roomId, kind, null, layers)
        viewModelScope.launch { surfaceRepo.upsertSurface(surface) }
        return surface
    }

    fun addFloorplanPhoto(uri: Uri) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch {
            try {
                attachmentRepo.addPhoto(uid, projectId, roomId, AttachmentParent.Room, roomId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити план: ${e.message}") }
            }
        }
    }

    fun observeAttachments(surfaceId: String) = attachmentRepo.observeForParent(AttachmentParent.Surface, surfaceId)

    fun addPhoto(surfaceId: String, uri: Uri) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch {
            try {
                attachmentRepo.addPhoto(uid, projectId, roomId, AttachmentParent.Surface, surfaceId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити фото: ${e.message}") }
            }
        }
    }

    fun addPdf(surfaceId: String, uri: Uri) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch {
            try {
                attachmentRepo.addPdf(uid, projectId, roomId, AttachmentParent.Surface, surfaceId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити PDF: ${e.message}") }
            }
        }
    }

    fun addNote(surfaceId: String, text: String) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch { attachmentRepo.addNote(projectId, roomId, AttachmentParent.Surface, surfaceId, text) }
    }

    fun deleteAttachment(attachment: Attachment) {
        viewModelScope.launch { attachmentRepo.delete(uid, attachment) }
    }
}
