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
        Index("groupKey"),
    ],
)
data class MediaItemEntity(
    @PrimaryKey val mediaKey: String,
    val sourceId: String,
    val path: String,
    val title: String,
    val fileName: String,
    val kind: String,
    @ColumnInfo(defaultValue = "") val groupKey: String,
    @ColumnInfo(defaultValue = "") val groupTitle: String,
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
    val director: String? = null,
    val castMembers: String? = null,
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
    val artworkPath: String?,
)
data class FavoriteItemRow(
    val mediaKey: String,
    val sourceId: String,
    val sourceName: String,
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val createdAtEpochMs: Long,
    val artworkPath: String?,
)
data class LibraryItemRow(
    val mediaKey: String,
    val sourceId: String,
    val sourceName: String,
    val path: String,
    val title: String,
    val fileName: String,
    val kind: String,
    val groupKey: String,
    val groupTitle: String,
    val season: Int?,
    val episode: Int?,
    val size: Long?,
    val modifiedAtEpochMs: Long?,
    val mimeType: String?,
    val plot: String?,
    val year: Int?,
    val rating: Float?,
    val director: String?,
    val castMembers: String?,
    val artworkPath: String?,
    val backdropPath: String?,
    val watchedPositionMs: Long?,
    val watchedDurationMs: Long?,
    val favorite: Boolean,
)

data class MediaVersionRow(
    val mediaKey: String,
    val path: String,
    val fileName: String,
    val size: Long?,
    val label: String?,
    val contentKey: String,
)

@Dao
interface MediaLibraryDao {
    @Query("SELECT * FROM remote_entries WHERE sourceId = :sourceId")
    suspend fun getEntries(sourceId: String): List<RemoteEntryIndexEntity>

    @Query("SELECT * FROM media_items WHERE sourceId = :sourceId")
    suspend fun getMediaItems(sourceId: String): List<MediaItemEntity>

    @Query(
        """
        SELECT m.sourceId, m.path, COALESCE(n.title, t.title, m.title) AS title, m.mimeType
        FROM media_items m
        LEFT JOIN metadata_records n ON n.metadataKey = m.mediaKey || ':nfo'
        LEFT JOIN metadata_records t ON t.metadataKey = m.mediaKey || ':tmdb'
        WHERE m.sourceId = :sourceId
          AND (
              (
                  (SELECT targetMedia.groupKey FROM media_items targetMedia
                   WHERE targetMedia.sourceId = :sourceId AND targetMedia.path = :currentPath) <> ''
                  AND m.groupKey = (SELECT targetMedia.groupKey FROM media_items targetMedia
                                    WHERE targetMedia.sourceId = :sourceId AND targetMedia.path = :currentPath)
                  AND m.kind = 'EPISODE'
              )
              OR (
                  (SELECT targetMedia.groupKey FROM media_items targetMedia
                   WHERE targetMedia.sourceId = :sourceId AND targetMedia.path = :currentPath) = ''
                  AND m.path = :currentPath
              )
          )
        ORDER BY m.season, m.episode, m.title COLLATE NOCASE
        """
    )
    suspend fun getPlaybackQueue(sourceId: String, currentPath: String): List<PlaybackQueueRow>
    @Query(
        """
        SELECT h.sourceId, s.displayName AS sourceName, h.path,
               COALESCE(n.title, t.title, m.title, h.path) AS title,
               h.positionMs, h.durationMs, h.updatedAtEpochMs,
               COALESCE(a.remotePath, ta.remotePath) AS artworkPath
        FROM playback_history h
        INNER JOIN media_sources s ON s.id = h.sourceId
        LEFT JOIN media_items m ON m.sourceId = h.sourceId AND m.path = h.path
        LEFT JOIN metadata_records n ON n.metadataKey = m.mediaKey || ':nfo'
        LEFT JOIN metadata_records t ON t.metadataKey = m.mediaKey || ':tmdb'
        LEFT JOIN artwork a ON a.artworkKey = m.mediaKey || ':poster'
        LEFT JOIN artwork ta ON ta.artworkKey = m.mediaKey || ':poster-tmdb'
        WHERE h.completed = 0 AND h.positionMs >= 30000
        ORDER BY h.updatedAtEpochMs DESC
        LIMIT 30
        """
    )
    fun observeContinueWatching(): Flow<List<ContinueWatchingRow>>

