package com.vidbridge.library

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vidbridge.core.database.DownloadDao
import com.vidbridge.core.database.DownloadEntity
import com.vidbridge.core.database.DownloadStatus
import com.vidbridge.protocol.api.MediaSourceType
import com.vidbridge.sources.SourceRepository
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Owns persistent offline files; it is intentionally separate from the playback buffer. */
class DownloadRepository(
    context: Context,
    private val dao: DownloadDao,
    private val sources: SourceRepository,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val root = File(appContext.filesDir, DOWNLOAD_DIRECTORY).apply { mkdirs() }

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    /** Repairs database state after process death, force-stop, or a canceled WorkManager run. */
    suspend fun reconcileOnStartup() = withContext(Dispatchers.IO) {
        dao.getAll().forEach { download ->
            val workInfos = runCatching {
                workManager.getWorkInfosForUniqueWork(workName(download.id)).get()
            }.getOrDefault(emptyList())
            val active = workInfos.any { info ->
                info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.BLOCKED
            }
            if (active) return@forEach

            val finalFile = File(download.localPath)
            val completeOnDisk = finalFile.isFile &&
                (download.totalBytes == null || finalFile.length() == download.totalBytes)
            when {
                download.status == DownloadStatus.COMPLETED.name && !completeOnDisk ->
                    dao.updateProgress(
                        download.id,
                        File(partialPath(download.localPath)).takeIf(File::isFile)?.length() ?: 0L,
                        download.totalBytes,
                        DownloadStatus.FAILED.name,
                        "本地下载文件已丢失，可重新下载",
                    )
                download.status == DownloadStatus.RUNNING.name && completeOnDisk ->
                    dao.updateProgress(
                        download.id,
                        download.totalBytes ?: finalFile.length(),
                        download.totalBytes ?: finalFile.length(),
                        DownloadStatus.COMPLETED.name,
                        null,
                    )
                download.status == DownloadStatus.RUNNING.name ->
                    dao.updateProgress(
                        download.id,
                        File(partialPath(download.localPath)).takeIf(File::isFile)?.length() ?: download.downloadedBytes,
                        download.totalBytes,
                        DownloadStatus.PAUSED.name,
                        null,
                    )
                download.status == DownloadStatus.QUEUED.name ->
                    workManager.enqueueUniqueWork(
                        workName(download.id),
                        ExistingWorkPolicy.REPLACE,
                        request(download.id, requiresNetwork(download.sourceId)),
                    )
            }
        }
    }

    suspend fun enqueue(sourceId: String, path: String, title: String, knownTotalBytes: Long? = null): DownloadEntity {
        val existing = dao.getBySourcePath(sourceId, path)
        if (existing != null) {
            val completedOnDisk = existing.status == DownloadStatus.COMPLETED.name &&
                File(existing.localPath).isFile &&
                (existing.totalBytes == null || File(existing.localPath).length() == existing.totalBytes)
            if (completedOnDisk) return existing
            val totalBytes = existing.totalBytes ?: knownTotalBytes
            workManager.enqueueUniqueWork(workName(existing.id), ExistingWorkPolicy.REPLACE, request(existing.id, requiresNetwork(existing.sourceId)))
            dao.updateProgress(existing.id, existing.downloadedBytes, totalBytes, DownloadStatus.QUEUED.name, null)
            return existing.copy(status = DownloadStatus.QUEUED.name, totalBytes = totalBytes, errorMessage = null)
        }
        val id = UUID.randomUUID().toString()
        val localPath = File(root, "$id-${safeFileName(path.substringAfterLast('/'))}").absolutePath
        val now = System.currentTimeMillis()
        val download = DownloadEntity(
            id = id,
            sourceId = sourceId,
            path = path,
            title = title.ifBlank { path.substringAfterLast('/') },
            localPath = localPath,
            totalBytes = knownTotalBytes?.takeIf { it >= 0L },
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED.name,
            errorMessage = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        dao.upsert(download)
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request(id, requiresNetwork(sourceId)))
        return download
    }

    suspend fun pause(id: String) {
        workManager.cancelUniqueWork(workName(id))
        dao.get(id)?.let { dao.updateProgress(id, it.downloadedBytes, it.totalBytes, DownloadStatus.PAUSED.name, null) }
    }

    suspend fun retry(id: String) {
        dao.get(id)?.let {
            dao.updateProgress(id, it.downloadedBytes, it.totalBytes, DownloadStatus.QUEUED.name, null)
            workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request(id, requiresNetwork(it.sourceId)))
        }
    }

    suspend fun delete(id: String) {
        workManager.cancelUniqueWork(workName(id))
        dao.get(id)?.let {
            File(it.localPath).delete()
            File(partialPath(it.localPath)).delete()
        }
        dao.delete(id)
    }

    suspend fun deleteForSource(sourceId: String) {
        dao.getForSource(sourceId).forEach { download -> delete(download.id) }
    }

    private suspend fun requiresNetwork(sourceId: String): Boolean = sources.get(sourceId)?.type != MediaSourceType.LOCAL

    private fun request(id: String, requiresNetwork: Boolean) = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(workDataOf(DownloadWorker.DOWNLOAD_ID to id))
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                .build(),
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .addTag(workName(id))
        .build()

    private fun workName(id: String) = "download-$id"

    companion object {
        fun partialPath(localPath: String): String = "$localPath.part"
        const val DOWNLOAD_DIRECTORY = "downloads"

        fun safeFileName(value: String): String = value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "video" }
            .take(160)
    }
}
