package com.vidbridge.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaKey"],
    indices = [androidx.room.Index(value = ["playlistId", "position"])],
)
data class PlaylistItemEntity(
    val playlistId: String,
    val mediaKey: String,
    val sourceId: String,
    val path: String,
    val title: String,
    val position: Int,
    val addedAtEpochMs: Long,
)

data class PlaylistItemRow(
    val playlistId: String,
    val mediaKey: String,
    val sourceId: String,
    val path: String,
    val title: String,
    val position: Int,
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAtEpochMs DESC, name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT playlistId, mediaKey, sourceId, path, title, position FROM playlist_items WHERE playlistId = :playlistId ORDER BY position, addedAtEpochMs")
    fun observeItems(playlistId: String): Flow<List<PlaylistItemRow>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position, addedAtEpochMs")
    suspend fun getItems(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun countItems(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity)

    @Query("UPDATE playlists SET updatedAtEpochMs = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: String, updatedAt: Long)

    @Query("UPDATE playlists SET name = :name, updatedAtEpochMs = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, updatedAt: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaKey = :mediaKey")
    suspend fun removeItem(playlistId: String, mediaKey: String)

    @Query("UPDATE playlist_items SET position = :position WHERE playlistId = :playlistId AND mediaKey = :mediaKey")
    suspend fun updatePosition(playlistId: String, mediaKey: String, position: Int)

    @Query("DELETE FROM playlist_items WHERE sourceId = :sourceId")
    suspend fun removeItemsForSource(sourceId: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteItemsForPlaylist(playlistId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistRow(playlistId: String)

    @Transaction
    suspend fun deletePlaylist(playlistId: String) {
        deleteItemsForPlaylist(playlistId)
        deletePlaylistRow(playlistId)
    }

    @Transaction
    suspend fun addItem(playlistId: String, item: PlaylistItemEntity) {
        insertItem(item)
        touchPlaylist(playlistId, System.currentTimeMillis())
    }

    @Transaction
    suspend fun moveItem(playlistId: String, mediaKey: String, offset: Int) {
        val items = getItems(playlistId)
        val currentIndex = items.indexOfFirst { it.mediaKey == mediaKey }
        val targetIndex = currentIndex + offset
        if (currentIndex !in items.indices || targetIndex !in items.indices) return
        val current = items[currentIndex]
        val target = items[targetIndex]
        updatePosition(playlistId, current.mediaKey, target.position)
        updatePosition(playlistId, target.mediaKey, current.position)
        touchPlaylist(playlistId, System.currentTimeMillis())
    }
}
