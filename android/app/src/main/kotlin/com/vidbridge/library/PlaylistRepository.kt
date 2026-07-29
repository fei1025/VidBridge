package com.vidbridge.library

import com.vidbridge.core.database.LibraryItemRow
import com.vidbridge.core.database.PlaylistDao
import com.vidbridge.core.database.PlaylistEntity
import com.vidbridge.core.database.PlaylistItemEntity
import com.vidbridge.core.database.PlaylistItemRow
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PlaylistRepository(private val dao: PlaylistDao) {
    fun observePlaylists(): Flow<List<PlaylistEntity>> = dao.observePlaylists()

    fun observeItems(playlistId: String): Flow<List<PlaylistItemRow>> = dao.observeItems(playlistId)

    suspend fun getItems(playlistId: String): List<PlaylistItemRow> = dao.getItems(playlistId).map {
        PlaylistItemRow(it.playlistId, it.mediaKey, it.sourceId, it.path, it.title, it.position)
    }

    suspend fun create(name: String): PlaylistEntity {
        val now = System.currentTimeMillis()
        return PlaylistEntity(UUID.randomUUID().toString(), name.trim(), now, now).also { dao.insertPlaylist(it) }
    }

    suspend fun add(playlist: PlaylistEntity, item: LibraryItemRow) {
        dao.addItem(
            playlist.id,
            PlaylistItemEntity(
                playlistId = playlist.id,
                mediaKey = item.mediaKey,
                sourceId = item.sourceId,
                path = item.path,
                title = item.title,
                position = dao.countItems(playlist.id),
                addedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(playlistId: String, mediaKey: String) = dao.removeItem(playlistId, mediaKey)

    suspend fun rename(playlistId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) dao.renamePlaylist(playlistId, trimmed, System.currentTimeMillis())
    }

    suspend fun moveUp(playlistId: String, mediaKey: String) = dao.moveItem(playlistId, mediaKey, -1)

    suspend fun moveDown(playlistId: String, mediaKey: String) = dao.moveItem(playlistId, mediaKey, 1)

    suspend fun removeForSource(sourceId: String) = dao.removeItemsForSource(sourceId)

    suspend fun delete(playlistId: String) = dao.deletePlaylist(playlistId)
}
