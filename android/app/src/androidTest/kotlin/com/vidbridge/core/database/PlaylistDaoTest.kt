package com.vidbridge.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {
    private lateinit var database: VidBridgeDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VidBridgeDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun playlistKeepsOrderAndIgnoresDuplicateMedia() = runBlocking {
        val playlist = PlaylistEntity("playlist", "晚间观看", 1L, 1L)
        database.playlists().insertPlaylist(playlist)
        database.playlists().addItem("playlist", item("one", "/one.mkv", 0))
        database.playlists().addItem("playlist", item("one", "/one.mkv", 1))
        database.playlists().addItem("playlist", item("two", "/two.mkv", 1))

        val rows = database.playlists().observeItems("playlist").first()
        assertEquals(listOf("/one.mkv", "/two.mkv"), rows.map { it.path })
        assertEquals(2, database.playlists().countItems("playlist"))
    }

    @Test
    fun playlistCanMoveItemsInBothDirections() = runBlocking {
        database.playlists().insertPlaylist(PlaylistEntity("playlist", "晚间观看", 1L, 1L))
        database.playlists().addItem("playlist", item("one", "/one.mkv", 0))
        database.playlists().addItem("playlist", item("two", "/two.mkv", 1))
        database.playlists().addItem("playlist", item("three", "/three.mkv", 2))

        database.playlists().moveItem("playlist", "three", -1)
        assertEquals(
            listOf("/one.mkv", "/three.mkv", "/two.mkv"),
            database.playlists().observeItems("playlist").first().map { it.path },
        )
        database.playlists().moveItem("playlist", "one", 1)
        assertEquals(
            listOf("/three.mkv", "/one.mkv", "/two.mkv"),
            database.playlists().observeItems("playlist").first().map { it.path },
        )
    }

    @Test
    fun deletingPlaylistAlsoDeletesItsItems() = runBlocking {
        database.playlists().insertPlaylist(PlaylistEntity("playlist", "晚间观看", 1L, 1L))
        database.playlists().addItem("playlist", item("one", "/one.mkv", 0))
        database.playlists().addItem("playlist", item("two", "/two.mkv", 1))

        database.playlists().deletePlaylist("playlist")

        assertEquals(0, database.playlists().countItems("playlist"))
        assertEquals(emptyList<PlaylistEntity>(), database.playlists().observePlaylists().first())
    }

    @Test
    fun renamingPlaylistUpdatesNameAndTimestamp() = runBlocking {
        database.playlists().insertPlaylist(PlaylistEntity("playlist", "旧名称", 1L, 1L))

        database.playlists().renamePlaylist("playlist", "新名称", 2L)

        assertEquals("新名称", database.playlists().observePlaylists().first().single().name)
        assertEquals(2L, database.playlists().observePlaylists().first().single().updatedAtEpochMs)
    }

    private fun item(mediaKey: String, path: String, position: Int) = PlaylistItemEntity(
        playlistId = "playlist",
        mediaKey = mediaKey,
        sourceId = "source",
        path = path,
        title = path,
        position = position,
        addedAtEpochMs = position.toLong(),
    )
}
