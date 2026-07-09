package com.cubemaster.core.model

import java.time.Instant

data class MaterialCatalogEntry(
    val sku: String,
    val nameUa: String,
    val category: MaterialCategory,
    val unit: MeasurementUnit,
    val packSize: Double?,
    val densityKgM3: Double?,
    val consumptionNorm: ConsumptionNorm
)

enum class MaterialCategory {
    Screed, Plaster, Primer, Waterproofing, Insulation,
    Flooring, WallFinish, Ceiling, Profile, Adhesive, Grout, Other
}

enum class MeasurementUnit { Kg, Bag25, Bag50, M2, M3, Pcs, L, M, Roll }

fun MeasurementUnit.shortLabelUa(): String = when (this) {
    MeasurementUnit.Kg -> "кг"
    MeasurementUnit.Bag25 -> "міш.25"
    MeasurementUnit.Bag50 -> "міш.50"
    MeasurementUnit.M2 -> "м²"
    MeasurementUnit.M3 -> "м³"
    MeasurementUnit.Pcs -> "шт."
    MeasurementUnit.L -> "л"
    MeasurementUnit.M -> "м"
    MeasurementUnit.Roll -> "рул."
}

data class ConsumptionNorm(
    val kgPerM2PerMm: Double?,
    val lPerM2: Double?,
    val minThicknessMm: Double?,
    val maxThicknessPerPassMm: Double?,
    val normativeReference: String,
    val minThicknessJustification: String
)

data class PriceEntry(
    val id: String,
    val materialSku: String,
    val vendor: String,
    val unitPrice: Double,
    val currency: String = "UAH",
    val source: PriceSource,
    val fetchedAt: Instant,
    val vendorUrl: String?
)

enum class PriceSource { Manual, Scraped, PartnerApi }
