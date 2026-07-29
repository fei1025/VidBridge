package com.vidbridge.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELED }

@Entity(
    tableName = "downloads",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = MediaSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [androidx.room.Index(value = ["sourceId", "path"], unique = true)],
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val path: String,
    val title: String,
    val localPath: String,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val status: String,
    val errorMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE sourceId = :sourceId AND path = :path LIMIT 1")
    suspend fun getBySourcePath(sourceId: String, path: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE sourceId = :sourceId")
    suspend fun getForSource(sourceId: String): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, status = :status, errorMessage = :errorMessage, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long?,
        status: String,
        errorMessage: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)
}
