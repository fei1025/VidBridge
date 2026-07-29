package com.vidbridge.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSessionStoreTest {
    private val session = PlaybackSession(
        sourceId = "source-a",
        path = "Movies/example.mkv",
        title = "Example",
        positionMs = 12_000L,
        localPath = "downloads/example.mkv",
        playlistId = "playlist-a",
    )

    @Test
    fun localPathIsRecoveredOnlyForTheSameMedia() {
        assertEquals(
            "downloads/example.mkv",
            localRecoveryPath(session, "source-a", "Movies/example.mkv") { true },
        )
        assertNull(localRecoveryPath(session, "source-b", "Movies/example.mkv") { true })
        assertNull(localRecoveryPath(session, "source-a", "Movies/other.mkv") { true })
    }

    @Test
    fun missingLocalFileIsNotRecovered() {
        assertNull(localRecoveryPath(session, "source-a", "Movies/example.mkv") { false })
    }
}
