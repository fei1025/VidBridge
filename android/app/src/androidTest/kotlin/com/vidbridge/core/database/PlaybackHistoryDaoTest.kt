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
class PlaybackHistoryDaoTest {
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
    fun recentHistoryIncludesCompletedItemsAndSortsByLastPlayed() = runBlocking {
        database.mediaSources().upsert(
            MediaSourceEntity(
                id = "source",
                displayName = "测试来源",
                type = "LOCAL",
                scheme = "file",
                host = "",
                port = 0,
                tls = false,
                rootPath = "",
                shareName = null,
                rootUri = "content://test",
                username = null,
                credentialId = null,
                enabled = true,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        database.mediaLibrary().upsertMedia(
            listOf(
                media("one", "/one.mkv", "第一部"),
                media("two", "/two.mkv", "第二部"),
            ),
        )
        database.playbackHistory().upsert(
            PlaybackHistoryEntity("history-one", "source", "/one.mkv", 100_000L, 100_000L, true, 1L),
        )
        database.playbackHistory().upsert(
            PlaybackHistoryEntity("history-two", "source", "/two.mkv", 20_000L, 100_000L, false, 2L),
        )

        val rows = database.mediaLibrary().observeRecentHistory().first()

        assertEquals(listOf("第二部", "第一部"), rows.map { it.title })
        assertEquals(listOf(20_000L, 100_000L), rows.map { it.positionMs })

        database.playbackHistory().clearAll()

        assertEquals(emptyList<PlaybackHistoryEntity>(), database.playbackHistory().observeRecent().first())
    }

    private fun media(key: String, path: String, title: String) = MediaItemEntity(
        mediaKey = key,
        sourceId = "source",
        path = path,
        title = title,
        fileName = path.substringAfterLast('/'),
        kind = "MOVIE",
        groupKey = "",
        groupTitle = "",
        season = null,
        episode = null,
        size = 1L,
        modifiedAtEpochMs = 1L,
        mimeType = "video/x-matroska",
        scanToken = "scan",
        createdAtEpochMs = 1L,
    )
}
