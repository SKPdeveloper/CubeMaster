package com.example.cubemaster.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.cubemaster.data.local.dao.*
import com.example.cubemaster.data.local.entity.*

@Database(
    entities = [
        ProjectEntity::class,
        RoomEntity::class,
        OpeningEntity::class,
        SurfaceEntity::class,
        DemolitionTaskEntity::class,
        EstimateEntity::class,
        MaterialCatalogEntity::class,
        PriceEntryEntity::class,
        CompanyProfileEntity::class,
        AttachmentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun roomDao(): RoomDao
    abstract fun openingDao(): OpeningDao
    abstract fun surfaceDao(): SurfaceDao
    abstract fun demolitionTaskDao(): DemolitionTaskDao
    abstract fun estimateDao(): EstimateDao
    abstract fun materialCatalogDao(): MaterialCatalogDao
    abstract fun priceEntryDao(): PriceEntryDao
    abstract fun companyProfileDao(): CompanyProfileDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        const val DATABASE_NAME = "cubemaster.db"
    }
}
