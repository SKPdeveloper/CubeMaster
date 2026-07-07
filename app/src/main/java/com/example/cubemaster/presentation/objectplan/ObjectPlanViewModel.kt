package com.example.cubemaster.presentation.objectplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.geometry.roomGeometryVertices
import com.cubemaster.core.model.Room
import com.example.cubemaster.domain.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val AUTO_ARRANGE_GAP_M = 0.5

data class RoomPlacement(
    val room: Room,
    val originXM: Double,
    val originYM: Double
)

data class ObjectPlanUiState(
    val roomPlacements: List<RoomPlacement> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ObjectPlanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomRepo: RoomRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"]!!
    private val _state = MutableStateFlow(ObjectPlanUiState())
    val state: StateFlow<ObjectPlanUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            roomRepo.observeRooms(projectId).collect { rooms ->
                val placements = autoArrange(rooms)
                _state.update { it.copy(roomPlacements = placements, isLoading = false) }
                // Персистимо авто-розкладку одразу для кімнат без збереженої позиції,
                // щоб вона була стабільною між відкриттями екрана, а не перераховувалась щоразу по-різному.
                rooms.filter { it.originXM == null || it.originYM == null }.forEach { room ->
                    val placement = placements.firstOrNull { it.room.id == room.id } ?: return@forEach
                    roomRepo.updateRoomPlacement(room.id, placement.originXM, placement.originYM, room.rotationDeg)
                }
            }
        }
    }

    // Проста розкладка в один ряд зліва направо для кімнат без збереженої позиції —
    // не автогенератор реального плану квартири, лише зручна стартова точка, яку користувач
    // потім перетягує на власний розсуд.
    private fun autoArrange(rooms: List<Room>): List<RoomPlacement> {
        var cursorX = 0.0
        return rooms.map { room ->
            val originXM = room.originXM
            val originYM = room.originYM
            if (originXM != null && originYM != null) {
                RoomPlacement(room, originXM, originYM)
            } else {
                val vertices = roomGeometryVertices(room.geometry)
                val widthM = (vertices.maxOf { it.x } - vertices.minOf { it.x }).coerceAtLeast(0.1)
                val placement = RoomPlacement(room, cursorX, 0.0)
                cursorX += widthM + AUTO_ARRANGE_GAP_M
                placement
            }
        }
    }

    // Живе перетягування кімнати на плані об'єкта — оновлює лише локальний UI-стан,
    // без запису в БД (щоб не плодити записи на кожен кадр жесту).
    fun moveRoomLive(roomId: String, deltaXM: Double, deltaYM: Double) {
        _state.update { s ->
            s.copy(
                roomPlacements = s.roomPlacements.map {
                    if (it.room.id == roomId) it.copy(originXM = it.originXM + deltaXM, originYM = it.originYM + deltaYM) else it
                }
            )
        }
    }

    // Викликається на відпускання пальця — фіксує поточну позицію кімнати в БД.
    fun persistRoomPlacement(roomId: String) {
        val placement = _state.value.roomPlacements.firstOrNull { it.room.id == roomId } ?: return
        viewModelScope.launch {
            roomRepo.updateRoomPlacement(roomId, placement.originXM, placement.originYM, placement.room.rotationDeg)
        }
    }
}
