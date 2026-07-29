package com.vidbridge.library

import android.content.Context
import androidx.work.*
import com.vidbridge.core.database.ContinueWatchingRow
import com.vidbridge.core.database.FavoriteEntity
import com.vidbridge.core.database.FavoriteItemRow
import com.vidbridge.core.database.LibraryItemRow
import com.vidbridge.core.database.MediaVersionRow
import com.vidbridge.core.database.PlaybackQueueRow
import com.vidbridge.protocol.api.RemoteEntry
import com.vidbridge.core.database.MediaLibraryDao
import com.vidbridge.core.database.ScanJobDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MediaLibraryRepository(
    context: Context,
    private val library: MediaLibraryDao,
    private val scanJobs: ScanJobDao,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun observeMedia(query: String = ""): Flow<List<LibraryItemRow>> = library.observeMedia(query)
    fun observeEpisodes(groupKey: String): Flow<List<LibraryItemRow>> = library.observeEpisodes(groupKey)
    fun observeContinueWatching(): Flow<List<ContinueWatchingRow>> = library.observeContinueWatching()
    fun observeRecentHistory(): Flow<List<ContinueWatchingRow>> = library.observeRecentHistory()
    fun observeFavorites(): Flow<List<FavoriteItemRow>> = library.observeFavorites()
    fun observeFavoriteKeys(sourceId: String): Flow<List<String>> = library.observeFavoriteKeys(sourceId)
    fun observeScanJobs() = scanJobs.observeAll()
    suspend fun getPlaybackQueue(sourceId: String, currentPath: String): List<PlaybackQueueRow> =
        library.getPlaybackQueue(sourceId, currentPath)
    suspend fun getVersions(mediaKey: String): List<MediaVersionRow> {
        val contentKey = library.getContentKey(mediaKey) ?: return emptyList()
        return library.getVersions(contentKey)
    }

    suspend fun toggleFavorite(item: LibraryItemRow) = library.toggleFavorite(item)

    suspend fun toggleFavorite(sourceId: String, entry: RemoteEntry) {
        val key = MediaIdentity.entryKey(
            sourceId,
            entry.path.value,
            entry.isDirectory,
            entry.size,
            entry.modifiedAt?.toEpochMilli(),
        )
        if (library.getFavorite(key) == null) {
            library.upsertFavorite(
                FavoriteEntity(
                    mediaKey = key,
                    sourceId = sourceId,
                    path = entry.path.value,
                    name = entry.name,
                    isDirectory = entry.isDirectory,
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
        } else {
            library.deleteFavorite(key)
        }
    }

    fun scan(sourceId: String) {
        val request = OneTimeWorkRequestBuilder<MediaScanWorker>()
            .setInputData(
                workDataOf(
                    MediaScanWorker.SOURCE_ID to sourceId,
                    MediaScanWorker.SCAN_TOKEN to java.util.UUID.randomUUID().toString(),
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(scanTag(sourceId))
            .build()
        workManager.enqueueUniqueWork(workName(sourceId), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun scanIfIdle(sourceId: String) = withContext(Dispatchers.IO) {
        val active = workManager.getWorkInfosForUniqueWork(workName(sourceId)).get()
            .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        if (!active) scan(sourceId)
    }

    fun cancelScan(sourceId: String) = workManager.cancelUniqueWork(workName(sourceId))

    private fun workName(sourceId: String) = "media-scan-$sourceId"
    private fun scanTag(sourceId: String) = "media-scan-source-$sourceId"
}
