package com.vidbridge.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LibVlcRetryTest {
    @Test
    fun retryPrefersPositionReachedBeforeNetworkFailure() {
        assertEquals(125_000L, retryStartPosition(30_000L, 125_000L))
    }

    @Test
    fun retryKeepsOriginalResumePositionWhenCurrentPositionIsUnavailable() {
        assertEquals(30_000L, retryStartPosition(30_000L, 0L))
    }

    @Test
    fun replayStartsAtBeginningWhenPlaybackAlreadyReachedEnd() {
        assertEquals(0L, restartPositionAfterEnd(99_900L, 100_000L))
    }

    @Test
    fun replayKeepsASeekedPositionAwayFromEnd() {
        assertEquals(12_000L, restartPositionAfterEnd(12_000L, 100_000L))
    }
}
