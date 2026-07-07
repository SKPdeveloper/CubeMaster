package com.example.cubemaster.domain.repository

import com.cubemaster.core.model.*
import com.example.cubemaster.data.local.AppDatabase
import com.example.cubemaster.data.local.converter.EntityMapper
import com.example.cubemaster.data.remote.FirestoreRepository
import com.example.cubemaster.data.remote.StorageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val db: AppDatabase,
    private val firestore: FirestoreRepository,
    private val json: Json,
    private val attachmentRepo: AttachmentRepository
) {
    fun observeProjects(ownerId: String): Flow<List<Project>> =
        db.projectDao().observeAll(ownerId).map { list ->
            list.map { EntityMapper.mapToProjectDomain(it) }
        }

    fun observeProject(id: String): Flow<Project?> =
        db.projectDao().observeById(id).map { it?.let { EntityMapper.mapToProjectDomain(it) } }

    suspend fun createProject(ownerId: String, title: String, address: String?, documentedAreaM2: Double? = null): Project {
        val now = Instant.now()
        val project = Project(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            title = title,
            address = address,
            documentedAreaM2 = documentedAreaM2,
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
        attachmentRepo.deleteAllForProject(id)
    }
}

@Singleton
class RoomRepository @Inject constructor(
    private val db: AppDatabase,
    private val json: Json,
    private val attachmentRepo: AttachmentRepository
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

    // Оновлює лише позицію/поворот кімнати на плані об'єкта (перетягування на ObjectPlanScreen) —
    // не чіпає геометрію кімнати, тож не конфліктує з незбереженими правками на GeometryScreen.
    suspend fun updateRoomPlacement(roomId: String, originXM: Double, originYM: Double, rotationDeg: Double = 0.0) {
        val entity = db.roomDao().getById(roomId) ?: return
        db.roomDao().upsert(
            entity.copy(
                originXM = originXM,
                originYM = originYM,
                rotationDeg = rotationDeg,
                syncState = SyncState.PendingUpload.name
            )
        )
    }

    suspend fun deleteRoom(id: String) {
        db.roomDao().deleteById(id)
        attachmentRepo.deleteAllForRoom(id)
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
                    sillHeightMm = it.sillHeightMm,
                    offsetMm = it.offsetMm,
                    syncState = SyncState.valueOf(it.syncState)
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
                sillHeightMm = opening.sillHeightMm,
                offsetMm = opening.offsetMm,
                // Кожна локальна зміна прорізу позначається як така, що потребує вивантаження.
                syncState = SyncState.PendingUpload.name
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
    private val json: Json,
    private val attachmentRepo: AttachmentRepository
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
        attachmentRepo.deleteAllForParent(AttachmentParent.Surface, id)
    }
}

@Singleton
class DemolitionRepository @Inject constructor(
    private val db: AppDatabase,
    private val json: Json
) {

    fun observeTasks(roomId: String): Flow<List<DemolitionTask>> =
        db.demolitionTaskDao().observeByRoom(roomId).map { list ->
            list.map { e ->
                DemolitionTask(
                    id = e.id,
                    roomId = e.roomId,
                    kind = DemolitionKind.valueOf(e.kind),
                    params = parseParams(e.paramsJson),
                    cachedResult = e.cachedResultJson?.let { parseCachedResult(it) }
                )
            }
        }

    // Раніше params завжди повертався порожнім, хоча вхідні дані форми (довжина/висота/
    // матеріал тощо) реально зберігались у paramsJson — картки списку завдань демонтажу
    // не могли показати, що саме додав користувач.
    private fun parseParams(paramsJson: String): Map<String, Any> = try {
        json.parseToJsonElement(paramsJson).jsonObject.mapValues { (_, v) ->
            val prim = v.jsonPrimitive
            prim.doubleOrNull ?: prim.booleanOrNull ?: prim.content
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun parseCachedResult(resultJson: String): DemolitionResult {
        val obj = json.parseToJsonElement(resultJson).jsonObject
        val materialLines = obj["materialLines"]?.jsonArray?.map { el ->
            val o = el.jsonObject
            DemolitionMaterialLine(
                descriptionUa = o.getValue("descriptionUa").jsonPrimitive.content,
                qty = o.getValue("qty").jsonPrimitive.double,
                unit = MeasurementUnit.valueOf(o.getValue("unit").jsonPrimitive.content)
            )
        } ?: emptyList()
        return DemolitionResult(
            debrisVolumeM3 = obj.getValue("debrisVolumeM3").jsonPrimitive.double,
            debrisMassKg = obj.getValue("debrisMassKg").jsonPrimitive.double,
            laborHours = obj.getValue("laborHours").jsonPrimitive.double,
            materialLines = materialLines
        )
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

    // Остання відома ціна (ручна чи зовнішня) для кожного матеріалу каталогу — саме
    // те, що показує бейдж ціни на картці Каталогу одразу при відкритті екрана.
    fun observeLatestPrices(): Flow<List<com.example.cubemaster.data.local.entity.PriceEntryEntity>> =
        db.priceEntryDao().observeLatestPrices()

    suspend fun upsertPrice(entry: com.example.cubemaster.data.local.entity.PriceEntryEntity) {
        db.priceEntryDao().upsert(entry)
    }
}

@Singleton
class AttachmentRepository @Inject constructor(
    private val db: AppDatabase,
    private val storage: StorageRepository,
    private val firestore: FirestoreRepository
) {
    fun observeForParent(parentType: AttachmentParent, parentId: String): Flow<List<Attachment>> =
        db.attachmentDao().observeByParent(parentType.name, parentId).map { list ->
            list.map { EntityMapper.mapToAttachmentDomain(it) }
        }

    fun observeProjectDocuments(projectId: String): Flow<List<Attachment>> =
        db.attachmentDao().observeProjectLevel(projectId).map { list ->
            list.map { EntityMapper.mapToAttachmentDomain(it) }
        }

    suspend fun addPhoto(
        uid: String, projectId: String, roomId: String?,
        parentType: AttachmentParent, parentId: String, uri: android.net.Uri
    ): Attachment = addFile(uid, projectId, roomId, parentType, parentId, uri, "jpg", "image/jpeg", AttachmentKind.Photo)

    suspend fun addPdf(
        uid: String, projectId: String, roomId: String?,
        parentType: AttachmentParent, parentId: String, uri: android.net.Uri
    ): Attachment = addFile(uid, projectId, roomId, parentType, parentId, uri, "pdf", "application/pdf", AttachmentKind.Pdf)

    private suspend fun addFile(
        uid: String, projectId: String, roomId: String?,
        parentType: AttachmentParent, parentId: String, uri: android.net.Uri,
        extension: String, mimeType: String, kind: AttachmentKind
    ): Attachment {
        val fileUrl = storage.uploadAttachment(uid, projectId, parentType.name, parentId, uri, extension)
        val attachment = Attachment(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            roomId = roomId,
            parentType = parentType,
            parentId = parentId,
            kind = kind,
            fileUrl = fileUrl,
            textContent = null,
            mimeType = mimeType,
            createdAt = System.currentTimeMillis(),
            syncState = SyncState.PendingUpload
        )
        db.attachmentDao().upsert(EntityMapper.mapToAttachmentEntity(attachment))
        return attachment
    }

    suspend fun addNote(
        projectId: String, roomId: String?,
        parentType: AttachmentParent, parentId: String, text: String
    ): Attachment {
        val attachment = Attachment(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            roomId = roomId,
            parentType = parentType,
            parentId = parentId,
            kind = AttachmentKind.Note,
            fileUrl = null,
            textContent = text,
            mimeType = null,
            createdAt = System.currentTimeMillis(),
            syncState = SyncState.PendingUpload
        )
        db.attachmentDao().upsert(EntityMapper.mapToAttachmentEntity(attachment))
        return attachment
    }

    suspend fun delete(uid: String, attachment: Attachment) {
        db.attachmentDao().deleteById(attachment.id)
        attachment.fileUrl?.let { url ->
            try { storage.deleteFile(url) } catch (_: Exception) { }
        }
        if (attachment.syncState == SyncState.Synced) {
            try { firestore.deleteAttachment(uid, attachment.projectId, attachment.id) } catch (_: Exception) { }
        }
    }

    // Каскадне видалення при видаленні батьківської сутності — тільки локально
    // (узгоджено з рештою проєкту, де видалення в хмару взагалі не синхронізується).
    suspend fun deleteAllForParent(parentType: AttachmentParent, parentId: String) {
        db.attachmentDao().deleteByParent(parentType.name, parentId)
    }

    suspend fun deleteAllForRoom(roomId: String) {
        db.attachmentDao().deleteByRoom(roomId)
    }

    suspend fun deleteAllForProject(projectId: String) {
        db.attachmentDao().deleteByProject(projectId)
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
