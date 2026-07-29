package com.vidbridge.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vidbridge.VidBridgeApplication
import com.vidbridge.core.database.ArtworkEntity
import com.vidbridge.core.database.MediaItemEntity
import com.vidbridge.core.database.MediaVersionEntity
import com.vidbridge.core.database.MetadataRecordEntity
import com.vidbridge.core.database.RemoteEntryIndexEntity
import com.vidbridge.core.database.ScanJobEntity
import com.vidbridge.protocol.api.PageRequest
import com.vidbridge.protocol.api.RemoteEntry
import com.vidbridge.protocol.api.RemoteFileSystem
import com.vidbridge.protocol.api.RemotePath
import com.vidbridge.protocol.api.SourceFailure
import com.vidbridge.protocol.api.safeUserMessage
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray

class MediaScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sourceId = inputData.getString(SOURCE_ID) ?: return Result.failure()
        val requestedToken = inputData.getString(SCAN_TOKEN) ?: return Result.failure()
        val container = (applicationContext as VidBridgeApplication).container
        val source = container.sources.get(sourceId) ?: return Result.failure()
        val jobs = container.database.scanJobs()
        val library = container.database.mediaLibrary()
        val resumeJob = jobs.get(sourceId)?.takeIf {
            it.scanToken == requestedToken && it.status == STATUS_RETRYING
        }
        val token = requestedToken
        val startedAt = resumeJob?.startedAtEpochMs ?: System.currentTimeMillis()
        var entryCount = resumeJob?.scannedEntries ?: 0
        var mediaCount = resumeJob?.scannedMedia ?: 0
        var currentPath = resumeJob?.currentPath
        var nextOffset = resumeJob?.nextOffset
        val directories = decodePaths(resumeJob?.pendingPathsJson)
        if (resumeJob == null) directories.add(RemotePath(source.rootPath.trim('/')))

        suspend fun saveStatus(status: String, error: String? = null) {
            jobs.upsert(
                ScanJobEntity(
                    sourceId = sourceId,
                    status = status,
                    scannedEntries = entryCount,
                    scannedMedia = mediaCount,
                    startedAtEpochMs = startedAt,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    errorMessage = error,
                    scanToken = token,
                    currentPath = currentPath,
                    nextOffset = nextOffset,
                    pendingPathsJson = encodePaths(directories),
                ),
            )
        }

        saveStatus(STATUS_RUNNING)
        val fileSystem = try {
            container.fileSystems.create(sourceId)
        } catch (error: Throwable) {
            val safeMessage = error.safeUserMessage("无法创建来源连接")
            saveStatus(STATUS_FAILED, safeMessage)
            return Result.failure(workDataOf(ERROR_MESSAGE to safeMessage))
        }

        try {
            fileSystem.connect()
            val showHidden = container.settings.preferences.first().showHiddenFiles
            val existingEntries = library.getEntries(sourceId).associateBy { it.path }
            val existingMedia = library.getMediaItems(sourceId).associateBy { it.path }
            var resumedPath = currentPath
            val nfoCache = mutableMapOf<String, LocalMetadata?>()

            while (resumedPath != null || directories.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val directory = resumedPath?.let(::RemotePath) ?: directories.removeFirst()
                var pageRequest: PageRequest? = PageRequest(
                    offset = if (resumedPath != null) nextOffset ?: 0 else 0,
                    limit = PAGE_SIZE,
                )
                resumedPath = null
                while (pageRequest != null) {
                    currentCoroutineContext().ensureActive()
                    currentPath = directory.value
                    nextOffset = pageRequest.offset
                    saveStatus(STATUS_RUNNING)
                    val page = fileSystem.list(directory, pageRequest)
                    val visible = page.items.filter { showHidden || !ScanRules.isHidden(it.name) }
                    val now = System.currentTimeMillis()
                    val indexed = visible.map { entry ->
                        val current = entry.toIndex(sourceId, token)
                        existingEntries[entry.path.value]
                            ?.takeIf { it.fingerprint == current.fingerprint }
                            ?.copy(scanToken = token)
                            ?: current
                    }
                    val videoEntries = visible.filter { !it.isDirectory && MediaFormats.isVideo(it.name) }
                    val changedVideoPaths = videoEntries.asSequence()
                        .filter { entry ->
                            val current = entry.toIndex(sourceId, token)
                            existingEntries[entry.path.value]?.fingerprint != current.fingerprint || existingMedia[entry.path.value] == null
                        }
                        .map { it.path.value }
                        .toSet()
                    val media = videoEntries.map { entry ->
                        val old = existingMedia[entry.path.value]
                        if (entry.path.value !in changedVideoPaths && old != null) old.copy(scanToken = token) else entry.toMedia(sourceId, token, now)
                    }
                    val versions = media.filter { it.path in changedVideoPaths }.map {
                        val parsed = MediaFileNameParser.parse(it.fileName)
                        MediaVersionEntity(
                            versionKey = it.mediaKey,
                            mediaKey = it.mediaKey,
                            contentKey = MediaVersionRules.contentKey(parsed),
                            label = MediaVersionRules.qualityLabel(it.fileName),
                            width = null,
                            height = null,
                            videoCodec = null,
                        )
                    }
                    val nfoEntries = visible.filter { !it.isDirectory && it.name.endsWith(".nfo", ignoreCase = true) }
                    val metadata = media.mapNotNull { item ->
                        val nfo = matchingNfo(item.fileName, nfoEntries) ?: return@mapNotNull null
                        val local = if (nfoCache.containsKey(nfo.path.value)) {
                            nfoCache[nfo.path.value]
                        } else {
                            fileSystem.readNfo(nfo).also { nfoCache[nfo.path.value] = it }
                        } ?: return@mapNotNull null
                        MetadataRecordEntity(
                            metadataKey = "${item.mediaKey}:nfo",
                            mediaKey = item.mediaKey,
                            provider = "NFO",
                            title = local.title,
                            originalTitle = local.originalTitle,
                            plot = local.plot,
                            year = local.year,
                            rating = local.rating,
                            director = local.director,
                            castMembers = local.castMembers.joinToString("\n").ifBlank { null },
                            updatedAtEpochMs = now,
                        )
                    }
                    val images = visible.filter { !it.isDirectory && isArtwork(it.name) }
                    val artwork = media.flatMap { item ->
                        buildList {
                            matchingArtwork(item.fileName, images)?.let { image ->
                                add(ArtworkEntity(
                                    artworkKey = "${item.mediaKey}:poster",
                                    mediaKey = item.mediaKey,
                                    kind = "POSTER",
                                    remotePath = image.path.value,
                                    updatedAtEpochMs = now,
                                ))
                            }
                            matchingBackdrop(item.fileName, images)?.let { image ->
                                add(ArtworkEntity(
                                    artworkKey = "${item.mediaKey}:backdrop",
                                    mediaKey = item.mediaKey,
                                    kind = "BACKDROP",
                                    remotePath = image.path.value,
                                    updatedAtEpochMs = now,
                                ))
                            }
                        }
                    }
                    library.persistScanBatch(indexed, media, versions, metadata, artwork)
                    visible.filter { it.isDirectory && !ScanRules.isExcludedDirectory(it.name) }
                        .forEach { directories.add(it.path) }
                    entryCount += visible.size
                    mediaCount += media.size
                    pageRequest = page.next
                    nextOffset = pageRequest?.offset
                    if (pageRequest == null) currentPath = null
                    setProgress(workDataOf(PROGRESS_ENTRIES to entryCount, PROGRESS_MEDIA to mediaCount))
                    saveStatus(STATUS_RUNNING)
                }
            }

            library.deleteStaleEntries(sourceId, token)
            library.deleteStaleMedia(sourceId, token)
            currentPath = null
            nextOffset = null
            saveStatus(STATUS_SUCCESS)
            return Result.success(workDataOf(PROGRESS_ENTRIES to entryCount, PROGRESS_MEDIA to mediaCount))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { saveStatus(STATUS_CANCELED) }
            throw cancelled
        } catch (error: Throwable) {
            val retryable = error is SourceFailure.Timeout || error is SourceFailure.HostUnreachable
            val willRetry = retryable && runAttemptCount < MAX_RETRIES
            saveStatus(if (willRetry) STATUS_RETRYING else STATUS_FAILED, error.safeUserMessage("扫描失败"))
            return if (willRetry) {
                Result.retry()
            } else {
                Result.failure(workDataOf(ERROR_MESSAGE to error.safeUserMessage("扫描失败")))
            }
        } finally {
            fileSystem.close()
        }
    }

    companion object {
        const val SOURCE_ID = "source_id"
        const val SCAN_TOKEN = "scan_token"
        const val ERROR_MESSAGE = "error_message"
        const val PROGRESS_ENTRIES = "entries"
        const val PROGRESS_MEDIA = "media"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_RETRYING = "RETRYING"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELED = "CANCELED"
        private const val PAGE_SIZE = 250
        private const val MAX_RETRIES = 3
    }
}

