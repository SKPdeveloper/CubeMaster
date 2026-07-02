package com.cubemaster.core.catalog

import com.cubemaster.core.model.*
import java.util.UUID

object LayerPresets {

    val all: List<LayerPreset> = listOf(

        LayerPreset(
            id = "floor_laminate_on_screed",
            nameUa = "Підлога під ламінат (по стяжці)",
            surfaceKind = SurfaceKind.Floor,
            layers = listOf(
                Layer(UUID.randomUUID().toString(), 1, LayerType.ScreedCpsManual, 50.0, "SCREED_CPS_MANUAL"),
                Layer(UUID.randomUUID().toString(), 2, LayerType.ScreedSelfLevelingCement, 5.0, "SCREED_SLC"),
                Layer(UUID.randomUUID().toString(), 3, LayerType.PrimerDeep, null, "PRIMER_DEEP"),
                Layer(UUID.randomUUID().toString(), 4, LayerType.FlooringLaminate, null, null)
            )
        ),

        LayerPreset(
            id = "floor_tile_bathroom_underfloor",
            nameUa = "Підлога санвузла під плитку (тепла підлога)",
            surfaceKind = SurfaceKind.Floor,
            layers = listOf(
                Layer(UUID.randomUUID().toString(), 1, LayerType.WaterproofingCoating, null, "WATERPROOF_COATING"),
                Layer(UUID.randomUUID().toString(), 2, LayerType.ScreedCpsManual, 60.0, "SCREED_CPS_MANUAL",
                    mapOf("hasUnderfloorHeating" to true, "minAbovePipeMm" to 30)),
                Layer(UUID.randomUUID().toString(), 3, LayerType.PrimerDeep, null, "PRIMER_DEEP"),
                Layer(UUID.randomUUID().toString(), 4, LayerType.FlooringTile, null, "TILE_ADHESIVE")
            )
        ),

        LayerPreset(
            id = "wall_new_build_paint",
            nameUa = "Стіни новобудови під фарбу",
            surfaceKind = SurfaceKind.Wall,
            layers = listOf(
                Layer(UUID.randomUUID().toString(), 1, LayerType.PlasterGypsumManual, 15.0, "PLASTER_GYPSUM_MANUAL"),
                Layer(UUID.randomUUID().toString(), 2, LayerType.PrimerDeep, null, "PRIMER_DEEP"),
                Layer(UUID.randomUUID().toString(), 3, LayerType.WallPutty, 2.0, "WALL_PUTTY_FINISH"),
                Layer(UUID.randomUUID().toString(), 4, LayerType.CeilingPaint, null, null)
            )
        ),

        LayerPreset(
            id = "wall_old_building_wallpaper",
            nameUa = "Стіни старого фонду під шпалери",
            surfaceKind = SurfaceKind.Wall,
            layers = listOf(
                Layer(UUID.randomUUID().toString(), 1, LayerType.PlasterGypsumManual, 20.0, "PLASTER_GYPSUM_MANUAL"),
                Layer(UUID.randomUUID().toString(), 2, LayerType.PrimerDeep, null, "PRIMER_DEEP"),
                Layer(UUID.randomUUID().toString(), 3, LayerType.WallPutty, 1.5, "WALL_PUTTY_FINISH"),
                Layer(UUID.randomUUID().toString(), 4, LayerType.WallWallpaper, null, null)
            )
        ),

        LayerPreset(
            id = "ceiling_paint",
            nameUa = "Стеля під фарбу",
            surfaceKind = SurfaceKind.Ceiling,
            layers = listOf(
                Layer(UUID.randomUUID().toString(), 1, LayerType.PrimerDeep, null, "PRIMER_DEEP"),
                Layer(UUID.randomUUID().toString(), 2, LayerType.WallPutty, 1.5, "WALL_PUTTY_FINISH"),
                Layer(UUID.randomUUID().toString(), 3, LayerType.CeilingPaint, null, null)
            )
        ),

        LayerPreset(
            id = "ceiling_stretch",
            nameUa = "Натяжна стеля",
            surfaceKind = SurfaceKind.Ceiling,
            layers = listOf(
                Layer(UUID.randomUUID().toString(), 1, LayerType.CeilingStretch, null, null)
            )
        )
    )
}
