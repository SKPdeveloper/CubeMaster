package com.example.cubemaster.data.local.dao

import androidx.room.*
import com.example.cubemaster.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE ownerId = :ownerId ORDER BY updatedAt DESC")
    fun observeAll(ownerId: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Upsert
    suspend fun upsertAll(projects: List<ProjectEntity>)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM projects WHERE syncState != 'Synced'")
    suspend fun getPending(): List<ProjectEntity>

    @Query("UPDATE projects SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String)
}