object ScanRules {
    private val excludedDirectories = setOf("@eadir", "#recycle", "@recycle", ".snapshot")

    fun isHidden(name: String): Boolean = name.startsWith('.') || name.lowercase() in excludedDirectories

    fun isExcludedDirectory(name: String): Boolean = name.lowercase() in excludedDirectories
}

internal fun encodePaths(paths: Collection<RemotePath>): String = JSONArray(paths.map { it.value }).toString()

internal fun decodePaths(value: String?): ArrayDeque<RemotePath> {
    val result = ArrayDeque<RemotePath>()
    if (value.isNullOrBlank()) return result
    val json = JSONArray(value)
    for (index in 0 until json.length()) result.add(RemotePath(json.getString(index)))
    return result
}

private suspend fun RemoteFileSystem.readNfo(entry: RemoteEntry): LocalMetadata? {
    val handle = open(entry.path)
    return try {
        val length = minOf(entry.size ?: MAX_NFO_BYTES.toLong(), MAX_NFO_BYTES.toLong()).toInt()
        NfoParser.parse(handle.readAt(0, length))
    } finally {
        handle.close()
    }
}

private fun matchingNfo(videoName: String, entries: List<RemoteEntry>): RemoteEntry? {
    val stem = videoName.substringBeforeLast('.').lowercase()
    return entries.firstOrNull { it.name.substringBeforeLast('.').lowercase() == stem }
        ?: entries.firstOrNull { it.name.equals("movie.nfo", true) || it.name.equals("tvshow.nfo", true) }
}

