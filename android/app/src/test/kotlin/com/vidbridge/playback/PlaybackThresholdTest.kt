package com.vidbridge.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackThresholdTest {
    @Test
    fun customCompletionThresholdIsAppliedAndClamped() {
        assertTrue(PlaybackHistoryRepository.isCompleted(80_000, 100_000, 0.8f))
        assertFalse(PlaybackHistoryRepository.isCompleted(79_999, 100_000, 0.8f))
        assertFalse(PlaybackHistoryRepository.isCompleted(99_999, 100_000, 2f))
    }
}
