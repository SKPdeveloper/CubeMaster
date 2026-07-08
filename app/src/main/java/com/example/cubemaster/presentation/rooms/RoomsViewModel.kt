package com.example.cubemaster.presentation.rooms

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.geometry.polygonAreaM2
import com.cubemaster.core.geometry.rectangleVertices
import com.cubemaster.core.model.*
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.domain.presetLayersFor
import com.example.cubemaster.domain.repository.AttachmentRepository
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
    val error: String? = null,
    val areaWarning: String? = null
)

private const val AREA_MISMATCH_TOLERANCE = 0.15

@HiltViewModel
class RoomsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepo: ProjectRepository,
    private val roomRepo: RoomRepository,
    private val surfaceRepo: SurfaceRepository,
    private val attachmentRepo: AttachmentRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"]!!
    private val uid: String get() = auth.currentUserId ?: ""
    private val _state = MutableStateFlow(RoomsUiState())
    val state: StateFlow<RoomsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepo.observeProject(projectId).collect { project ->
                _state.update { it.copy(project = project) }
                updateAreaWarning()
            }
        }
        viewModelScope.launch {
            roomRepo.observeRooms(projectId).collect { rooms ->
                val items = rooms.map { room ->
                    val areaM2 = polygonAreaM2((room.geometry as RoomGeometry.Polygon).vertices)
                    val surfaces = surfaceRepo.observeSurfaces(room.id).first()
                    RoomUiItem(room, areaM2, surfaces)
                }
                _state.update { it.copy(rooms = items, isLoading = false) }
                updateAreaWarning()
            }
        }
    }

    private fun updateAreaWarning() {
        val s = _state.value
        val documented = s.project?.documentedAreaM2
        if (documented == null || documented <= 0.0 || s.rooms.isEmpty()) {
            _state.update { it.copy(areaWarning = null) }
            return
        }
        val sumOfRooms = s.rooms.sumOf { it.floorAreaM2 }
        val relativeDiff = kotlin.math.abs(sumOfRooms - documented) / documented
        val warning = if (relativeDiff > AREA_MISMATCH_TOLERANCE) {
            "Сума площ кімнат (${String.format("%.2f", sumOfRooms)} м²) суттєво відрізняється від площі за документами (${String.format("%.2f", documented)} м²) — перевірте виміри."
        } else null
        _state.update { it.copy(areaWarning = warning) }
    }

    fun createRoom(name: String, roomType: RoomType) {
        viewModelScope.launch {
            val sortOrder = (_state.value.rooms.maxOfOrNull { it.room.sortOrder } ?: 0) + 1
            try {
                val room = roomRepo.createRoom(
                    projectId = projectId,
                    name = name,
                    geometry = RoomGeometry.Polygon(rectangleVertices(4000, 3000)),
                    heightMode = HeightMode.Uniform,
                    heightMm = 2700,
                    cornerHeightsMm = null,
                    roomType = roomType,
                    sortOrder = sortOrder
                )
                // Автоматично створити поверхні для нової кімнати, підставивши рекомендований пресет шарів (якщо є для цього типу приміщення)
                surfaceRepo.upsertSurface(Surface(UUID.randomUUID().toString(), room.id, SurfaceKind.Floor, null, presetLayersFor(roomType, SurfaceKind.Floor)))
                surfaceRepo.upsertSurface(Surface(UUID.randomUUID().toString(), room.id, SurfaceKind.Ceiling, null, presetLayersFor(roomType, SurfaceKind.Ceiling)))
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

    fun observeAttachments(roomId: String) = attachmentRepo.observeForParent(AttachmentParent.Room, roomId)

    fun addPhoto(roomId: String, uri: Uri) {
        viewModelScope.launch {
            try {
                attachmentRepo.addPhoto(uid, projectId, roomId, AttachmentParent.Room, roomId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити фото: ${e.message}") }
            }
        }
    }

    fun addPdf(roomId: String, uri: Uri) {
        viewModelScope.launch {
            try {
                attachmentRepo.addPdf(uid, projectId, roomId, AttachmentParent.Room, roomId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити PDF: ${e.message}") }
            }
        }
    }

    fun addNote(roomId: String, text: String) {
        viewModelScope.launch { attachmentRepo.addNote(projectId, roomId, AttachmentParent.Room, roomId, text) }
    }

    fun deleteAttachment(attachment: Attachment) {
        viewModelScope.launch { attachmentRepo.delete(uid, attachment) }
    }
}
