package com.example.cubemaster.presentation.rooms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.model.*
import com.example.cubemaster.domain.repository.ProjectRepository
import com.example.cubemaster.domain.repository.RoomRepository
import com.example.cubemaster.domain.repository.SurfaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RoomUiItem(
    val room: Room,
    val floorAreaM2: Double,
    val surfaces: List<Surface>
)

data class RoomsUiState(
    val project: Project? = null,
    val rooms: List<RoomUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class RoomsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepo: ProjectRepository,
    private val roomRepo: RoomRepository,
    private val surfaceRepo: SurfaceRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"]!!
    private val _state = MutableStateFlow(RoomsUiState())
    val state: StateFlow<RoomsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepo.observeProject(projectId).collect { project ->
                _state.update { it.copy(project = project) }
            }
        }
        viewModelScope.launch {
            roomRepo.observeRooms(projectId).collect { rooms ->
                val items = rooms.map { room ->
                    val areaM2 = when (val g = room.geometry) {
                        is RoomGeometry.Rectangle -> (g.widthMm / 1000.0) * (g.lengthMm / 1000.0)
                        is RoomGeometry.Polygon -> {
                            val result = com.cubemaster.core.geometry.buildPolygon(g.edges)
                            com.cubemaster.core.geometry.polygonAreaM2(result.vertices)
                        }
                    }
                    val surfaces = surfaceRepo.observeSurfaces(room.id).first()
                    RoomUiItem(room, areaM2, surfaces)
                }
                _state.update { it.copy(rooms = items, isLoading = false) }
            }
        }
    }

    fun createRoom(name: String, roomType: RoomType) {
        viewModelScope.launch {
            val sortOrder = (_state.value.rooms.maxOfOrNull { it.room.sortOrder } ?: 0) + 1
            try {
                val room = roomRepo.createRoom(
                    projectId = projectId,
                    name = name,
                    geometry = RoomGeometry.Rectangle(4000, 3000),
                    heightMode = HeightMode.Uniform,
                    heightMm = 2700,
                    cornerHeightsMm = null,
                    roomType = roomType,
                    sortOrder = sortOrder
                )
                // Автоматично створити поверхні для нової кімнати
                surfaceRepo.upsertSurface(Surface(UUID.randomUUID().toString(), room.id, SurfaceKind.Floor, null, emptyList()))
                surfaceRepo.upsertSurface(Surface(UUID.randomUUID().toString(), room.id, SurfaceKind.Ceiling, null, emptyList()))
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            roomRepo.deleteRoom(roomId)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
