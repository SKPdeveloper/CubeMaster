package com.example.cubemaster.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 5,
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

        // v3 -> v4: план об'єкта (позиція/поворот кімнати) + позиція отвору вздовж стіни й
        // синхронізація прорізів. Справжня міграція — щоб оновлення застосунку не стирало
        // локальні дані вже встановлених користувачів (fallbackToDestructiveMigration це робив би).
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rooms ADD COLUMN originXM REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE rooms ADD COLUMN originYM REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE rooms ADD COLUMN rotationDeg REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE openings ADD COLUMN offsetMm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE openings ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PendingUpload'")
            }
        }
    }
}
