package com.example.cubemaster

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.example.cubemaster.data.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CubeMasterApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            SyncWorker.buildRequest()
        )
    }
}