    @Query(
        """
        SELECT h.sourceId, s.displayName AS sourceName, h.path,
               COALESCE(n.title, t.title, m.title, h.path) AS title,
               h.positionMs, h.durationMs, h.updatedAtEpochMs,
               COALESCE(a.remotePath, ta.remotePath) AS artworkPath
        FROM playback_history h
        INNER JOIN media_sources s ON s.id = h.sourceId
        LEFT JOIN media_items m ON m.sourceId = h.sourceId AND m.path = h.path
        LEFT JOIN metadata_records n ON n.metadataKey = m.mediaKey || ':nfo'
        LEFT JOIN metadata_records t ON t.metadataKey = m.mediaKey || ':tmdb'
        LEFT JOIN artwork a ON a.artworkKey = m.mediaKey || ':poster'
        LEFT JOIN artwork ta ON ta.artworkKey = m.mediaKey || ':poster-tmdb'
        ORDER BY h.updatedAtEpochMs DESC
        LIMIT 50
        """
    )
    fun observeRecentHistory(): Flow<List<ContinueWatchingRow>>
    @Query(
        """
        SELECT m.mediaKey, m.sourceId, s.displayName AS sourceName, m.path,
               COALESCE(n.title, t.title, m.title) AS title, m.fileName,
               m.kind, m.groupKey, m.groupTitle, m.season, m.episode, m.size, m.modifiedAtEpochMs, m.mimeType,
               COALESCE(n.plot, t.plot) AS plot, COALESCE(n.year, t.year) AS year,
               COALESCE(n.rating, t.rating) AS rating,
               COALESCE(n.director, t.director) AS director, COALESCE(n.castMembers, t.castMembers) AS castMembers,
               COALESCE(a.remotePath, ta.remotePath) AS artworkPath,
               COALESCE(b.remotePath, tb.remotePath) AS backdropPath,
               (SELECT h.positionMs FROM playback_history h
                WHERE h.sourceId = m.sourceId AND h.path = m.path AND h.completed = 0
                ORDER BY h.updatedAtEpochMs DESC LIMIT 1) AS watchedPositionMs,
               (SELECT h.durationMs FROM playback_history h
                WHERE h.sourceId = m.sourceId AND h.path = m.path AND h.completed = 0
                ORDER BY h.updatedAtEpochMs DESC LIMIT 1) AS watchedDurationMs,
               EXISTS(SELECT 1 FROM favorites f WHERE f.mediaKey = m.mediaKey) AS favorite
        FROM media_items m
        INNER JOIN media_sources s ON s.id = m.sourceId
        LEFT JOIN metadata_records n ON n.metadataKey = m.mediaKey || ':nfo'
        LEFT JOIN metadata_records t ON t.metadataKey = m.mediaKey || ':tmdb'
        LEFT JOIN artwork a ON a.artworkKey = m.mediaKey || ':poster'
        LEFT JOIN artwork ta ON ta.artworkKey = m.mediaKey || ':poster-tmdb'
        LEFT JOIN artwork b ON b.artworkKey = m.mediaKey || ':backdrop'
        LEFT JOIN artwork tb ON tb.artworkKey = m.mediaKey || ':backdrop-tmdb'
        WHERE (
            :query = ''
            OR COALESCE(n.title, t.title, m.title) LIKE '%' || :query || '%'
            OR COALESCE(n.originalTitle, t.originalTitle) LIKE '%' || :query || '%'
            OR COALESCE(n.director, t.director) LIKE '%' || :query || '%'
            OR COALESCE(n.castMembers, t.castMembers) LIKE '%' || :query || '%'
            OR m.fileName LIKE '%' || :query || '%'
            OR m.path LIKE '%' || :query || '%'
        )
        ORDER BY m.title COLLATE NOCASE, m.season, m.episode
        """
    )
    fun observeMedia(query: String = ""): Flow<List<LibraryItemRow>>

    @Query(
        """
        SELECT m.mediaKey, m.sourceId, s.displayName AS sourceName, m.path,
               COALESCE(n.title, t.title, m.title) AS title, m.fileName,
               m.kind, m.groupKey, m.groupTitle, m.season, m.episode, m.size, m.modifiedAtEpochMs, m.mimeType,
               COALESCE(n.plot, t.plot) AS plot, COALESCE(n.year, t.year) AS year,
               COALESCE(n.rating, t.rating) AS rating,
               COALESCE(n.director, t.director) AS director, COALESCE(n.castMembers, t.castMembers) AS castMembers,
               COALESCE(a.remotePath, ta.remotePath) AS artworkPath,
               COALESCE(b.remotePath, tb.remotePath) AS backdropPath,
               (SELECT h.positionMs FROM playback_history h
                WHERE h.sourceId = m.sourceId AND h.path = m.path AND h.completed = 0
                ORDER BY h.updatedAtEpochMs DESC LIMIT 1) AS watchedPositionMs,
               (SELECT h.durationMs FROM playback_history h
                WHERE h.sourceId = m.sourceId AND h.path = m.path AND h.completed = 0
                ORDER BY h.updatedAtEpochMs DESC LIMIT 1) AS watchedDurationMs,
               EXISTS(SELECT 1 FROM favorites f WHERE f.mediaKey = m.mediaKey) AS favorite
        FROM media_items m
        INNER JOIN media_sources s ON s.id = m.sourceId
        LEFT JOIN metadata_records n ON n.metadataKey = m.mediaKey || ':nfo'
        LEFT JOIN metadata_records t ON t.metadataKey = m.mediaKey || ':tmdb'
        LEFT JOIN artwork a ON a.artworkKey = m.mediaKey || ':poster'
        LEFT JOIN artwork ta ON ta.artworkKey = m.mediaKey || ':poster-tmdb'
        LEFT JOIN artwork b ON b.artworkKey = m.mediaKey || ':backdrop'
        LEFT JOIN artwork tb ON tb.artworkKey = m.mediaKey || ':backdrop-tmdb'
        WHERE m.groupKey = :groupKey AND m.kind = 'EPISODE'
        ORDER BY m.season, m.episode, m.title COLLATE NOCASE
        """
    )
    fun observeEpisodes(groupKey: String): Flow<List<LibraryItemRow>>

