package com.example.cubemaster.presentation.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.calculation.*
import com.cubemaster.core.catalog.MaterialDefaults
import com.cubemaster.core.geometry.*
import com.cubemaster.core.model.*
import com.example.cubemaster.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryMaterialLine(
    val sku: String,
    val nameUa: String,
    val totalQty: Double,
    val unit: MeasurementUnit,
    val roomNames: List<String>
)

data class SummaryUiState(
    val projectTitle: String = "",
    val materialLines: List<SummaryMaterialLine> = emptyList(),
    val totalMassKg: Double = 0.0,
    val isLoading: Boolean = true
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepo: ProjectRepository,
    private val roomRepo: RoomRepository,
    private val surfaceRepo: SurfaceRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"]!!
    private val _state = MutableStateFlow(SummaryUiState())
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                projectRepo.observeProject(projectId).filterNotNull(),
                roomRepo.observeRooms(projectId)
            ) { project, rooms -> project to rooms }.collect { (project, rooms) ->
                val materialMap = mutableMapOf<String, Triple<Double, MeasurementUnit, MutableList<String>>>()
                var totalMass = 0.0

                for (room in rooms) {
                    val surfaces = surfaceRepo.observeSurfaces(room.id).first()
                    val openings = roomRepo.observeOpenings(room.id).first()
                    val roomVertices = roomGeometryVertices(room.geometry)
                    val roomPerimeterM = perimeterM(roomVertices)
                    val concaveCorners = countConcaveCorners(roomVertices)
                    for (surface in surfaces) {
                        val areaM2 = computeSurfaceArea(room, surface, openings)
                        for (layer in surface.layers) {
                            val norm = layer.materialSku?.let { MaterialDefaults.findBySku(it)?.consumptionNorm }
                            val thickness = layer.thicknessMm ?: 0.0
                            val result = computeLayerResult(layer, areaM2, norm, thickness, roomPerimeterM, concaveCorners)
                            val sku = layer.materialSku ?: layer.layerType.name
                            val nameUa = layer.materialSku?.let { MaterialDefaults.findBySku(it)?.nameUa }
                                ?: layerDisplayName(layer.layerType)
                            val unit = layer.materialSku?.let { MaterialDefaults.findBySku(it)?.unit }
                                ?: MeasurementUnit.Kg

                            val qty = when (unit) {
                                MeasurementUnit.Bag25 -> result.bagsCount.toDouble()
                                MeasurementUnit.Bag50 -> result.bagsCount.toDouble()
                                MeasurementUnit.Kg -> result.mixMassKg
                                MeasurementUnit.M2 -> areaM2
                                MeasurementUnit.M3 -> result.volumeM3
                                MeasurementUnit.L -> result.additionalLines.firstOrNull()?.qty ?: 0.0
                                else -> result.mixMassKg
                            }

                            if (qty > 0) {
                                val entry = materialMap.getOrPut(sku) { Triple(0.0, unit, mutableListOf()) }
                                materialMap[sku] = Triple(entry.first + qty, unit, entry.third.also { it.add(room.name) })
                                totalMass += result.mixMassKg
                            }

                            // Додаткові рядки
                            for (addLine in result.additionalLines) {
                                val addSku = "${sku}_${addLine.descriptionUa}"
                                val addEntry = materialMap.getOrPut(addSku) { Triple(0.0, addLine.unit, mutableListOf()) }
                                materialMap[addSku] = Triple(addEntry.first + addLine.qty, addLine.unit, addEntry.third.also { it.add(room.name) })
                            }
                        }
                    }
                }

                val lines = materialMap.map { (sku, triple) ->
                    SummaryMaterialLine(
                        sku = sku,
                        nameUa = MaterialDefaults.findBySku(sku)?.nameUa ?: sku.replace("_", " "),
                        totalQty = triple.first,
                        unit = triple.second,
                        roomNames = triple.third.distinct()
                    )
                }.sortedBy { it.nameUa }

                _state.update {
                    it.copy(
                        projectTitle = project.title,
                        materialLines = lines,
                        totalMassKg = totalMass,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun computeSurfaceArea(room: Room, surface: Surface, openings: List<Opening>): Double {
        val vertices = (room.geometry as RoomGeometry.Polygon).vertices
        return when (surface.kind) {
            SurfaceKind.Floor, SurfaceKind.Ceiling -> polygonAreaM2(vertices)
            SurfaceKind.Wall -> {
                val edgeIndex = surface.wallEdgeIndex ?: 0
                val n = vertices.size
                val edgeLengthMm = if (edgeIndex in 0 until n) {
                    Math.round(distance(vertices[edgeIndex], vertices[(edgeIndex + 1) % n]) * 1000.0).toInt()
                } else 3000
                val gross = wallAreaGross(edgeLengthMm, room.heightMm ?: 2700, room.heightMm ?: 2700)
                wallAreaNet(gross, openings.filter { it.wallEdgeIndex == edgeIndex })
            }
        }
    }

    private fun computeLayerResult(
        layer: Layer,
        areaM2: Double,
        norm: ConsumptionNorm?,
        thickness: Double,
        roomPerimeterM: Double,
        concaveCorners: Int
    ): LayerResult {
        return when (layer.layerType) {
            LayerType.ScreedCpsManual -> calculateScreedCps(areaM2, thickness)
            LayerType.ScreedCp5Bags, LayerType.ScreedSelfLevelingCement, LayerType.ScreedSelfLevelingGypsum ->
                if (norm != null) calculateScreed(areaM2, thickness, norm) else LayerResult(0.0, 0)
            LayerType.PlasterGypsumManual, LayerType.PlasterGypsumMachine,
            LayerType.PlasterCementSandManual, LayerType.PlasterCementSandMachine, LayerType.WallPutty ->
                if (norm != null) calculatePlaster(areaM2, thickness, norm, concaveCorners) else LayerResult(0.0, 0)
            LayerType.PrimerDeep, LayerType.PrimerContact -> calculatePrimer(areaM2, layer.isPorous)
            LayerType.WaterproofingCoating -> calculateWaterproofing(areaM2, roomPerimeterM)
            LayerType.FlooringLaminate, LayerType.FlooringParquet,
            LayerType.FlooringTile, LayerType.FlooringLinoleum -> calculateFlooring(areaM2, layer.layerType, layer.isDiagonal)
            LayerType.WallPaint -> calculatePaint(areaM2)
            LayerType.WallDrywall -> calculateDrywall(areaM2)
            LayerType.WallTile -> calculateWallTile(areaM2, norm = norm)
            else -> LayerResult(0.0, 0)
        }
    }

    private fun layerDisplayName(type: LayerType): String =
        type.name.replace(Regex("([A-Z])"), " $1").trim()
}
