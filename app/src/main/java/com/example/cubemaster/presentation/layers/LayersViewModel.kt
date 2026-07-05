package com.example.cubemaster.presentation.layers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.calculation.*
import com.cubemaster.core.catalog.MaterialDefaults
import com.cubemaster.core.catalog.LayerPresets
import com.cubemaster.core.geometry.*
import com.cubemaster.core.model.*
import com.example.cubemaster.domain.repository.RoomRepository
import com.example.cubemaster.domain.repository.SurfaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class LayerCalculatedItem(
    val layer: Layer,
    val result: LayerResult,
    val normativeRef: String?,
    val warnings: List<String>
)

data class LayersUiState(
    val surface: Surface? = null,
    val room: Room? = null,
    val openings: List<Opening> = emptyList(),
    val calculatedLayers: List<LayerCalculatedItem> = emptyList(),
    val surfaceAreaM2: Double = 0.0,
    val waterproofingRequired: Boolean = false,
    val waterproofingPresent: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LayersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val surfaceRepo: SurfaceRepository,
    private val roomRepo: RoomRepository
) : ViewModel() {

    private val roomId: String = savedStateHandle["roomId"]!!
    private val surfaceId: String = savedStateHandle["surfaceId"]!!
    private val _state = MutableStateFlow(LayersUiState())
    val state: StateFlow<LayersUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                surfaceRepo.observeSurfaces(roomId),
                roomRepo.observeRoom(roomId).filterNotNull(),
                roomRepo.observeOpenings(roomId)
            ) { surfaces, room, openings ->
                Triple(surfaces.find { it.id == surfaceId }, room, openings)
            }.collect { (surface, room, openings) ->
                val areaM2 = computeArea(room, surface, openings)
                val calculated = surface?.layers?.map { computeLayer(it, areaM2, room) } ?: emptyList()
                val needsWaterproofing = room.roomType in listOf(RoomType.Bathroom, RoomType.KitchenWetZone, RoomType.Balcony) &&
                    surface?.kind == SurfaceKind.Floor
                val hasWaterproofing = surface?.layers?.any {
                    it.layerType == LayerType.WaterproofingCoating
                } == true
                _state.update {
                    it.copy(
                        surface = surface,
                        room = room,
                        openings = openings,
                        calculatedLayers = calculated,
                        surfaceAreaM2 = areaM2,
                        waterproofingRequired = needsWaterproofing,
                        waterproofingPresent = hasWaterproofing,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun addLayerFromPreset(preset: LayerPreset) {
        val surface = _state.value.surface ?: return
        val existingCount = surface.layers.size
        val newLayers = surface.layers + preset.layers.mapIndexed { i, l ->
            l.copy(id = UUID.randomUUID().toString(), position = existingCount + i + 1)
        }
        saveLayers(newLayers)
    }

    fun addLayer(layerType: LayerType, thicknessMm: Double?, materialSku: String?) {
        val surface = _state.value.surface ?: return
        val autoAddPrimer = requiresPrimer(surface.layers.lastOrNull()?.layerType, layerType)
        val newLayers = surface.layers.toMutableList()
        if (autoAddPrimer) {
            newLayers.add(Layer(UUID.randomUUID().toString(), newLayers.size + 1, LayerType.PrimerDeep, null, "PRIMER_DEEP"))
        }
        newLayers.add(Layer(UUID.randomUUID().toString(), newLayers.size + 1, layerType, thicknessMm, materialSku))
        saveLayers(newLayers)
    }

    fun removeLayer(layerId: String) {
        val surface = _state.value.surface ?: return
        val newLayers = surface.layers.filter { it.id != layerId }
            .mapIndexed { i, l -> l.copy(position = i + 1) }
        saveLayers(newLayers)
    }

    fun updateLayerThickness(layerId: String, thicknessMm: Double) {
        val surface = _state.value.surface ?: return
        val newLayers = surface.layers.map { if (it.id == layerId) it.copy(thicknessMm = thicknessMm) else it }
        saveLayers(newLayers)
    }

    fun moveLayerUp(layerId: String) {
        val surface = _state.value.surface ?: return
        val index = surface.layers.indexOfFirst { it.id == layerId }
        if (index <= 0) return
        val newLayers = surface.layers.toMutableList().also {
            val tmp = it[index]; it[index] = it[index - 1]; it[index - 1] = tmp
        }.mapIndexed { i, l -> l.copy(position = i + 1) }
        saveLayers(newLayers)
    }

    fun moveLayerDown(layerId: String) {
        val surface = _state.value.surface ?: return
        val index = surface.layers.indexOfFirst { it.id == layerId }
        if (index >= surface.layers.size - 1) return
        val newLayers = surface.layers.toMutableList().also {
            val tmp = it[index]; it[index] = it[index + 1]; it[index + 1] = tmp
        }.mapIndexed { i, l -> l.copy(position = i + 1) }
        saveLayers(newLayers)
    }

    val presets get() = LayerPresets.all

    private fun saveLayers(layers: List<Layer>) {
        val surface = _state.value.surface ?: return
        viewModelScope.launch {
            surfaceRepo.upsertSurface(surface.copy(layers = layers))
        }
    }

    private fun computeArea(room: Room, surface: Surface?, openings: List<Opening>): Double {
        val height = (room.heightMm ?: 2700) / 1000.0
        return when (surface?.kind) {
            SurfaceKind.Floor, SurfaceKind.Ceiling -> when (val g = room.geometry) {
                is RoomGeometry.Rectangle -> rectangleAreaM2(g.widthMm, g.lengthMm)
                is RoomGeometry.Polygon -> polygonAreaM2(buildPolygon(g.edges).vertices)
            }
            SurfaceKind.Wall -> {
                val edgeIndex = surface.wallEdgeIndex ?: 0
                val edgeLengthMm = when (val g = room.geometry) {
                    is RoomGeometry.Rectangle -> if (edgeIndex % 2 == 0) g.widthMm else g.lengthMm
                    is RoomGeometry.Polygon -> g.edges.getOrNull(edgeIndex)?.lengthMm ?: 3000
                }
                val h1 = room.heightMm ?: 2700
                val gross = wallAreaGross(edgeLengthMm, h1, h1)
                val wallOpenings = openings.filter { it.wallEdgeIndex == edgeIndex }
                wallAreaNet(gross, wallOpenings)
            }
            null -> 0.0
        }
    }

    private fun computeLayer(layer: Layer, areaM2: Double, room: Room): LayerCalculatedItem {
        val norm = layer.materialSku?.let { MaterialDefaults.findBySku(it)?.consumptionNorm }
        val thickness = layer.thicknessMm ?: 0.0
        val result: LayerResult = when (layer.layerType) {
            LayerType.ScreedCpsManual -> calculateScreedCps(areaM2, thickness)
            LayerType.ScreedCp5Bags, LayerType.ScreedSelfLevelingCement, LayerType.ScreedSelfLevelingGypsum ->
                if (norm != null) calculateScreed(areaM2, thickness, norm) else LayerResult(0.0, 0)
            LayerType.PlasterGypsumManual, LayerType.PlasterGypsumMachine,
            LayerType.PlasterCementSandManual, LayerType.PlasterCementSandMachine ->
                if (norm != null) calculatePlaster(areaM2, thickness, norm) else LayerResult(0.0, 0)
            LayerType.WallPutty ->
                if (norm != null) calculatePlaster(areaM2, thickness, norm) else LayerResult(0.0, 0)
            LayerType.PrimerDeep, LayerType.PrimerContact -> calculatePrimer(areaM2)
            LayerType.WaterproofingCoating -> calculateWaterproofing(areaM2, 0.0)
            LayerType.FlooringLaminate, LayerType.FlooringParquet,
            LayerType.FlooringTile, LayerType.FlooringLinoleum -> calculateFlooring(areaM2, layer.layerType)
            LayerType.WallPaint -> calculatePaint(areaM2)
            LayerType.WallDrywall -> calculateDrywall(areaM2)
            LayerType.WallTile -> calculateWallTile(areaM2)
            else -> LayerResult(0.0, 0)
        }
        return LayerCalculatedItem(
            layer = layer,
            result = result,
            normativeRef = norm?.normativeReference,
            warnings = result.warnings
        )
    }
}