    @Query("SELECT v.contentKey FROM media_versions v WHERE v.mediaKey = :mediaKey LIMIT 1")
    suspend fun getContentKey(mediaKey: String): String?

    @Query("SELECT * FROM metadata_records WHERE metadataKey = :metadataKey LIMIT 1")
    suspend fun getMetadata(metadataKey: String): MetadataRecordEntity?

    @Query(
        """
        SELECT m.mediaKey, m.path, m.fileName, m.size, v.label, v.contentKey
        FROM media_versions v
        INNER JOIN media_items m ON m.mediaKey = v.mediaKey
        WHERE v.contentKey = :contentKey
        ORDER BY CASE WHEN v.label IS NULL THEN 1 ELSE 0 END, v.label DESC, m.size DESC
        """
    )
    suspend fun getVersions(contentKey: String): List<MediaVersionRow>

    @Query(
        """
        SELECT f.mediaKey, f.sourceId, s.displayName AS sourceName, f.path, f.name,
               f.isDirectory, f.createdAtEpochMs,
               COALESCE(a.remotePath, ta.remotePath) AS artworkPath
        FROM favorites f
        INNER JOIN media_sources s ON s.id = f.sourceId
        LEFT JOIN artwork a ON a.artworkKey = f.mediaKey || ':poster'
        LEFT JOIN artwork ta ON ta.artworkKey = f.mediaKey || ':poster-tmdb'
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

    @Query("DELETE FROM metadata_records WHERE metadataKey = :metadataKey")
    suspend fun deleteMetadata(metadataKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtwork(items: List<ArtworkEntity>)

    @Query("DELETE FROM artwork WHERE artworkKey = :artworkKey")
    suspend fun deleteArtwork(artworkKey: String)

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
        items.forEach { item -> deleteMetadata("${item.mediaKey}:nfo") }
        if (metadata.isNotEmpty()) upsertMetadata(metadata)
        items.forEach { item ->
            deleteArtwork("${item.mediaKey}:poster")
            deleteArtwork("${item.mediaKey}:backdrop")
        }
        if (artwork.isNotEmpty()) upsertArtwork(artwork)
    }

    @Query("DELETE FROM remote_entries WHERE sourceId = :sourceId AND scanToken != :scanToken")
    suspend fun deleteStaleEntries(sourceId: String, scanToken: String)

    @Query("DELETE FROM media_items WHERE sourceId = :sourceId AND scanToken != :scanToken")
    suspend fun deleteStaleMediaRows(sourceId: String, scanToken: String)

    @Query("DELETE FROM media_versions WHERE mediaKey NOT IN (SELECT mediaKey FROM media_items)")
    suspend fun deleteOrphanVersions()

    @Query("DELETE FROM metadata_records WHERE mediaKey NOT IN (SELECT mediaKey FROM media_items)")
    suspend fun deleteOrphanMetadata()

    @Query("DELETE FROM artwork WHERE mediaKey NOT IN (SELECT mediaKey FROM media_items)")
    suspend fun deleteOrphanArtwork()

    @Query(
        "DELETE FROM playback_history WHERE sourceId = :sourceId " +
            "AND NOT EXISTS (SELECT 1 FROM media_items m WHERE m.sourceId = playback_history.sourceId AND m.path = playback_history.path)",
    )
    suspend fun deleteStaleHistory(sourceId: String)

    @Query(
        "DELETE FROM favorites WHERE sourceId = :sourceId AND isDirectory = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM media_items m WHERE m.mediaKey = favorites.mediaKey)",
    )
    suspend fun deleteStaleVideoFavorites(sourceId: String)

    @Transaction
    suspend fun deleteStaleMedia(sourceId: String, scanToken: String) {
        deleteStaleMediaRows(sourceId, scanToken)
        deleteOrphanVersions()
        deleteOrphanMetadata()
        deleteOrphanArtwork()
        deleteStaleHistory(sourceId)
        deleteStaleVideoFavorites(sourceId)
    }

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
