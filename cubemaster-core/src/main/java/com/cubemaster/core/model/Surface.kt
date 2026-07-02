package com.cubemaster.core.model

data class Surface(
    val id: String,
    val roomId: String,
    val kind: SurfaceKind,
    val wallEdgeIndex: Int?,
    val layers: List<Layer>
)

enum class SurfaceKind { Floor, Ceiling, Wall }

data class Layer(
    val id: String,
    val position: Int,
    val layerType: LayerType,
    val thicknessMm: Double?,
    val materialSku: String?,
    val params: Map<String, Any> = emptyMap()
)

enum class LayerType {
    // Стяжки підлоги
    ScreedCpsManual,
    ScreedCp5Bags,
    ScreedSelfLevelingCement,
    ScreedSelfLevelingGypsum,
    ScreedDryPrefab,

    // Штукатурка
    PlasterGypsumManual,
    PlasterGypsumMachine,
    PlasterCementSandManual,
    PlasterCementSandMachine,
    PlasterDecorativeKoroid,
    PlasterVenetian,

    // Ґрунтовка
    PrimerDeep,
    PrimerContact,

    // Гідроізоляція
    WaterproofingCoating,

    // Утеплення
    InsulationLayer,

    // Фінішні покриття підлоги
    FlooringLaminate,
    FlooringParquet,
    FlooringLinoleum,
    FlooringTile,
    FlooringSelfLevelingFinish,

    // Покриття стін
    WallPutty,
    WallPaint,
    WallWallpaper,
    WallDrywall,
    WallTile,

    // Стеля
    CeilingStretch,
    CeilingDrywall,
    CeilingPaint,
}

data class LayerPreset(
    val id: String,
    val nameUa: String,
    val surfaceKind: SurfaceKind,
    val layers: List<Layer>
)
