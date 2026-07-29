package com.vidbridge.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleDelayTest {
    @Test
    fun clampsDelayToSafePlaybackRange() {
        assertEquals(-10_000L, clampSubtitleDelayMs(-99_000L))
        assertEquals(0L, clampSubtitleDelayMs(0L))
        assertEquals(10_000L, clampSubtitleDelayMs(99_000L))
        assertEquals(-10_000L, clampAudioDelayMs(-99_000L))
        assertEquals(10_000L, clampAudioDelayMs(99_000L))
    }
}
