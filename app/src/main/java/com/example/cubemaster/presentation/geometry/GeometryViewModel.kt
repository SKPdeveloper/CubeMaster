package com.example.cubemaster.presentation.geometry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.geometry.*
import com.cubemaster.core.model.*
import com.example.cubemaster.domain.repository.RoomRepository
import com.example.cubemaster.domain.repository.SurfaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EdgeInput(val lengthMm: String = "", val angleDeg: String = "90")

data class GeometryUiState(
    val room: Room? = null,
    val openings: List<Opening> = emptyList(),
    val surfaces: List<Surface> = emptyList(),
    val isRectangle: Boolean = true,
    val widthMm: String = "4000",
    val lengthMm: String = "3000",
    val heightMm: String = "2700",
    val heightMode: HeightMode = HeightMode.Uniform,
    val cornerHeightsMm: List<String> = List(4) { "2700" },
    val edges: List<EdgeInput> = List(4) { EdgeInput("3000", "90") },
    val polygonResult: PolygonResult? = null,
    val polygonVertices: List<Vertex> = emptyList(),
    val floorAreaM2: Double = 0.0,
    val perimeter: Double = 0.0,
    val closureWarning: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GeometryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomRepo: RoomRepository,
    private val surfaceRepo: SurfaceRepository
) : ViewModel() {

    private val roomId: String = savedStateHandle["roomId"]!!
    private val _state = MutableStateFlow(GeometryUiState())
    val state: StateFlow<GeometryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            roomRepo.observeRoom(roomId).filterNotNull().first().let { room ->
                val isRect = room.geometry is RoomGeometry.Rectangle
                _state.update { s ->
                    s.copy(
                        room = room,
                        isRectangle = isRect,
                        widthMm = if (isRect) (room.geometry as RoomGeometry.Rectangle).widthMm.toString() else s.widthMm,
                        lengthMm = if (isRect) (room.geometry as RoomGeometry.Rectangle).lengthMm.toString() else s.lengthMm,
                        heightMm = room.heightMm?.toString() ?: s.heightMm,
                        heightMode = room.heightMode,
                        edges = if (!isRect) {
                            (room.geometry as RoomGeometry.Polygon).edges.map {
                                EdgeInput(it.lengthMm.toString(), it.interiorAngleDeg.toString())
                            }
                        } else s.edges
                    )
                }
                recalculate()
            }
        }
        viewModelScope.launch {
            roomRepo.observeOpenings(roomId).collect { openings ->
                _state.update { it.copy(openings = openings) }
            }
        }
        viewModelScope.launch {
            surfaceRepo.observeSurfaces(roomId).collect { surfaces ->
                _state.update { it.copy(surfaces = surfaces) }
            }
        }
    }

    fun setRectangleMode(isRect: Boolean) {
        _state.update { it.copy(isRectangle = isRect, hasUnsavedChanges = true) }
        recalculate()
    }

    fun setWidth(v: String) { _state.update { it.copy(widthMm = v, hasUnsavedChanges = true) }; recalculate() }
    fun setLength(v: String) { _state.update { it.copy(lengthMm = v, hasUnsavedChanges = true) }; recalculate() }
    fun setHeight(v: String) { _state.update { it.copy(heightMm = v, hasUnsavedChanges = true) } }

    fun setEdgeLength(index: Int, v: String) {
        _state.update { s ->
            val edges = s.edges.toMutableList()
            if (index in edges.indices) edges[index] = edges[index].copy(lengthMm = v)
            s.copy(edges = edges, hasUnsavedChanges = true)
        }
        recalculate()
    }

    fun setEdgeAngle(index: Int, v: String) {
        _state.update { s ->
            val edges = s.edges.toMutableList()
            if (index in edges.indices) edges[index] = edges[index].copy(angleDeg = v)
            s.copy(edges = edges, hasUnsavedChanges = true)
        }
        recalculate()
    }

    fun addEdge() {
        _state.update { it.copy(edges = it.edges + EdgeInput("3000", "90"), hasUnsavedChanges = true) }
        recalculate()
    }

    fun removeEdge() {
        if (_state.value.edges.size > 3) {
            _state.update { it.copy(edges = it.edges.dropLast(1), hasUnsavedChanges = true) }
            recalculate()
        }
    }

    fun addOpening(wallEdgeIndex: Int, kind: OpeningKind, widthMm: Int, heightMm: Int, sillMm: Int) {
        viewModelScope.launch {
            roomRepo.upsertOpening(
                Opening(UUID.randomUUID().toString(), roomId, wallEdgeIndex, kind, widthMm, heightMm, sillMm)
            )
        }
    }

    fun deleteOpening(id: String) {
        viewModelScope.launch { roomRepo.deleteOpening(id) }
    }

    fun saveGeometry() {
        val s = _state.value
        if (s.polygonResult?.status == ClosureStatus.Error) {
            _state.update { it.copy(error = "Нев'язка контуру перевищує 10 см. Перевірте виміри.") }
            return
        }
        val room = s.room ?: return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val geometry = if (s.isRectangle) {
                RoomGeometry.Rectangle(
                    s.widthMm.toIntOrNull() ?: 4000,
                    s.lengthMm.toIntOrNull() ?: 3000
                )
            } else {
                val edges = s.edges.mapNotNull { e ->
                    val l = e.lengthMm.toIntOrNull() ?: return@mapNotNull null
                    val a = e.angleDeg.toDoubleOrNull() ?: 90.0
                    Edge(l, a)
                }
                RoomGeometry.Polygon(edges)
            }
            roomRepo.updateRoom(
                room.copy(
                    geometry = geometry,
                    heightMm = s.heightMm.toIntOrNull()
                )
            )
            _state.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
        }
    }

    private fun recalculate() {
        val s = _state.value
        if (s.isRectangle) {
            val w = s.widthMm.toIntOrNull() ?: return
            val l = s.lengthMm.toIntOrNull() ?: return
            val area = rectangleAreaM2(w, l)
            val perimeter = rectanglePerimeterM(w, l)
            _state.update { it.copy(floorAreaM2 = area, perimeter = perimeter, closureWarning = null) }
        } else {
            val edges = s.edges.mapNotNull { e ->
                val l = e.lengthMm.toIntOrNull() ?: return
                val a = e.angleDeg.toDoubleOrNull() ?: 90.0
                Edge(l, a)
            }
            if (edges.size < 3) return

            val angleError = validateAngleSum(edges)
            if (angleError > 1.0) {
                _state.update { it.copy(closureWarning = "Сума кутів відхиляється на ${String.format("%.1f", angleError)}° від теоретичної") }
            }

            val result = buildPolygon(edges)
            val area = polygonAreaM2(result.vertices)
            val perimeter = perimeterM(result.vertices)
            val warning = when (result.status) {
                ClosureStatus.WarningAutoFixed ->
                    "Нев'язка замикання ${String.format("%.1f", result.closureErrorM * 100)} см — застосована автокорекція"
                ClosureStatus.Error ->
                    "Нев'язка замикання ${String.format("%.1f", result.closureErrorM * 100)} см перевищує допустимі 10 см"
                else -> null
            }
            _state.update { it.copy(
                polygonResult = result,
                polygonVertices = result.vertices,
                floorAreaM2 = area,
                perimeter = perimeter,
                closureWarning = warning
            ) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun ensureSurfaceExists(kind: SurfaceKind): Surface {
        val existing = _state.value.surfaces.firstOrNull { it.kind == kind }
        if (existing != null) return existing
        val surface = Surface(UUID.randomUUID().toString(), roomId, kind, null, emptyList())
        viewModelScope.launch { surfaceRepo.upsertSurface(surface) }
        return surface
    }
}
