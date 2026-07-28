package com.vidbridge.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "remote_entries",
    primaryKeys = ["sourceId", "path"],
    foreignKeys = [
        ForeignKey(
            entity = MediaSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId"), Index(value = ["sourceId", "scanToken"])],
)
data class RemoteEntryIndexEntity(
    val sourceId: String,
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long?,
    val modifiedAtEpochMs: Long?,
    val fingerprint: String,
    val scanToken: String,
)

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = MediaSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "scanToken"]),
        Index("title"),
    ],
)
data class MediaItemEntity(
    @PrimaryKey val mediaKey: String,
    val sourceId: String,
    val path: String,
    val title: String,
    val fileName: String,
    val kind: String,
    val season: Int?,
    val episode: Int?,
    val size: Long?,
    val modifiedAtEpochMs: Long?,
    val mimeType: String?,
    val scanToken: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "media_versions",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["mediaKey"],
            childColumns = ["mediaKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaKey"), Index("contentKey")],
)
data class MediaVersionEntity(
    @PrimaryKey val versionKey: String,
    val mediaKey: String,
    val contentKey: String,
    val label: String?,
    val width: Int?,
    val height: Int?,
    val videoCodec: String?,
)

@Entity(
    tableName = "metadata_records",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["mediaKey"],
            childColumns = ["mediaKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaKey")],
)
data class MetadataRecordEntity(
    @PrimaryKey val metadataKey: String,
    val mediaKey: String,
    val provider: String,
    val title: String?,
    val originalTitle: String?,
    val plot: String?,
    val year: Int?,
    val rating: Float?,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "artwork",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["mediaKey"],
            childColumns = ["mediaKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaKey")],
)
data class ArtworkEntity(
    @PrimaryKey val artworkKey: String,
    val mediaKey: String,
    val kind: String,
    val remotePath: String,
    val updatedAtEpochMs: Long,
)
@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = MediaSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId")],
)
data class FavoriteEntity(
    @PrimaryKey val mediaKey: String,
    val sourceId: String,
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "scan_jobs",
    foreignKeys = [
        ForeignKey(
            entity = MediaSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ScanJobEntity(
    @PrimaryKey val sourceId: String,
    val status: String,
    val scannedEntries: Int,
    val scannedMedia: Int,
    val startedAtEpochMs: Long?,
    val updatedAtEpochMs: Long,
    val errorMessage: String?,
    val scanToken: String?,
    val currentPath: String?,
    val nextOffset: Int?,
    val pendingPathsJson: String?,
)

data class PlaybackQueueRow(
    val sourceId: String,
    val path: String,
    val title: String,
    val mimeType: String?,
)
data class ContinueWatchingRow(
    val sourceId: String,
    val sourceName: String,
    val path: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
)
data class FavoriteItemRow(
    val mediaKey: String,
    val sourceId: String,
    val sourceName: String,
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val createdAtEpochMs: Long,
)
data class LibraryItemRow(
    val mediaKey: String,
    val sourceId: String,
    val sourceName: String,
    val path: String,
    val title: String,
    val fileName: String,
    val kind: String,
    val season: Int?,
    val episode: Int?,
    val size: Long?,
    val modifiedAtEpochMs: Long?,
    val mimeType: String?,
    val plot: String?,
    val year: Int?,
    val artworkPath: String?,
    val favorite: Boolean,
)

@Dao
interface MediaLibraryDao {
    @Query(
        """
        SELECT m.sourceId, m.path, COALESCE(n.title, m.title) AS title, m.mimeType
        FROM media_items m
        LEFT JOIN metadata_records n ON n.metadataKey = m.mediaKey || ':nfo'
        WHERE m.sourceId = :sourceId
        ORDER BY m.title COLLATE NOCASE, m.season, m.episode
        """
    )
    suspend fun getPlaybackQueue(sourceId: String): List<PlaybackQueueRow>
    @Query(
        """
        SELECT h.sourceId, s.displayName AS sourceName, h.path,
               COALESCE(n.title, m.title, h.path) AS title,
               h.positionMs, h.durationMs, h.updatedAtEpochMs
        FROM playback_history h
        INNER JOIN media_sources s ON s.id = h.sourceId
        LEFT JOIN media_items m ON m.sourceId = h.sourceId AND m.path = h.path
        LEFT JOIN metadata_records n ON n.metadataKey = m.mediaKey || ':nfo'
        WHERE h.completed = 0 AND h.positionMs >= 30000
        ORDER BY h.updatedAtEpochMs DESC
        LIMIT 30
        """
    )
    fun observeContinueWatching(): Flow<List<ContinueWatchingRow>>
    @Query(
        """
        SELECT m.mediaKey, m.sourceId, s.displayName AS sourceName, m.path,
               COALESCE(n.title, m.title) AS title, m.fileName,
               m.kind, m.season, m.episode, m.size, m.modifiedAtEpochMs, m.mimeType,
               n.plot, n.year, a.remotePath AS artworkPath,
               EXISTS(SELECT 1 FROM favorites f WHERE f.mediaKey = m.mediaKey) AS favorite
        FROM media_items m
        INNER JOIN media_sources s ON s.id = m.sourceId
        LEFT JOIN metadata_records n ON n.metadataKey = m.mediaKey || ':nfo'
        LEFT JOIN artwork a ON a.artworkKey = m.mediaKey || ':poster'
        WHERE (:query = '' OR COALESCE(n.title, m.title) LIKE '%' || :query || '%' OR m.fileName LIKE '%' || :query || '%')
        ORDER BY m.title COLLATE NOCASE, m.season, m.episode
        """
    )
    fun observeMedia(query: String = ""): Flow<List<LibraryItemRow>>

    @Query(
        """
        SELECT f.mediaKey, f.sourceId, s.displayName AS sourceName, f.path, f.name,
               f.isDirectory, f.createdAtEpochMs
        FROM favorites f
        INNER JOIN media_sources s ON s.id = f.sourceId
        ORDER BY f.createdAtEpochMs DESC
        """
    )
    fun observeFavorites(): Flow<List<FavoriteItemRow>>

    @Query("SELECT mediaKey FROM favorites WHERE sourceId = :sourceId")
    fun observeFavoriteKeys(sourceId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<RemoteEntryIndexEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersions(items: List<MediaVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(items: List<MetadataRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtwork(items: List<ArtworkEntity>)

    @Transaction
    suspend fun persistScanBatch(
        entries: List<RemoteEntryIndexEntity>,
        items: List<MediaItemEntity>,
        versions: List<MediaVersionEntity> = emptyList(),
        metadata: List<MetadataRecordEntity> = emptyList(),
        artwork: List<ArtworkEntity> = emptyList(),
    ) {
        if (entries.isNotEmpty()) upsertEntries(entries)
        if (items.isNotEmpty()) upsertMedia(items)
        if (versions.isNotEmpty()) upsertVersions(versions)
        if (metadata.isNotEmpty()) upsertMetadata(metadata)
        if (artwork.isNotEmpty()) upsertArtwork(artwork)
    }

    @Query("DELETE FROM remote_entries WHERE sourceId = :sourceId AND scanToken != :scanToken")
    suspend fun deleteStaleEntries(sourceId: String, scanToken: String)

    @Query("DELETE FROM media_items WHERE sourceId = :sourceId AND scanToken != :scanToken")
    suspend fun deleteStaleMedia(sourceId: String, scanToken: String)

    @Query("SELECT * FROM favorites WHERE mediaKey = :mediaKey")
    suspend fun getFavorite(mediaKey: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaKey = :mediaKey")
    suspend fun deleteFavorite(mediaKey: String)

    @Transaction
    suspend fun toggleFavorite(item: LibraryItemRow) {
        if (getFavorite(item.mediaKey) == null) {
            upsertFavorite(
                FavoriteEntity(
                    mediaKey = item.mediaKey,
                    sourceId = item.sourceId,
                    path = item.path,
                    name = item.fileName,
                    isDirectory = false,
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
        } else {
            deleteFavorite(item.mediaKey)
        }
    }
}

@Dao
interface ScanJobDao {
    @Query("SELECT * FROM scan_jobs ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<ScanJobEntity>>

    @Query("SELECT * FROM scan_jobs WHERE sourceId = :sourceId")
    fun observe(sourceId: String): Flow<ScanJobEntity?>

    @Query("SELECT * FROM scan_jobs WHERE sourceId = :sourceId")
    suspend fun get(sourceId: String): ScanJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: ScanJobEntity)
}
