package com.example.cubemaster.data.local.converter

import androidx.room.TypeConverter
import com.cubemaster.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromJson(value: String?): String? = value

    // Конвертери потрібні для складних типів у Room.
    // Всі складні об'єкти зберігаємо як JSON-рядки в entity-полях,
    // а десеріалізацію виконуємо на рівні mapper-ів.
}

// Mapper-и між Entity і Domain моделями
object EntityMapper {

    fun mapToProjectDomain(e: com.example.cubemaster.data.local.entity.ProjectEntity) =
        Project(
            id = e.id,
            ownerId = e.ownerId,
            title = e.title,
            address = e.address,
            documentedAreaM2 = e.documentedAreaM2,
            createdAt = java.time.Instant.ofEpochMilli(e.createdAt),
            updatedAt = java.time.Instant.ofEpochMilli(e.updatedAt),
            syncState = SyncState.valueOf(e.syncState)
        )

    fun mapToProjectEntity(d: Project) =
        com.example.cubemaster.data.local.entity.ProjectEntity(
            id = d.id,
            ownerId = d.ownerId,
            title = d.title,
            address = d.address,
            documentedAreaM2 = d.documentedAreaM2,
            createdAt = d.createdAt.toEpochMilli(),
            updatedAt = d.updatedAt.toEpochMilli(),
            syncState = d.syncState.name
        )

    fun mapToRoomDomain(e: com.example.cubemaster.data.local.entity.RoomEntity, json: Json): Room {
        val geometry: RoomGeometry = if (e.geometryType == "rectangle") {
            RoomGeometry.Rectangle(e.widthMm ?: 0, e.lengthMm ?: 0)
        } else {
            val edges = e.edgesJson?.let { parseEdges(it, json) } ?: emptyList()
            RoomGeometry.Polygon(edges)
        }
        return Room(
            id = e.id,
            projectId = e.projectId,
            name = e.name,
            geometry = geometry,
            heightMode = HeightMode.valueOf(e.heightMode),
            heightMm = e.heightMm,
            cornerHeightsMm = e.cornerHeightsMmJson?.let { parseIntList(it, json) },
            roomType = RoomType.valueOf(e.roomType),
            sortOrder = e.sortOrder,
            originXM = e.originXM,
            originYM = e.originYM,
            rotationDeg = e.rotationDeg,
            syncState = SyncState.valueOf(e.syncState)
        )
    }

    fun mapToRoomEntity(d: Room, json: Json): com.example.cubemaster.data.local.entity.RoomEntity {
        val (geometryType, widthMm, lengthMm, edgesJson) = when (val g = d.geometry) {
            is RoomGeometry.Rectangle -> Quadruple("rectangle", g.widthMm, g.lengthMm, null)
            is RoomGeometry.Polygon -> Quadruple(
                "polygon", null, null,
                json.encodeToString(g.edges.map { mapOf("lengthMm" to it.lengthMm, "angleDeg" to it.interiorAngleDeg) })
            )
        }
        return com.example.cubemaster.data.local.entity.RoomEntity(
            id = d.id,
            projectId = d.projectId,
            name = d.name,
            geometryType = geometryType,
            widthMm = widthMm,
            lengthMm = lengthMm,
            edgesJson = edgesJson,
            heightMode = d.heightMode.name,
            heightMm = d.heightMm,
            cornerHeightsMmJson = d.cornerHeightsMm?.let { json.encodeToString(it) },
            roomType = d.roomType.name,
            sortOrder = d.sortOrder,
            originXM = d.originXM,
            originYM = d.originYM,
            rotationDeg = d.rotationDeg,
            syncState = d.syncState.name
        )
    }

    fun mapToSurfaceDomain(e: com.example.cubemaster.data.local.entity.SurfaceEntity, json: Json): Surface =
        Surface(
            id = e.id,
            roomId = e.roomId,
            kind = SurfaceKind.valueOf(e.kind),
            wallEdgeIndex = e.wallEdgeIndex,
            layers = parseLayers(e.layersJson, json)
        )

    fun mapToSurfaceEntity(d: Surface, json: Json) =
        com.example.cubemaster.data.local.entity.SurfaceEntity(
            id = d.id,
            roomId = d.roomId,
            kind = d.kind.name,
            wallEdgeIndex = d.wallEdgeIndex,
            layersJson = encodeLayers(d.layers, json)
        )

    fun mapToAttachmentDomain(e: com.example.cubemaster.data.local.entity.AttachmentEntity) =
        Attachment(
            id = e.id,
            projectId = e.projectId,
            roomId = e.roomId,
            parentType = AttachmentParent.valueOf(e.parentType),
            parentId = e.parentId,
            kind = AttachmentKind.valueOf(e.kind),
            fileUrl = e.fileUrl,
            textContent = e.textContent,
            mimeType = e.mimeType,
            createdAt = e.createdAt,
            syncState = SyncState.valueOf(e.syncState)
        )

    fun mapToAttachmentEntity(d: Attachment) =
        com.example.cubemaster.data.local.entity.AttachmentEntity(
            id = d.id,
            projectId = d.projectId,
            roomId = d.roomId,
            parentType = d.parentType.name,
            parentId = d.parentId,
            kind = d.kind.name,
            fileUrl = d.fileUrl,
            textContent = d.textContent,
            mimeType = d.mimeType,
            createdAt = d.createdAt,
            syncState = d.syncState.name
        )

    private fun parseEdges(jsonStr: String, json: Json): List<Edge> {
        return try {
            val arr = json.parseToJsonElement(jsonStr).jsonArray
            arr.map { el ->
                val obj = el.jsonObject
                Edge(
                    obj["lengthMm"]!!.jsonPrimitive.int,
                    obj["angleDeg"]!!.jsonPrimitive.double
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseIntList(jsonStr: String, json: Json): List<Int> =
        try { json.parseToJsonElement(jsonStr).jsonArray.map { it.jsonPrimitive.int } }
        catch (_: Exception) { emptyList() }

    private fun parseLayers(jsonStr: String, json: Json): List<Layer> {
        return try {
            val arr = json.parseToJsonElement(jsonStr).jsonArray
            arr.map { el ->
                val obj = el.jsonObject
                Layer(
                    id = obj["id"]!!.jsonPrimitive.content,
                    position = obj["position"]!!.jsonPrimitive.int,
                    layerType = LayerType.valueOf(obj["layerType"]!!.jsonPrimitive.content),
                    thicknessMm = obj["thicknessMm"]?.jsonPrimitive?.doubleOrNull,
                    materialSku = obj["materialSku"]?.jsonPrimitive?.contentOrNull
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun encodeLayers(layers: List<Layer>, json: Json): String {
        val arr = JsonArray(layers.map { l ->
            buildJsonObject {
                put("id", l.id)
                put("position", l.position)
                put("layerType", l.layerType.name)
                l.thicknessMm?.let { put("thicknessMm", it) }
                l.materialSku?.let { put("materialSku", it) }
            }
        })
        return arr.toString()
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
