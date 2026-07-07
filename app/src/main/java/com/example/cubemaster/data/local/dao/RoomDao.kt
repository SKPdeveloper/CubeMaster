package com.example.cubemaster.data.local.dao

import androidx.room.*
import com.example.cubemaster.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {

    @Query("SELECT * FROM rooms WHERE projectId = :projectId ORDER BY sortOrder ASC")
    fun observeByProject(projectId: String): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id")
    fun observeById(id: String): Flow<RoomEntity?>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun getById(id: String): RoomEntity?

    @Upsert
    suspend fun upsert(room: RoomEntity)

    @Upsert
    suspend fun upsertAll(rooms: List<RoomEntity>)

    @Query("DELETE FROM rooms WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM rooms WHERE syncState != 'Synced'")
    suspend fun getPending(): List<RoomEntity>

    @Query("UPDATE rooms SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String)
}

@Dao
interface OpeningDao {

    @Query("SELECT * FROM openings WHERE roomId = :roomId")
    fun observeByRoom(roomId: String): Flow<List<OpeningEntity>>

    @Upsert
    suspend fun upsert(opening: OpeningEntity)

    @Upsert
    suspend fun upsertAll(openings: List<OpeningEntity>)

    @Query("DELETE FROM openings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM openings WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)

    @Query("SELECT * FROM openings WHERE syncState != 'Synced'")
    suspend fun getPending(): List<OpeningEntity>

    @Query("UPDATE openings SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String)
}

@Dao
interface SurfaceDao {

    @Query("SELECT * FROM surfaces WHERE roomId = :roomId")
    fun observeByRoom(roomId: String): Flow<List<SurfaceEntity>>

    @Query("SELECT * FROM surfaces WHERE id = :id")
    suspend fun getById(id: String): SurfaceEntity?

    @Upsert
    suspend fun upsert(surface: SurfaceEntity)

    @Upsert
    suspend fun upsertAll(surfaces: List<SurfaceEntity>)

    @Query("DELETE FROM surfaces WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM surfaces WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)
}

@Dao
interface DemolitionTaskDao {

    @Query("SELECT * FROM demolition_tasks WHERE roomId = :roomId")
    fun observeByRoom(roomId: String): Flow<List<DemolitionTaskEntity>>

    @Upsert
    suspend fun upsert(task: DemolitionTaskEntity)

    @Query("DELETE FROM demolition_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE demolition_tasks SET cachedResultJson = :json WHERE id = :id")
    suspend fun updateCachedResult(id: String, json: String?)
}

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE parentType = :parentType AND parentId = :parentId ORDER BY createdAt ASC")
    fun observeByParent(parentType: String, parentId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE projectId = :projectId AND parentType = 'Project' ORDER BY createdAt ASC")
    fun observeProjectLevel(projectId: String): Flow<List<AttachmentEntity>>

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: String): AttachmentEntity?

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM attachments WHERE parentType = :parentType AND parentId = :parentId")
    suspend fun deleteByParent(parentType: String, parentId: String)

    @Query("DELETE FROM attachments WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)

    @Query("DELETE FROM attachments WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)

    @Query("SELECT * FROM attachments WHERE syncState != 'Synced'")
    suspend fun getPending(): List<AttachmentEntity>

    @Query("UPDATE attachments SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String)
}

@Dao
interface EstimateDao {

    @Query("SELECT * FROM estimates WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeByProject(projectId: String): Flow<List<EstimateEntity>>

    @Query("SELECT * FROM estimates WHERE id = :id")
    suspend fun getById(id: String): EstimateEntity?

    @Upsert
    suspend fun upsert(estimate: EstimateEntity)

    @Query("DELETE FROM estimates WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM estimates WHERE syncState != 'Synced'")
    suspend fun getPending(): List<EstimateEntity>
}

@Dao
interface MaterialCatalogDao {

    @Query("SELECT * FROM material_catalog ORDER BY nameUa ASC")
    fun observeAll(): Flow<List<com.example.cubemaster.data.local.entity.MaterialCatalogEntity>>

    @Query("SELECT * FROM material_catalog WHERE sku = :sku")
    suspend fun getBySku(sku: String): com.example.cubemaster.data.local.entity.MaterialCatalogEntity?

    @Query("SELECT * FROM material_catalog WHERE category = :category ORDER BY nameUa ASC")
    fun observeByCategory(category: String): Flow<List<com.example.cubemaster.data.local.entity.MaterialCatalogEntity>>

    @Query("SELECT * FROM material_catalog WHERE nameUa LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<com.example.cubemaster.data.local.entity.MaterialCatalogEntity>

    @Upsert
    suspend fun upsertAll(entries: List<com.example.cubemaster.data.local.entity.MaterialCatalogEntity>)
}

@Dao
interface PriceEntryDao {

    @Query("SELECT * FROM price_entries WHERE materialSku = :sku ORDER BY fetchedAt DESC")
    fun observeBySku(sku: String): Flow<List<PriceEntryEntity>>

    @Query("SELECT * FROM price_entries WHERE materialSku = :sku ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun getLatestBySku(sku: String): PriceEntryEntity?

    @Upsert
    suspend fun upsert(entry: PriceEntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<PriceEntryEntity>)

    @Query("DELETE FROM price_entries WHERE materialSku = :sku AND source != 'Manual'")
    suspend fun deleteScrapedBySku(sku: String)
}

@Dao
interface CompanyProfileDao {

    @Query("SELECT * FROM company_profile WHERE id = 1")
    fun observe(): Flow<CompanyProfileEntity?>

    @Query("SELECT * FROM company_profile WHERE id = 1")
    suspend fun get(): CompanyProfileEntity?

    @Upsert
    suspend fun upsert(profile: CompanyProfileEntity)
}
