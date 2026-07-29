package com.vidbridge.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vidbridge.VidBridgeApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Low-frequency TMDB enrichment for already indexed movies and series. */
class MetadataRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as VidBridgeApplication).container
        val apiKey = container.settings.preferences.first().tmdbApiKey
        if (apiKey.isBlank()) return Result.success()
        val candidates = container.mediaLibrary.observeMedia()
            .first()
            .filter { it.kind != "VIDEO" }
            .groupBy { it.groupKey.ifBlank { it.mediaKey } }
            .values
            .mapNotNull { it.firstOrNull() }
        val items = buildList {
            for (item in candidates) {
                if (size >= MAX_ITEMS_PER_RUN) break
                if (container.tmdb.needsRefresh(item)) add(item)
            }
        }
        var failures = 0
        items.forEach { item ->
            runCatching { container.tmdb.enrich(item, apiKey) }
                .onFailure { failures++ }
        }
        return if (failures == 0) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "tmdb-metadata-refresh"
        private const val MAX_ITEMS_PER_RUN = 60

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MetadataRefreshWorker>(
                12,
                TimeUnit.HOURS,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
