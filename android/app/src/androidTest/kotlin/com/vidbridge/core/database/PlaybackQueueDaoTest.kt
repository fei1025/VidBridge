package com.vidbridge.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueDaoTest {
    private lateinit var database: VidBridgeDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VidBridgeDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun episodeQueueStaysWithinTheSameSeriesAndMovieDoesNotJump() = runBlocking {
        database.mediaSources().upsert(
            MediaSourceEntity(
                id = "source",
                displayName = "Test source",
                type = "SMB",
                scheme = "smb",
                host = "host",
                port = 445,
                tls = false,
                rootPath = "",
                shareName = "Videos",
                rootUri = null,
                username = null,
                credentialId = null,
                enabled = true,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        database.mediaLibrary().upsertMedia(
            listOf(
                media("episode-1", "/show/S01E01.mkv", "Show", "EPISODE", "show", 1, 1),
                media("episode-2", "/show/S01E02.mkv", "Show", "EPISODE", "show", 1, 2),
                media("other-show", "/other/S01E01.mkv", "Other", "EPISODE", "other", 1, 1),
                media("movie", "/movie.mkv", "Movie", "MOVIE", "", null, null),
            ),
        )

        val episodes = database.mediaLibrary().getPlaybackQueue("source", "/show/S01E01.mkv")
        val movie = database.mediaLibrary().getPlaybackQueue("source", "/movie.mkv")

        assertEquals(listOf("/show/S01E01.mkv", "/show/S01E02.mkv"), episodes.map { it.path })
        assertEquals(listOf("/movie.mkv"), movie.map { it.path })
    }

    private fun media(
        key: String,
        path: String,
        title: String,
        kind: String,
        groupKey: String,
        season: Int?,
        episode: Int?,
    ) = MediaItemEntity(
        mediaKey = key,
        sourceId = "source",
        path = path,
        title = title,
        fileName = path.substringAfterLast('/'),
        kind = kind,
        groupKey = groupKey,
        groupTitle = title,
        season = season,
        episode = episode,
        size = 1L,
        modifiedAtEpochMs = 1L,
        mimeType = "video/x-matroska",
        scanToken = "scan",
        createdAtEpochMs = 1L,
    )
}
