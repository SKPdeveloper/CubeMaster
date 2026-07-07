package com.cubemaster.core.model

data class DemolitionTask(
    val id: String,
    val roomId: String,
    val kind: DemolitionKind,
    val params: Map<String, Any>,
    val cachedResult: DemolitionResult? = null
)

enum class DemolitionKind {
    WallRemoval,
    OpeningCut,
    PlasterRemoval,
    TileRemoval,
    ScreedRemoval,
    FlooringRemoval,
    CeilingRemoval,
    PaintRemoval
}

data class DemolitionResult(
    val debrisVolumeM3: Double,
    val debrisMassKg: Double,
    val laborHours: Double,
    val materialLines: List<DemolitionMaterialLine>,
    val warnings: List<String> = emptyList()
)

data class DemolitionMaterialLine(
    val descriptionUa: String,
    val qty: Double,
    val unit: MeasurementUnit
)

enum class WallMaterial(
    val nameUa: String,
    val densityKgM3: Double,
    val productivityManualM2PerHour: Double,
    val productivityPoweredM2PerHour: Double
) {
    Brick("Цегла", 1700.0, 0.20, 0.65),
    Aerated("Газобетон / піноблок", 600.0, 0.50, 1.25),
    Drywall("ГКЛ-перегородка", 80.0, 4.0, 4.0),
    ReinforcedConcrete("Залізобетон монолітний", 2450.0, 0.0, 0.225),
    Fbs("Блоки ФБС", 2000.0, 0.15, 0.40)
}

enum class PaintType { WaterBased, OilBased, EnamelAlkyd, Unknown }
enum class PaintSubstrate { Plaster, Concrete, Tile, Drywall }
enum class PaintRemovalMethod { MechanicalGrinder, HeatGun, ChemicalStripper, Combined }

data class PaintRemovalParams(
    val areaM2: Double,
    val paintType: PaintType,
    val substrate: PaintSubstrate,
    val removalMethod: PaintRemovalMethod,
    val layersCountEstimate: Int
)
