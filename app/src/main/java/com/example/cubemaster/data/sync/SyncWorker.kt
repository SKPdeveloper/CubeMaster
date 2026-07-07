package com.example.cubemaster.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.cubemaster.data.local.AppDatabase
import com.example.cubemaster.data.local.converter.EntityMapper
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.data.remote.FirestoreRepository
import com.cubemaster.core.model.SyncState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: AppDatabase,
    private val firestore: FirestoreRepository,
    private val auth: AuthRepository,
    private val json: Json
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val uid = auth.currentUserId ?: return Result.success()

        return try {
            // Синхронізація проєктів
            val pendingProjects = db.projectDao().getPending()
            for (entity in pendingProjects) {
                val domain = EntityMapper.mapToProjectDomain(entity)
                firestore.uploadProject(uid, domain)
                db.projectDao().updateSyncState(entity.id, SyncState.Synced.name)
            }

            // Синхронізація кімнат
            val pendingRooms = db.roomDao().getPending()
            for (entity in pendingRooms) {
                val projectId = entity.projectId
                firestore.uploadRoom(uid, projectId, entity)
                db.roomDao().updateSyncState(entity.id, SyncState.Synced.name)
            }

            // Синхронізація прорізів (двері/вікна/вентиляція/ніші)
            val pendingOpenings = db.openingDao().getPending()
            for (entity in pendingOpenings) {
                val room = db.roomDao().getById(entity.roomId)
                if (room != null) {
                    firestore.uploadOpening(uid, room.projectId, entity.roomId, entity)
                    db.openingDao().updateSyncState(entity.id, SyncState.Synced.name)
                }
            }

            // Синхронізація кошторисів
            val pendingEstimates = db.estimateDao().getPending()
            for (entity in pendingEstimates) {
                firestore.uploadEstimate(uid, entity)
                // Позначаємо синхронізованими (оновлюємо entity)
                db.estimateDao().upsert(entity.copy(syncState = SyncState.Synced.name))
            }

            // Синхронізація вкладень
            val pendingAttachments = db.attachmentDao().getPending()
            for (entity in pendingAttachments) {
                firestore.uploadAttachment(uid, entity)
                db.attachmentDao().updateSyncState(entity.id, SyncState.Synced.name)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Синхронізація не вдалась (спроба $runAttemptCount)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "cubemaster_sync"
        const val ONE_TIME_WORK_NAME = "cubemaster_sync_once"

        fun buildRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

        fun buildOneTimeRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
    }
}