private fun isArtwork(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")

private fun matchingArtwork(videoName: String, entries: List<RemoteEntry>): RemoteEntry? {
    val stem = videoName.substringBeforeLast('.').lowercase()
    return entries.firstOrNull {
        val imageStem = it.name.substringBeforeLast('.').lowercase()
        imageStem == stem || imageStem == "$stem-poster" || imageStem in setOf("poster", "folder")
    }
}

private fun matchingBackdrop(videoName: String, entries: List<RemoteEntry>): RemoteEntry? {
    val stem = videoName.substringBeforeLast('.').lowercase()
    return entries.firstOrNull {
        val imageStem = it.name.substringBeforeLast('.').lowercase()
        imageStem == "$stem-backdrop" || imageStem == "$stem-fanart" ||
            imageStem in setOf("backdrop", "fanart", "background")
    }
}

private const val MAX_NFO_BYTES = 512 * 1024
private fun RemoteEntry.toIndex(sourceId: String, scanToken: String) = RemoteEntryIndexEntity(
    sourceId = sourceId,
    path = path.value,
    name = name,
    isDirectory = isDirectory,
    size = size,
    modifiedAtEpochMs = modifiedAt?.toEpochMilli(),
    fingerprint = MediaIdentity.fingerprint(path.value, size, modifiedAt?.toEpochMilli()),
    scanToken = scanToken,
)

private fun RemoteEntry.toMedia(sourceId: String, scanToken: String, createdAt: Long): MediaItemEntity {
    val modified = modifiedAt?.toEpochMilli()
    val parsed = MediaFileNameParser.parse(name)
    val groupTitle = parsed.title.substringBefore(" · ").ifBlank { parsed.title }
    return MediaItemEntity(
        mediaKey = MediaIdentity.mediaKey(sourceId, path.value, size, modified),
        sourceId = sourceId,
        path = path.value,
        title = parsed.title,
        fileName = name,
        kind = parsed.kind.name,
        groupKey = MediaIdentity.groupKey(sourceId, groupTitle, parsed.kind),
        groupTitle = groupTitle,
        season = parsed.season,
        episode = parsed.episode,
        size = size,
        modifiedAtEpochMs = modified,
        mimeType = MediaFormats.mimeType(name),
        scanToken = scanToken,
        createdAtEpochMs = createdAt,
    )
}
