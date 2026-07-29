package com.vidbridge.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vidbridge.VidBridgeApplication
import com.vidbridge.MainActivity
import com.vidbridge.core.database.DownloadStatus
import com.vidbridge.protocol.api.RemotePath
import com.vidbridge.protocol.api.SourceFailure
import com.vidbridge.protocol.api.safeUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DownloadWorker(
    appContext: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(DOWNLOAD_ID) ?: return Result.failure()
        val container = (applicationContext as VidBridgeApplication).container
        val dao = container.database.downloads()
        val download = dao.get(id) ?: return Result.failure()
        val finalFile = File(download.localPath)
        val partialFile = File(DownloadRepository.partialPath(download.localPath))
        if (!partialFile.exists() && finalFile.exists() && download.status != DownloadStatus.COMPLETED.name) {
            finalFile.renameTo(partialFile)
        }
        var downloaded = partialFile.takeIf(File::exists)?.length() ?: 0L
        setForeground(createForegroundInfo(id, download.title, downloaded, download.totalBytes))
        dao.updateProgress(id, downloaded, download.totalBytes, DownloadStatus.RUNNING.name, null)
        return try {
            container.fileSystems.create(download.sourceId).use { remote ->
                remote.connect()
                remote.open(RemotePath(download.path)).use { handle ->
                    val total = handle.size ?: download.totalBytes
                    if (total != null && downloaded > total) {
                        partialFile.delete()
                        downloaded = 0L
                    }
                    if (total != null && downloaded == total) {
                        if (!commitPartial(partialFile, finalFile)) {
                            dao.updateProgress(id, downloaded, total, DownloadStatus.FAILED.name, "无法保存完整下载文件")
                            return Result.failure()
                        }
                        dao.updateProgress(id, downloaded, total, DownloadStatus.COMPLETED.name, null)
                        return Result.success()
                    }
                    if (downloaded > 0L && !handle.seekable) {
                        partialFile.delete()
                        downloaded = 0L
                    }
                    partialFile.parentFile?.mkdirs()
                    FileOutputStream(partialFile, downloaded > 0L).use { output ->
                        var offset = downloaded
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val bytes = handle.readAt(offset, CHUNK_SIZE)
                            if (bytes.isEmpty()) break
                            withContext(Dispatchers.IO) { output.write(bytes) }
                            offset += bytes.size
                            downloaded = offset
                            dao.updateProgress(id, downloaded, total, DownloadStatus.RUNNING.name, null)
                            setForeground(createForegroundInfo(id, download.title, downloaded, total))
                        }
                    }
                    if (total == null || downloaded >= total) {
                        if (!commitPartial(partialFile, finalFile)) {
                            dao.updateProgress(id, downloaded, total, DownloadStatus.FAILED.name, "无法保存完整下载文件")
                            return Result.failure()
                        }
                        dao.updateProgress(id, downloaded, total, DownloadStatus.COMPLETED.name, null)
                        Result.success()
                    } else {
                        dao.updateProgress(id, downloaded, total, DownloadStatus.FAILED.name, "远端文件读取提前结束")
                        Result.failure()
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            dao.updateProgress(id, downloaded, download.totalBytes, DownloadStatus.PAUSED.name, null)
            throw cancelled
        } catch (error: Throwable) {
            val transient = error is SourceFailure.HostUnreachable || error is SourceFailure.Timeout
            dao.updateProgress(
                id,
                downloaded,
                download.totalBytes,
                if (transient) DownloadStatus.QUEUED.name else DownloadStatus.FAILED.name,
                error.safeUserMessage("下载失败"),
            )
            if (transient) Result.retry() else Result.failure()
        }
    }

    private fun createForegroundInfo(id: String, title: String, downloaded: Long, total: Long?): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "离线下载", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val percent = total?.takeIf { it > 0 }?.let { ((downloaded * 100L) / it).toInt().coerceIn(0, 100) }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId(id),
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseIntent = PendingIntent.getBroadcast(
            applicationContext,
            notificationId(id),
            Intent(applicationContext, DownloadNotificationReceiver::class.java)
                .setAction(DownloadNotificationReceiver.ACTION_PAUSE)
                .putExtra(DownloadNotificationReceiver.EXTRA_DOWNLOAD_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.vidbridge.R.mipmap.ic_launcher)
            .setContentTitle("下载：$title")
            .setContentText(if (percent == null) "正在准备" else "$percent% · ${downloaded / 1024 / 1024} MB")
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_pause, "暂停", pauseIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId(id), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId(id), notification)
        }
    }

    private fun notificationId(id: String): Int = NOTIFICATION_ID_BASE + (id.hashCode() and 0x0fff)

    private fun commitPartial(partial: File, target: File): Boolean {
        if (!partial.isFile) return target.isFile
        if (target.exists() && !target.delete()) return false
        return partial.renameTo(target) && target.isFile
    }

    companion object {
        const val CHUNK_SIZE = 1024 * 1024
        const val DOWNLOAD_ID = "download_id"
        private const val CHANNEL_ID = "offline_downloads"
        private const val NOTIFICATION_ID_BASE = 2000
    }
}
