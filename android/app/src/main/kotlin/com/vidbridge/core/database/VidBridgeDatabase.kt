package com.vidbridge.core.database

import android.content.Context
import androidx.room.*
import com.vidbridge.protocol.api.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Entity(tableName = "media_sources")
data class MediaSourceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val type: String,
    val scheme: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val rootPath: String,
    val shareName: String?,
    val rootUri: String?,
    val username: String?,
    val credentialId: String?,
    val enabled: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["sourceId", "path"]),
        Index("updatedAtEpochMs"),
    ],
)
data class PlaybackHistoryEntity(
    @PrimaryKey val mediaKey: String,
    val sourceId: String,
    val path: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAtEpochMs: Long,
)

@Dao
interface MediaSourceDao {
    @Query("SELECT * FROM media_sources ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<MediaSourceEntity>>

    @Query("SELECT * FROM media_sources WHERE id = :id")
    suspend fun get(id: String): MediaSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaSourceEntity)

    @Query("DELETE FROM media_sources WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM media_sources WHERE rootUri = :rootUri")
    suspend fun countByRootUri(rootUri: String): Int
}

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history WHERE mediaKey = :key")
    suspend fun get(key: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: String)

    @Query("DELETE FROM playback_history WHERE mediaKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearAll()

    @Query("SELECT * FROM playback_history ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<PlaybackHistoryEntity>>
}

@Database(
    entities = [
        MediaSourceEntity::class,
        PlaybackHistoryEntity::class,
        RemoteEntryIndexEntity::class,
        MediaItemEntity::class,
        MediaVersionEntity::class,
        MetadataRecordEntity::class,
        ArtworkEntity::class,
        FavoriteEntity::class,
        ScanJobEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        DownloadEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class VidBridgeDatabase : RoomDatabase() {
    abstract fun mediaSources(): MediaSourceDao
    abstract fun playbackHistory(): PlaybackHistoryDao
        abstract fun mediaLibrary(): MediaLibraryDao
        abstract fun scanJobs(): ScanJobDao
        abstract fun playlists(): PlaylistDao
        abstract fun downloads(): DownloadDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_sources ADD COLUMN rootUri TEXT")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS remote_entries (
                        sourceId TEXT NOT NULL,
                        path TEXT NOT NULL,
                        name TEXT NOT NULL,
                        isDirectory INTEGER NOT NULL,
                        size INTEGER,
                        modifiedAtEpochMs INTEGER,
                        fingerprint TEXT NOT NULL,
                        scanToken TEXT NOT NULL,
                        PRIMARY KEY(sourceId, path),
                        FOREIGN KEY(sourceId) REFERENCES media_sources(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_remote_entries_sourceId ON remote_entries (sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_remote_entries_sourceId_scanToken ON remote_entries (sourceId, scanToken)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS media_items (
                        mediaKey TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        path TEXT NOT NULL,
                        title TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        season INTEGER,
                        episode INTEGER,
                        size INTEGER,
                        modifiedAtEpochMs INTEGER,
                        mimeType TEXT,
                        scanToken TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(mediaKey),
                        FOREIGN KEY(sourceId) REFERENCES media_sources(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_sourceId ON media_items (sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_sourceId_scanToken ON media_items (sourceId, scanToken)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_title ON media_items (title)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS media_versions (
                        versionKey TEXT NOT NULL,
                        mediaKey TEXT NOT NULL,
                        contentKey TEXT NOT NULL,
                        label TEXT,
                        width INTEGER,
                        height INTEGER,
                        videoCodec TEXT,
                        PRIMARY KEY(versionKey),
                        FOREIGN KEY(mediaKey) REFERENCES media_items(mediaKey) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_versions_mediaKey ON media_versions (mediaKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_versions_contentKey ON media_versions (contentKey)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS metadata_records (
                        metadataKey TEXT NOT NULL,
                        mediaKey TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        title TEXT,
                        originalTitle TEXT,
                        plot TEXT,
                        year INTEGER,
                        rating REAL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(metadataKey),
                        FOREIGN KEY(mediaKey) REFERENCES media_items(mediaKey) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_records_mediaKey ON metadata_records (mediaKey)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS artwork (
                        artworkKey TEXT NOT NULL,
                        mediaKey TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        remotePath TEXT NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(artworkKey),
                        FOREIGN KEY(mediaKey) REFERENCES media_items(mediaKey) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_artwork_mediaKey ON artwork (mediaKey)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS favorites (
                        mediaKey TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        path TEXT NOT NULL,
                        name TEXT NOT NULL,
                        isDirectory INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(mediaKey),
                        FOREIGN KEY(sourceId) REFERENCES media_sources(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_sourceId ON favorites (sourceId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS scan_jobs (
                        sourceId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        scannedEntries INTEGER NOT NULL,
                        scannedMedia INTEGER NOT NULL,
                        startedAtEpochMs INTEGER,
                        updatedAtEpochMs INTEGER NOT NULL,
                        errorMessage TEXT,
                        scanToken TEXT,
                        currentPath TEXT,
                        nextOffset INTEGER,
                        pendingPathsJson TEXT,
                        PRIMARY KEY(sourceId),
                        FOREIGN KEY(sourceId) REFERENCES media_sources(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN groupKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media_items ADD COLUMN groupTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_groupKey ON media_items (groupKey)")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS playlists (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )""",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS playlist_items (
                        playlistId TEXT NOT NULL,
                        mediaKey TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        path TEXT NOT NULL,
                        title TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        addedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, mediaKey)
                    )""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_items_playlistId_position ON playlist_items (playlistId, position)")
            }
        }
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS downloads (
                        id TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        path TEXT NOT NULL,
                        title TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        totalBytes INTEGER,
                        downloadedBytes INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        errorMessage TEXT,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(sourceId) REFERENCES media_sources(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_downloads_sourceId_path ON downloads (sourceId, path)")
            }
        }
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE metadata_records ADD COLUMN director TEXT")
                db.execSQL("ALTER TABLE metadata_records ADD COLUMN castMembers TEXT")
            }
        }
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_history_sourceId_path ON playback_history (sourceId, path)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_history_updatedAtEpochMs ON playback_history (updatedAtEpochMs)")
            }
        }
        fun create(context: Context): VidBridgeDatabase =
            Room.databaseBuilder(context, VidBridgeDatabase::class.java, "vidbridge.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
    }
}

fun MediaSourceEntity.toModel() = MediaSourceConfig(
    id = id,
    displayName = displayName,
    type = MediaSourceType.valueOf(type),
    endpoint = Endpoint(scheme, host, port, tls),
    rootPath = rootPath,
    shareName = shareName,
    rootUri = rootUri,
    username = username,
    credentialId = credentialId,
    enabled = enabled,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

fun MediaSourceConfig.toEntity() = MediaSourceEntity(
    id, displayName, type.name, endpoint.scheme, endpoint.host, endpoint.port, endpoint.tls,
    rootPath, shareName, rootUri, username, credentialId, enabled, createdAt.toEpochMilli(), updatedAt.toEpochMilli(),
)
