package com.example.cubemaster.domain

import com.cubemaster.core.catalog.LayerPresets
import com.cubemaster.core.catalog.RoomTemplates
import com.cubemaster.core.model.Layer
import com.cubemaster.core.model.RoomType
import com.cubemaster.core.model.SurfaceKind
import java.util.UUID

fun presetLayersFor(roomType: RoomType, surfaceKind: SurfaceKind): List<Layer> {
    val presetId = RoomTemplates.presetIdFor(roomType, surfaceKind) ?: return emptyList()
    val preset = LayerPresets.byId(presetId) ?: return emptyList()
    return preset.layers.map { it.copy(id = UUID.randomUUID().toString()) }
}
