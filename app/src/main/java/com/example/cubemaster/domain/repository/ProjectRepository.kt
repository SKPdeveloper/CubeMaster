package com.example.cubemaster.domain.repository

import com.cubemaster.core.model.*
import com.example.cubemaster.data.local.AppDatabase
import com.example.cubemaster.data.local.converter.EntityMapper
import com.example.cubemaster.data.remote.FirestoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val db: AppDatabase,
    private val firestore: FirestoreRepository,
    private val json: Json
) {
    fun observeProjects(ownerId: String): Flow<List<Project>> =
        db.projectDao().observeAll(ownerId).map { list ->
            list.map { EntityMapper.mapToProjectDomain(it) }
        }

    fun observeProject(id: String): Flow<Project?> =
        db.projectDao().observeById(id).map { it?.let { EntityMapper.mapToProjectDomain(it) } }

    suspend fun createProject(ownerId: String, title: String, address: String?): Project {
        val now = Instant.now()
        val project = Project(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            title = title,
            address = address,
            createdAt = now,
            updatedAt = now
        )
        db.projectDao().upsert(EntityMapper.mapToProjectEntity(project))
        return project
    }

    suspend fun updateProject(project: Project) {
        val updated = project.copy(updatedAt = Instant.now(), syncState = SyncState.PendingUpload)
        db.projectDao().upsert(EntityMapper.mapToProjectEntity(updated))
    }

    suspend fun deleteProject(id: String) {
        db.projectDao().deleteById(id)
    }
}

@Singleton
class RoomRepository @Inject constructor(
    private val db: AppDatabase,
    private val json: Json
) {
    fun observeRooms(projectId: String): Flow<List<Room>> =
        db.roomDao().observeByProject(projectId).map { list ->
            list.map { EntityMapper.mapToRoomDomain(it, json) }
        }

    fun observeRoom(id: String): Flow<Room?> =
        db.roomDao().observeById(id).map { it?.let { EntityMapper.mapToRoomDomain(it, json) } }

    suspend fun createRoom(
        projectId: String,
        name: String,
        geometry: RoomGeometry,
        heightMode: HeightMode,
        heightMm: Int?,
        cornerHeightsMm: List<Int>?,
        roomType: RoomType,
        sortOrder: Int
    ): Room {
        val room = Room(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            name = name,
            geometry = geometry,
            heightMode = heightMode,
            heightMm = heightMm,
            cornerHeightsMm = cornerHeightsMm,
            roomType = roomType,
            sortOrder = sortOrder
        )
        db.roomDao().upsert(EntityMapper.mapToRoomEntity(room, json))
        return room
    }

    suspend fun updateRoom(room: Room) {
        db.roomDao().upsert(EntityMapper.mapToRoomEntity(room.copy(syncState = SyncState.PendingUpload), json))
    }

    suspend fun deleteRoom(id: String) {
        db.roomDao().deleteById(id)
    }

    fun observeOpenings(roomId: String): Flow<List<Opening>> =
        db.openingDao().observeByRoom(roomId).map { list ->
            list.map {
                Opening(
                    id = it.id,
                    roomId = it.roomId,
                    wallEdgeIndex = it.wallEdgeIndex,
                    kind = OpeningKind.valueOf(it.kind),
                    widthMm = it.widthMm,
                    heightMm = it.heightMm,
                    sillHeightMm = it.sillHeightMm
                )
            }
        }

    suspend fun upsertOpening(opening: Opening) {
        db.openingDao().upsert(
            com.example.cubemaster.data.local.entity.OpeningEntity(
                id = opening.id,
                roomId = opening.roomId,
                wallEdgeIndex = opening.wallEdgeIndex,
                kind = opening.kind.name,
                widthMm = opening.widthMm,
                heightMm = opening.heightMm,
                sillHeightMm = opening.sillHeightMm
            )
        )
    }

    suspend fun deleteOpening(id: String) {
        db.openingDao().deleteById(id)
    }
}

