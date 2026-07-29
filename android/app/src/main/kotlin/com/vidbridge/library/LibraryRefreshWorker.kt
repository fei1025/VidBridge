package com.vidbridge.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vidbridge.VidBridgeApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Periodically refreshes enabled media sources using the normal resumable scanner. */
class LibraryRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as VidBridgeApplication).container
        container.sources.observeAll()
            .first()
            .filter { it.enabled }
            .forEach { container.mediaLibrary.scanIfIdle(it.id) }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "enabled-source-library-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LibraryRefreshWorker>(
                6,
                TimeUnit.HOURS,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
