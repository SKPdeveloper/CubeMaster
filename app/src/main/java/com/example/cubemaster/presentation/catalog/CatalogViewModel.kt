package com.example.cubemaster.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.catalog.MaterialDefaults
import com.cubemaster.core.model.MaterialCatalogEntry
import com.example.cubemaster.data.local.entity.PriceEntryEntity
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.data.remote.FirestoreRepository
import com.example.cubemaster.domain.repository.MaterialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class CatalogUiState(
    val entries: List<MaterialCatalogEntry> = emptyList(),
    val prices: Map<String, Double> = emptyMap(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val refreshResult: String? = null
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val materialRepo: MaterialRepository,
    private val firestore: FirestoreRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        // Seed дефолтного каталогу при першому запуску
        viewModelScope.launch {
            materialRepo.seedDefaults()
            _state.update { it.copy(entries = MaterialDefaults.catalog, isLoading = false) }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val results = if (query.isBlank()) {
                MaterialDefaults.catalog
            } else {
                MaterialDefaults.catalog.filter { it.nameUa.contains(query, ignoreCase = true) }
            }
            _state.update { it.copy(entries = results) }
        }
    }

    fun setManualPrice(sku: String, price: Double) {
        viewModelScope.launch {
            val entry = PriceEntryEntity(
                id = UUID.randomUUID().toString(),
                materialSku = sku,
                vendor = "Ручне введення",
                unitPrice = price,
                currency = "UAH",
                source = "Manual",
                fetchedAt = Instant.now().toEpochMilli(),
                vendorUrl = null
            )
            materialRepo.upsertPrice(entry)
            _state.update { s ->
                s.copy(prices = s.prices + (sku to price))
            }

            // Зберегти як приватну ціну у Firestore
            auth.currentUserId?.let { uid ->
                firestore.uploadManualPrice(uid, com.cubemaster.core.model.PriceEntry(
                    id = entry.id,
                    materialSku = sku,
                    vendor = "Ручне введення",
                    unitPrice = price,
                    source = com.cubemaster.core.model.PriceSource.Manual,
                    fetchedAt = Instant.now(),
                    vendorUrl = null
                ))
            }
        }
    }

    fun refreshExternalPrices() {
        viewModelScope.launch {
            try {
                val skus = MaterialDefaults.catalog.map { it.sku }
                val (updated, skipped) = firestore.callRefreshPrices(skus)
                _state.update { it.copy(refreshResult = "Оновлено: $updated, пропущено: $skipped") }
            } catch (e: Exception) {
                _state.update { it.copy(refreshResult = "Помилка оновлення: ${e.message}") }
            }
        }
    }
}
