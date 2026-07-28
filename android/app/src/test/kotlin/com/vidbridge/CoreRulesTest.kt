package com.vidbridge

import com.vidbridge.core.database.PlaybackHistoryEntity
import com.vidbridge.playback.PlaybackHistoryRepository
import com.vidbridge.protocol.api.RemotePath
import org.junit.Assert.*
import org.junit.Test

class CoreRulesTest {
    @Test
    fun remotePathNormalizesChildrenAndParents() {
        val root = RemotePath("Movies")
        val child = root.child("电影 2026/第一集.mkv")
        assertEquals("Movies/电影 2026/第一集.mkv", child.value)
        assertEquals("Movies/电影 2026", child.parent.value)
    }

    @Test
    fun completionUsesNinetyPercentThreshold() {
        assertFalse(PlaybackHistoryRepository.isCompleted(89_999, 100_000))
        assertTrue(PlaybackHistoryRepository.isCompleted(90_000, 100_000))
        assertFalse(PlaybackHistoryRepository.isCompleted(0, 0))
    }

    @Test
    fun resumeSkipsShortAndCompletedProgress() {
        fun record(position: Long, completed: Boolean) = PlaybackHistoryEntity(
            "key", "source", "movie.mkv", position, 100_000, completed, 1,
        )
        assertEquals(0, PlaybackHistoryRepository.resumePosition(record(29_999, false)))
        assertEquals(30_000, PlaybackHistoryRepository.resumePosition(record(30_000, false)))
        assertEquals(0, PlaybackHistoryRepository.resumePosition(record(95_000, true)))
    }

    @Test
    fun mediaKeyIsStableAndSeparatesSourceAndPath() {
        val first = PlaybackHistoryRepository.key("one", "Movies/a.mkv")
        assertEquals(first, PlaybackHistoryRepository.key("one", "Movies/a.mkv"))
        assertNotEquals(first, PlaybackHistoryRepository.key("two", "Movies/a.mkv"))
        assertNotEquals(first, PlaybackHistoryRepository.key("one", "Movies/b.mkv"))
    }
}
