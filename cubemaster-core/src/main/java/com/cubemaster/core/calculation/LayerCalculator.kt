package com.cubemaster.core.calculation

import com.cubemaster.core.model.*
import kotlin.math.ceil
import kotlin.math.floor

data class LayerResult(
    val mixMassKg: Double,
    val bagsCount: Int,
    val volumeM3: Double = 0.0,
    val additionalLines: List<AdditionalMaterialLine> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class AdditionalMaterialLine(val descriptionUa: String, val qty: Double, val unit: MeasurementUnit)

private const val PACK_25_KG = 25.0
private const val PACK_50_KG = 50.0

fun calculateScreed(
    floorAreaM2: Double,
    thicknessMm: Double,
    norm: ConsumptionNorm
): LayerResult {
    val warnings = mutableListOf<String>()
    if (norm.minThicknessMm != null && thicknessMm < norm.minThicknessMm) {
        warnings.add("Товщина ${thicknessMm}мм менша за мінімум ${norm.minThicknessMm}мм (${norm.minThicknessJustification})")
    }
    val kgPerM2PerMm = norm.kgPerM2PerMm ?: 2.0
    val mixMassKg = floorAreaM2 * thicknessMm * kgPerM2PerMm
    val bagsCount = ceil(mixMassKg / PACK_25_KG).toInt()
    return LayerResult(mixMassKg, bagsCount, warnings = warnings)
}

fun calculateScreedCps(
    floorAreaM2: Double,
    thicknessMm: Double,
    ratio: Double = 3.0
): LayerResult {
    val warnings = mutableListOf<String>()
    if (thicknessMm < 30) {
        warnings.add("Мінімальна товщина ЦПС стяжки 30мм (по основі). Тонший шар тріскається при усадці.")
    }
    if (thicknessMm > 50) {
        warnings.add("При товщині понад 50мм — обов'язкове армування сіткою.")
    }
    val volumeM3 = floorAreaM2 * thicknessMm / 1000.0
    val result = splitCementSand(volumeM3, ratio)
    return LayerResult(
        mixMassKg = result.cementKg + result.sandKg,
        bagsCount = result.cementBags50kg,
        volumeM3 = volumeM3,
        additionalLines = listOf(
            AdditionalMaterialLine("Цемент М400 (мішки 50кг)", result.cementBags50kg.toDouble(), MeasurementUnit.Bag50),
            AdditionalMaterialLine("Пісок річковий", result.sandKg / 1600.0, MeasurementUnit.M3)
        ),
        warnings = warnings
    )
}

data class CementSandResult(
    val cementKg: Double,
    val sandKg: Double,
    val cementBags50kg: Int
)

fun splitCementSand(volumeM3: Double, ratio: Double = 3.0): CementSandResult {
    val cementVolume = volumeM3 * (1.0 / (1.0 + ratio))
    val sandVolume = volumeM3 * (ratio / (1.0 + ratio))
    return CementSandResult(
        cementKg = cementVolume * 1300.0,
        sandKg = sandVolume * 1600.0,
        cementBags50kg = ceil(cementVolume * 1300.0 / PACK_50_KG).toInt()
    )
}

fun calculatePlaster(
    surfaceAreaM2: Double,
    thicknessMm: Double,
    norm: ConsumptionNorm,
    concaveCorners: Int = 0
): LayerResult {
    val warnings = mutableListOf<String>()
    if (norm.minThicknessMm != null && thicknessMm < norm.minThicknessMm) {
        warnings.add("Мінімальна товщина ${norm.minThicknessMm}мм. ${norm.minThicknessJustification}")
    }
    val kgPerM2PerMm = norm.kgPerM2PerMm ?: 0.87
    var mixMassKg = surfaceAreaM2 * thicknessMm * kgPerM2PerMm
    // +3% на приміщення з ≥5 кутами (§4.2)
    if (concaveCorners >= 5) {
        mixMassKg *= 1.03
        warnings.add("Додано +3% витрати на кутові з'єднання (≥5 кутів у приміщенні)")
    }
    return LayerResult(
        mixMassKg = mixMassKg,
        bagsCount = ceil(mixMassKg / PACK_25_KG).toInt(),
        warnings = warnings
    )
}

fun calculatePrimer(
    surfaceAreaM2: Double,
    isPorous: Boolean = false
): LayerResult {
    val lPerM2 = if (isPorous) 0.175 else 0.125
    val totalL = surfaceAreaM2 * lPerM2
    return LayerResult(
        mixMassKg = totalL,
        bagsCount = 0,
        additionalLines = listOf(
            AdditionalMaterialLine("Ґрунтовка глибокого проникнення", totalL, MeasurementUnit.L)
        )
    )
}

fun calculateWaterproofing(
    floorAreaM2: Double,
    perimeter: Double,
    wallRaiseM: Double = 0.25
): LayerResult {
    val wallRaiseAreaM2 = perimeter * wallRaiseM
    val totalAreaM2 = floorAreaM2 + wallRaiseAreaM2
    // 2 шари, 1.5 кг/м² за 2 шари
    val kgPerM2 = 1.5
    val totalKg = totalAreaM2 * kgPerM2
    return LayerResult(
        mixMassKg = totalKg,
        bagsCount = ceil(totalKg / PACK_25_KG).toInt()
    )
}

fun calculateFlooring(
    floorAreaM2: Double,
    layerType: LayerType,
    isDiagonal: Boolean = false,
    isComplexGeometry: Boolean = false,
    patternRepeatCm: Int = 0
): LayerResult {
    return when (layerType) {
        LayerType.FlooringLaminate -> {
            var waste = if (isDiagonal) 0.10 else 0.07
            if (isComplexGeometry) waste += 0.03
            val totalM2 = floorAreaM2 * (1 + waste)
            val underlayM2 = floorAreaM2 * 1.05
            LayerResult(
                mixMassKg = 0.0,
                bagsCount = 0,
                additionalLines = listOf(
                    AdditionalMaterialLine("Ламінат", totalM2, MeasurementUnit.M2),
                    AdditionalMaterialLine("Підложка", underlayM2, MeasurementUnit.M2)
                )
            )
        }
        LayerType.FlooringParquet -> {
            var waste = if (isDiagonal) 0.12 else 0.08
            if (isComplexGeometry) waste += 0.03
            val totalM2 = floorAreaM2 * (1 + waste)
            val glueKg = floorAreaM2 * 1.15
            LayerResult(
                mixMassKg = glueKg,
                bagsCount = 0,
                additionalLines = listOf(
                    AdditionalMaterialLine("Паркет", totalM2, MeasurementUnit.M2),
                    AdditionalMaterialLine("Клей для паркету", glueKg, MeasurementUnit.Kg)
                )
            )
        }
        LayerType.FlooringTile -> {
            var waste = if (isDiagonal) 0.15 else 0.10
            if (isComplexGeometry) waste += 0.05
            val totalM2 = floorAreaM2 * (1 + waste)
            val adhesiveKg = floorAreaM2 * 5.0
            LayerResult(
                mixMassKg = adhesiveKg,
                bagsCount = ceil(adhesiveKg / PACK_25_KG).toInt(),
                additionalLines = listOf(
                    AdditionalMaterialLine("Плитка підлогова", totalM2, MeasurementUnit.M2),
                    AdditionalMaterialLine("Клей для плитки", adhesiveKg, MeasurementUnit.Kg)
                )
            )
        }
        LayerType.FlooringLinoleum -> {
            // розрахунок за шириною рулону 3м або 4м
            val rollWidth = 3.5
            val totalM2 = floorAreaM2 * 1.05
            val contactGlueKg = floorAreaM2 * 0.4
            LayerResult(
                mixMassKg = contactGlueKg,
                bagsCount = 0,
                additionalLines = listOf(
                    AdditionalMaterialLine("Лінолеум (рулон ${rollWidth}м)", ceil(totalM2 / rollWidth).toInt() * rollWidth, MeasurementUnit.M2),
                    AdditionalMaterialLine("Клей контактний", contactGlueKg, MeasurementUnit.Kg)
                )
            )
        }
        else -> LayerResult(0.0, 0)
    }
}

fun calculateTileGrout(
    areaM2: Double,
    tileSizeAMm: Int,
    tileSizeBMm: Int,
    jointWidthMm: Int,
    tileThicknessMm: Int
): Double {
    val a = tileSizeAMm.toDouble()
    val b = tileSizeBMm.toDouble()
    return (a + b) / (a * b) * jointWidthMm * tileThicknessMm * 1.6 * areaM2
}

fun calculateDrywall(
    surfaceAreaM2: Double,
    sheetWidthMm: Int = 1200,
    sheetHeightMm: Int = 2500,
    studSpacingMm: Int = 600
): LayerResult {
    val sheetAreaM2 = (sheetWidthMm / 1000.0) * (sheetHeightMm / 1000.0)
    val sheetsCount = ceil(surfaceAreaM2 / sheetAreaM2 * 1.10).toInt()
    return LayerResult(
        mixMassKg = 0.0,
        bagsCount = 0,
        additionalLines = listOf(
            AdditionalMaterialLine("ГКЛ-листи ${sheetWidthMm}×${sheetHeightMm}", sheetsCount.toDouble(), MeasurementUnit.Pcs)
        )
    )
}

fun calculatePaint(
    surfaceAreaM2: Double,
    lPerM2PerCoat: Double = 0.125,
    coats: Int = 2
): LayerResult {
    val totalL = surfaceAreaM2 * lPerM2PerCoat * coats
    return LayerResult(
        mixMassKg = 0.0,
        bagsCount = 0,
        additionalLines = listOf(
            AdditionalMaterialLine("Фарба водоемульсійна", totalL, MeasurementUnit.L)
        )
    )
}

fun calculateWallpaper(
    wallAreaM2: Double,
    rollWidthM: Double = 0.53,
    rollLengthM: Double = 10.5,
    roomHeightM: Double,
    patternRepeatCm: Int = 0
): LayerResult {
    val stripsPerRoll =
        floor(rollLengthM / roomHeightM).toInt()
    val actualStripLength = if (patternRepeatCm > 0) {
        ceil(roomHeightM / (patternRepeatCm / 100.0)) * (patternRepeatCm / 100.0)
    } else {
        roomHeightM
    }
    val stripsNeeded = ceil(wallAreaM2 / (rollWidthM * roomHeightM)).toInt()
    val stripsPerRollWithWaste = floor(rollLengthM / actualStripLength).toInt().coerceAtLeast(1)
    val rollsCount = ceil(stripsNeeded.toDouble() / stripsPerRollWithWaste).toInt()
    return LayerResult(
        mixMassKg = 0.0,
        bagsCount = 0,
        additionalLines = listOf(
            AdditionalMaterialLine("Шпалери (рулони)", rollsCount.toDouble(), MeasurementUnit.Roll)
        )
    )
}

fun requiresPrimer(fromLayerType: LayerType?, toLayerType: LayerType?): Boolean {
    if (toLayerType == null) return false
    return when (toLayerType) {
        LayerType.PlasterGypsumManual,
        LayerType.PlasterGypsumMachine,
        LayerType.PlasterCementSandManual,
        LayerType.PlasterCementSandMachine,
        LayerType.ScreedSelfLevelingCement,
        LayerType.ScreedSelfLevelingGypsum,
        LayerType.WallPutty,
        LayerType.WallTile,
        LayerType.FlooringTile -> true
        LayerType.FlooringLaminate,
        LayerType.FlooringParquet,
        LayerType.FlooringLinoleum -> fromLayerType?.name?.startsWith("Screed") == true
        else -> false
    }
}
