package com.cubemaster.core.calculation

import com.cubemaster.core.model.*
import kotlin.math.ceil

private const val BULKING_FACTOR = 1.4

fun calculateWallRemoval(
    lengthM: Double,
    heightM: Double,
    thicknessMm: Double,
    material: WallMaterial,
    usePoweredTools: Boolean
): DemolitionResult {
    val volumeM3 = lengthM * heightM * (thicknessMm / 1000.0)
    val debrisMassKg = volumeM3 * material.densityKgM3 * BULKING_FACTOR
    val productivity = if (usePoweredTools)
        material.productivityPoweredM2PerHour
    else
        material.productivityManualM2PerHour
    val laborHours = if (productivity > 0) (lengthM * heightM) / productivity else 0.0

    val warnings = if (material == WallMaterial.ReinforcedConcrete) listOf(
        "УВАГА: Демонтаж залізобетонних конструкцій потребує перевірки несучої функції та проєкту перепланування. " +
        "Самовільний знос несучої стіни/плити перекриття є порушенням ДБН В.1.2-14:2018."
    ) else emptyList()

    return DemolitionResult(
        debrisVolumeM3 = volumeM3 * BULKING_FACTOR,
        debrisMassKg = debrisMassKg,
        laborHours = laborHours,
        materialLines = listOf(
            DemolitionMaterialLine("Будівельне сміття (${material.nameUa})", volumeM3 * BULKING_FACTOR, MeasurementUnit.M3)
        )
    )
}

fun calculateOpeningCut(
    widthM: Double,
    heightM: Double,
    wallMaterial: WallMaterial
): DemolitionResult {
    val cutLength = 2 * (widthM + heightM)
    val productivityMPerH = when (wallMaterial) {
        WallMaterial.ReinforcedConcrete -> 0.75 // м²/год алмазне різання
        else -> 0.45 // штроборіз
    }
    val laborHours = cutLength / productivityMPerH
    val reinforcementM = widthM + 0.4

    return DemolitionResult(
        debrisVolumeM3 = widthM * heightM * 0.2,
        debrisMassKg = widthM * heightM * 0.2 * wallMaterial.densityKgM3,
        laborHours = laborHours,
        materialLines = listOf(
            DemolitionMaterialLine("Підсилення прорізу (кутник/швелер, опційно)", reinforcementM, MeasurementUnit.M)
        )
    )
}

fun calculatePlasterRemoval(
    areaM2: Double,
    isGypsum: Boolean,
    isCeiling: Boolean = false
): DemolitionResult {
    val baseProductivity = if (isGypsum) 5.0 else 2.25 // м²/люд·год
    val productivity = if (isCeiling) baseProductivity * 0.8 else baseProductivity
    return DemolitionResult(
        debrisVolumeM3 = areaM2 * 0.025,
        debrisMassKg = areaM2 * 0.025 * (if (isGypsum) 900.0 else 1700.0),
        laborHours = areaM2 / productivity,
        materialLines = emptyList()
    )
}

fun calculateTileRemoval(areaM2: Double): DemolitionResult {
    val productivity = 3.0
    return DemolitionResult(
        debrisVolumeM3 = areaM2 * 0.012,
        debrisMassKg = areaM2 * 0.012 * 1800.0,
        laborHours = areaM2 / productivity,
        materialLines = emptyList()
    )
}

fun calculateScreedRemoval(areaM2: Double, thicknessMm: Double): DemolitionResult {
    val productivity = 1.0 // м²/люд·год
    return DemolitionResult(
        debrisVolumeM3 = areaM2 * (thicknessMm / 1000.0) * BULKING_FACTOR,
        debrisMassKg = areaM2 * (thicknessMm / 1000.0) * 1850.0,
        laborHours = areaM2 / productivity,
        materialLines = emptyList()
    )
}

fun calculateFlooringRemoval(areaM2: Double): DemolitionResult {
    val productivity = 11.5
    return DemolitionResult(
        debrisVolumeM3 = areaM2 * 0.015,
        debrisMassKg = areaM2 * 0.015 * 600.0,
        laborHours = areaM2 / productivity,
        materialLines = emptyList()
    )
}

fun calculatePaintRemoval(params: PaintRemovalParams): DemolitionResult {
    val productivityM2PerHour = when (params.paintType) {
        PaintType.WaterBased -> when (params.removalMethod) {
            PaintRemovalMethod.MechanicalGrinder -> 4.0
            else -> 3.0
        }
        PaintType.OilBased, PaintType.EnamelAlkyd -> when (params.removalMethod) {
            PaintRemovalMethod.MechanicalGrinder -> 0.85
            PaintRemovalMethod.HeatGun -> 2.0
            PaintRemovalMethod.ChemicalStripper -> 1.5
            PaintRemovalMethod.Combined -> 0.55
        }
        PaintType.Unknown -> 0.55 // найгірший сценарій
    }

    val warnings = buildList {
        if (params.paintType == PaintType.Unknown) {
            add("Тип фарби не визначено — розрахунок за найважчим сценарієм (олійна/алкідна). " +
                "Олійна/алкідна фарба глянцева, не змивається водою з милом.")
        }
        if (params.removalMethod == PaintRemovalMethod.HeatGun) {
            add("При нагріванні старої олійної/алкідної фарби виділяються токсичні пари. Обов'язкова вентиляція.")
        }
    }

    val chemWaitHours = if (params.removalMethod == PaintRemovalMethod.ChemicalStripper) {
        params.areaM2 / 10.0 * 0.5 // 20-40хв на кожні 10 м²
    } else 0.0

    return DemolitionResult(
        debrisVolumeM3 = params.areaM2 * 0.002 * params.layersCountEstimate,
        debrisMassKg = params.areaM2 * 0.002 * params.layersCountEstimate * 1200.0,
        laborHours = params.areaM2 / productivityM2PerHour + chemWaitHours,
        materialLines = emptyList()
    )
}

fun debrisContainersCount(totalDebrisM3: Double, containerSizeM3: Double = 8.0): Int =
    ceil(totalDebrisM3 / containerSizeM3).toInt()
