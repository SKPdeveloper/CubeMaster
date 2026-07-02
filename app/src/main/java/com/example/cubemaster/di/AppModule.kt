package com.example.cubemaster.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.cubemaster.data.local.AppDatabase
import com.example.cubemaster.data.local.dao.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideProjectDao(db: AppDatabase) = db.projectDao()
    @Provides fun provideRoomDao(db: AppDatabase) = db.roomDao()
    @Provides fun provideOpeningDao(db: AppDatabase) = db.openingDao()
    @Provides fun provideSurfaceDao(db: AppDatabase) = db.surfaceDao()
    @Provides fun provideDemolitionTaskDao(db: AppDatabase) = db.demolitionTaskDao()
    @Provides fun provideEstimateDao(db: AppDatabase) = db.estimateDao()
    @Provides fun provideMaterialCatalogDao(db: AppDatabase) = db.materialCatalogDao()
    @Provides fun providePriceEntryDao(db: AppDatabase) = db.priceEntryDao()
    @Provides fun provideCompanyProfileDao(db: AppDatabase) = db.companyProfileDao()
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val fs = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        fs.firestoreSettings = settings
        return fs
    }

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

    @Provides
    @Singleton
    fun provideRemoteConfig(): FirebaseRemoteConfig {
        val rc = FirebaseRemoteConfig.getInstance()
        val settings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        rc.setConfigSettingsAsync(settings)
        return rc
    }
}
