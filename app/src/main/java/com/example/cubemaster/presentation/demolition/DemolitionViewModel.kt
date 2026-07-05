package com.example.cubemaster.presentation.demolition

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.calculation.*
import com.cubemaster.core.model.*
import com.example.cubemaster.data.local.entity.DemolitionTaskEntity
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.domain.repository.AttachmentRepository
import com.example.cubemaster.domain.repository.DemolitionRepository
import com.example.cubemaster.domain.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject

data class DemolitionUiState(
    val room: Room? = null,
    val tasks: List<DemolitionTaskEntity> = emptyList(),
    val totalDebrisM3: Double = 0.0,
    val totalLaborHours: Double = 0.0,
    val containersCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DemolitionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomRepo: RoomRepository,
    private val demolitionRepo: DemolitionRepository,
    private val attachmentRepo: AttachmentRepository,
    private val auth: AuthRepository,
    private val json: Json
) : ViewModel() {

    private val roomId: String = savedStateHandle["roomId"]!!
    private val uid: String get() = auth.currentUserId ?: ""
    private val _state = MutableStateFlow(DemolitionUiState())
    val state: StateFlow<DemolitionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                roomRepo.observeRoom(roomId).filterNotNull(),
                demolitionRepo.observeTasks(roomId)
            ) { room, tasks -> room to tasks }.collect { (room, tasks) ->
                _state.update { it.copy(room = room, isLoading = false) }
                recalculate(tasks)
            }
        }
    }

    fun addWallRemoval(
        lengthM: Double,
        heightM: Double,
        thicknessMm: Double,
        material: WallMaterial,
        usePoweredTools: Boolean
    ) {
        val result = calculateWallRemoval(lengthM, heightM, thicknessMm, material, usePoweredTools)
        saveTask(DemolitionKind.WallRemoval, buildJsonObject {
            put("lengthM", lengthM); put("heightM", heightM); put("thicknessMm", thicknessMm)
            put("material", material.name); put("powered", usePoweredTools)
        }, result)
    }

    fun addPlasterRemoval(areaM2: Double, isGypsum: Boolean, isCeiling: Boolean = false) {
        val result = calculatePlasterRemoval(areaM2, isGypsum, isCeiling)
        saveTask(DemolitionKind.PlasterRemoval, buildJsonObject {
            put("areaM2", areaM2); put("isGypsum", isGypsum); put("isCeiling", isCeiling)
        }, result)
    }

    fun addTileRemoval(areaM2: Double) {
        val result = calculateTileRemoval(areaM2)
        saveTask(DemolitionKind.TileRemoval, buildJsonObject { put("areaM2", areaM2) }, result)
    }

    fun addScreedRemoval(areaM2: Double, thicknessMm: Double) {
        val result = calculateScreedRemoval(areaM2, thicknessMm)
        saveTask(DemolitionKind.ScreedRemoval, buildJsonObject {
            put("areaM2", areaM2); put("thicknessMm", thicknessMm)
        }, result)
    }

    fun addFlooringRemoval(areaM2: Double) {
        val result = calculateFlooringRemoval(areaM2)
        saveTask(DemolitionKind.FlooringRemoval, buildJsonObject { put("areaM2", areaM2) }, result)
    }

    fun addPaintRemoval(params: PaintRemovalParams) {
        val result = calculatePaintRemoval(params)
        saveTask(DemolitionKind.PaintRemoval, buildJsonObject {
            put("areaM2", params.areaM2); put("paintType", params.paintType.name)
            put("method", params.removalMethod.name); put("layers", params.layersCountEstimate)
        }, result)
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { demolitionRepo.deleteTask(taskId) }
    }

    private fun saveTask(kind: DemolitionKind, paramsJson: JsonObject, result: DemolitionResult) {
        val resultJson = buildJsonObject {
            put("debrisVolumeM3", result.debrisVolumeM3)
            put("debrisMassKg", result.debrisMassKg)
            put("laborHours", result.laborHours)
        }
        val entity = DemolitionTaskEntity(
            id = UUID.randomUUID().toString(),
            roomId = roomId,
            kind = kind.name,
            paramsJson = paramsJson.toString(),
            cachedResultJson = resultJson.toString()
        )
        viewModelScope.launch { demolitionRepo.upsertTask(entity) }
    }

    private fun recalculate(tasks: List<DemolitionTask>) {
        _state.update { state ->
            state.copy(
                tasks = emptyList() // simplified; full impl would parse cachedResultJson
            )
        }
    }

    fun observeAttachments() = attachmentRepo.observeForParent(AttachmentParent.Demolition, roomId)

    fun addPhoto(uri: Uri) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch {
            try {
                attachmentRepo.addPhoto(uid, projectId, roomId, AttachmentParent.Demolition, roomId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити фото: ${e.message}") }
            }
        }
    }

    fun addPdf(uri: Uri) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch {
            try {
                attachmentRepo.addPdf(uid, projectId, roomId, AttachmentParent.Demolition, roomId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити PDF: ${e.message}") }
            }
        }
    }

    fun addNote(text: String) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch { attachmentRepo.addNote(projectId, roomId, AttachmentParent.Demolition, roomId, text) }
    }

    fun deleteAttachment(attachment: Attachment) {
        viewModelScope.launch { attachmentRepo.delete(uid, attachment) }
    }
}