@Singleton
class SurfaceRepository @Inject constructor(
    private val db: AppDatabase,
    private val json: Json
) {
    fun observeSurfaces(roomId: String): Flow<List<Surface>> =
        db.surfaceDao().observeByRoom(roomId).map { list ->
            list.map { EntityMapper.mapToSurfaceDomain(it, json) }
        }

    suspend fun upsertSurface(surface: Surface) {
        db.surfaceDao().upsert(EntityMapper.mapToSurfaceEntity(surface, json))
    }

    suspend fun deleteSurface(id: String) {
        db.surfaceDao().deleteById(id)
    }
}

@Singleton
class DemolitionRepository @Inject constructor(private val db: AppDatabase) {

    fun observeTasks(roomId: String): Flow<List<DemolitionTask>> =
        db.demolitionTaskDao().observeByRoom(roomId).map { list ->
            list.map { e ->
                DemolitionTask(
                    id = e.id,
                    roomId = e.roomId,
                    kind = DemolitionKind.valueOf(e.kind),
                    params = emptyMap()
                )
            }
        }

    suspend fun upsertTask(task: com.example.cubemaster.data.local.entity.DemolitionTaskEntity) {
        db.demolitionTaskDao().upsert(task)
    }

    suspend fun deleteTask(id: String) {
        db.demolitionTaskDao().deleteById(id)
    }
}

@Singleton
class EstimateRepository @Inject constructor(
    private val db: AppDatabase,
    private val firestore: FirestoreRepository
) {
    fun observeEstimates(projectId: String): Flow<List<com.example.cubemaster.data.local.entity.EstimateEntity>> =
        db.estimateDao().observeByProject(projectId)

    suspend fun upsert(estimate: com.example.cubemaster.data.local.entity.EstimateEntity) {
        db.estimateDao().upsert(estimate)
    }

    suspend fun delete(id: String) {
        db.estimateDao().deleteById(id)
    }

    suspend fun generatePdf(uid: String, projectId: String, estimateId: String): String =
        firestore.callGeneratePdf(uid, projectId, estimateId)

    suspend fun generateXlsx(uid: String, projectId: String, estimateId: String): String =
        firestore.callGenerateXlsx(uid, projectId, estimateId)
}

@Singleton
class MaterialRepository @Inject constructor(
    private val db: AppDatabase,
    private val json: Json
) {
    fun observeAll(): Flow<List<com.example.cubemaster.data.local.entity.MaterialCatalogEntity>> =
        db.materialCatalogDao().observeAll()

    suspend fun search(query: String) = db.materialCatalogDao().search(query)

    suspend fun getBySku(sku: String) = db.materialCatalogDao().getBySku(sku)

    suspend fun seedDefaults() {
        val entries = com.cubemaster.core.catalog.MaterialDefaults.catalog.map { d ->
            com.example.cubemaster.data.local.entity.MaterialCatalogEntity(
                sku = d.sku,
                nameUa = d.nameUa,
                category = d.category.name,
                unit = d.unit.name,
                packSize = d.packSize,
                densityKgM3 = d.densityKgM3,
                consumptionNormJson = encodeNorm(d.consumptionNorm, json)
            )
        }
        db.materialCatalogDao().upsertAll(entries)
    }

    fun observePrices(sku: String): Flow<List<com.example.cubemaster.data.local.entity.PriceEntryEntity>> =
        db.priceEntryDao().observeBySku(sku)

    suspend fun upsertPrice(entry: com.example.cubemaster.data.local.entity.PriceEntryEntity) {
        db.priceEntryDao().upsert(entry)
    }
}

private fun encodeNorm(norm: com.cubemaster.core.model.ConsumptionNorm, json: Json): String {
    val obj = kotlinx.serialization.json.buildJsonObject {
        norm.kgPerM2PerMm?.let { put("kgPerM2PerMm", kotlinx.serialization.json.JsonPrimitive(it)) }
        norm.lPerM2?.let { put("lPerM2", kotlinx.serialization.json.JsonPrimitive(it)) }
        norm.minThicknessMm?.let { put("minThicknessMm", kotlinx.serialization.json.JsonPrimitive(it)) }
        put("normativeReference", kotlinx.serialization.json.JsonPrimitive(norm.normativeReference))
        put("minThicknessJustification", kotlinx.serialization.json.JsonPrimitive(norm.minThicknessJustification))
    }
    return obj.toString()
}
