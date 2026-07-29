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
}
