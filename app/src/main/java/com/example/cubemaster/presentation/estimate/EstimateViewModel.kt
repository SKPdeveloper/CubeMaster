package com.example.cubemaster.presentation.estimate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.model.*
import com.example.cubemaster.data.local.entity.EstimateEntity
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.domain.repository.EstimateRepository
import com.example.cubemaster.domain.repository.MaterialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class EstimateUiState(
    val estimate: EstimateEntity? = null,
    val lines: List<EstimateLineUi> = emptyList(),
    val markupPercent: Double = 15.0,
    val grandTotal: Double = 0.0,
    val isGeneratingPdf: Boolean = false,
    val exportUrl: String? = null,
    val error: String? = null
)

data class EstimateLineUi(
    val id: String,
    val description: String,
    val qty: Double,
    val unitLabel: String,
    val unitPrice: Double,
    val applyMarkup: Boolean,
    val priceSource: String,
    val lineType: String
)

@HiltViewModel
class EstimateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val estimateRepo: EstimateRepository,
    private val materialRepo: MaterialRepository,
    private val auth: AuthRepository,
    private val json: Json
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"]!!
    private val _state = MutableStateFlow(EstimateUiState())
    val state: StateFlow<EstimateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            estimateRepo.observeEstimates(projectId).collect { estimates ->
                val latest = estimates.firstOrNull()
                if (latest != null) {
                    val lines = parseLines(latest.linesJson)
                    val total = computeTotal(lines, latest.markupPercent)
                    _state.update { it.copy(estimate = latest, lines = lines, markupPercent = latest.markupPercent, grandTotal = total) }
                }
            }
        }
    }

    fun addMaterialLine(description: String, qty: Double, unit: MeasurementUnit, unitPrice: Double) {
        val newLine = EstimateLineUi(
            id = UUID.randomUUID().toString(),
            description = description,
            qty = qty,
            unitLabel = unit.shortLabelUa(),
            unitPrice = unitPrice,
            applyMarkup = true,
            priceSource = PriceSource.Manual.name,
            lineType = LineType.Material.name
        )
        val lines = _state.value.lines + newLine
        saveEstimate(lines)
    }

    fun addLaborLine(description: String, qty: Double, unitPrice: Double) {
        val newLine = EstimateLineUi(
            id = UUID.randomUUID().toString(),
            description = description,
            qty = qty,
            unitLabel = MeasurementUnit.M2.shortLabelUa(),
            unitPrice = unitPrice,
            applyMarkup = false,
            priceSource = PriceSource.Manual.name,
            lineType = LineType.Labor.name
        )
        val lines = _state.value.lines + newLine
        saveEstimate(lines)
    }

    fun removeLine(id: String) {
        val lines = _state.value.lines.filter { it.id != id }
        saveEstimate(lines)
    }

    fun updateMarkup(percent: Double) {
        _state.update { it.copy(markupPercent = percent) }
        saveEstimate(_state.value.lines)
    }

    fun toggleMarkup(lineId: String) {
        val lines = _state.value.lines.map {
            if (it.id == lineId) it.copy(applyMarkup = !it.applyMarkup) else it
        }
        saveEstimate(lines)
    }

    fun generatePdf() {
        val uid = auth.currentUserId ?: return
        val estimateId = _state.value.estimate?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingPdf = true, error = null) }
            try {
                val url = estimateRepo.generatePdf(uid, projectId, estimateId)
                _state.update { it.copy(isGeneratingPdf = false, exportUrl = url) }
            } catch (e: Exception) {
                _state.update { it.copy(isGeneratingPdf = false, error = "Помилка генерації PDF: ${e.message}") }
            }
        }
    }

    fun generateXlsx() {
        val uid = auth.currentUserId ?: return
        val estimateId = _state.value.estimate?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingPdf = true, error = null) }
            try {
                val url = estimateRepo.generateXlsx(uid, projectId, estimateId)
                _state.update { it.copy(isGeneratingPdf = false, exportUrl = url) }
            } catch (e: Exception) {
                _state.update { it.copy(isGeneratingPdf = false, error = "Помилка генерації XLSX: ${e.message}") }
            }
        }
    }

    fun clearExportUrl() = _state.update { it.copy(exportUrl = null) }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun saveEstimate(lines: List<EstimateLineUi>) {
        val markup = _state.value.markupPercent
        val total = computeTotal(lines, markup)
        val entity = EstimateEntity(
            id = _state.value.estimate?.id ?: UUID.randomUUID().toString(),
            projectId = projectId,
            markupPercent = markup,
            linesJson = encodeLines(lines),
            createdAt = Instant.now().toEpochMilli()
        )
        _state.update { it.copy(estimate = entity, lines = lines, grandTotal = total) }
        viewModelScope.launch { estimateRepo.upsert(entity) }
    }

    private fun computeTotal(lines: List<EstimateLineUi>, markup: Double): Double =
        lines.sumOf { line ->
            val lineTotal = line.qty * line.unitPrice
            if (line.applyMarkup && markup > 0) lineTotal * (1 + markup / 100) else lineTotal
        }

    private fun parseLines(jsonStr: String): List<EstimateLineUi> {
        return try {
            val arr = json.parseToJsonElement(jsonStr).jsonArray
            arr.map { el ->
                val obj = el.jsonObject
                EstimateLineUi(
                    id = obj["id"]!!.jsonPrimitive.content,
                    description = obj["description"]!!.jsonPrimitive.content,
                    qty = obj["qty"]!!.jsonPrimitive.double,
                    unitLabel = obj["unit"]?.jsonPrimitive?.content ?: MeasurementUnit.M2.shortLabelUa(),
                    unitPrice = obj["unitPrice"]!!.jsonPrimitive.double,
                    applyMarkup = obj["applyMarkup"]?.jsonPrimitive?.boolean ?: true,
                    priceSource = obj["priceSource"]?.jsonPrimitive?.content ?: "Manual",
                    lineType = obj["lineType"]?.jsonPrimitive?.content ?: "Material"
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun encodeLines(lines: List<EstimateLineUi>): String {
        val arr = JsonArray(lines.map { l ->
            buildJsonObject {
                put("id", l.id); put("description", l.description); put("qty", l.qty)
                put("unit", l.unitLabel); put("unitPrice", l.unitPrice)
                put("applyMarkup", l.applyMarkup); put("priceSource", l.priceSource)
                put("lineType", l.lineType)
            }
        })
        return arr.toString()
    }
}
