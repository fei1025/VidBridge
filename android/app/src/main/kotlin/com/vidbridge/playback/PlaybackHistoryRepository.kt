package com.vidbridge.playback

import com.vidbridge.core.database.PlaybackHistoryDao
import com.vidbridge.core.database.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class PlaybackHistoryRepository(private val dao: PlaybackHistoryDao) {
    suspend fun get(sourceId: String, path: String): PlaybackHistoryEntity? = dao.get(key(sourceId, path))

    suspend fun save(
        sourceId: String,
        path: String,
        positionMs: Long,
        durationMs: Long,
        completionThreshold: Float = 0.9f,
    ) {
        if (durationMs <= 0) return
        dao.upsert(
            PlaybackHistoryEntity(
                mediaKey = key(sourceId, path),
                sourceId = sourceId,
                path = path,
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = durationMs,
                completed = isCompleted(positionMs, durationMs, completionThreshold),
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    fun observeRecent(): Flow<List<PlaybackHistoryEntity>> = dao.observeRecent()

    companion object {
        fun isCompleted(positionMs: Long, durationMs: Long, threshold: Float = 0.9f): Boolean =
            durationMs > 0 && positionMs.toFloat() / durationMs.toFloat() >= threshold.coerceIn(0.5f, 1f)

        fun resumePosition(history: PlaybackHistoryEntity?): Long =
            history?.takeIf { !it.completed && it.positionMs >= 30_000 }?.positionMs ?: 0

        fun key(sourceId: String, path: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$sourceId\u0000$path".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
