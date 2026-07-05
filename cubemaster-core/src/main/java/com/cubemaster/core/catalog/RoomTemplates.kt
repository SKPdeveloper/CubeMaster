package com.cubemaster.core.catalog

import com.cubemaster.core.model.RoomType
import com.cubemaster.core.model.SurfaceKind

object RoomTemplates {

    // Рекомендовані пресети шарів за типом приміщення (id з LayerPresets.all).
    // Свідомо покриті лише типи з однозначним типовим набором опоряджання —
    // для решти типів автопідстановка не виконується (краще порожньо, ніж хибно).
    val recommendedPresetIds: Map<RoomType, Map<SurfaceKind, String>> = mapOf(
        RoomType.Living to mapOf(
            SurfaceKind.Floor to "floor_laminate_on_screed",
            SurfaceKind.Wall to "wall_new_build_paint",
            SurfaceKind.Ceiling to "ceiling_paint"
        ),
        RoomType.Bathroom to mapOf(
            SurfaceKind.Floor to "floor_tile_bathroom_underfloor",
            SurfaceKind.Wall to "wall_tile_bathroom",
            SurfaceKind.Ceiling to "ceiling_paint"
        )
    )

    fun presetIdFor(roomType: RoomType, surfaceKind: SurfaceKind): String? =
        recommendedPresetIds[roomType]?.get(surfaceKind)
}
