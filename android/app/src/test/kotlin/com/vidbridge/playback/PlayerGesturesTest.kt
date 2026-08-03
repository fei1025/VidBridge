package com.vidbridge.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGesturesTest {
    @Test
    fun horizontalMotionWinsAndUsesTwoMinuteSpan() {
        assertEquals(PlaybackGesture.SEEK, resolvePlaybackGesture(20f, 10f, 10f, 400f))
        assertEquals(180_000L, gestureSeekTargetMs(60_000L, 400f, 400f, 600_000L))
    }

    @Test
    fun verticalMotionUsesTheTouchDownSide() {
        assertEquals(PlaybackGesture.BRIGHTNESS, resolvePlaybackGesture(5f, 30f, 10f, 400f))
        assertEquals(PlaybackGesture.VOLUME, resolvePlaybackGesture(5f, 30f, 390f, 400f))
    }

    @Test
    fun seekTargetClampsAndAlignsToWholeSeconds() {
        assertEquals(0L, gestureSeekTargetMs(5_000L, -1_000f, 400f, 90_000L))
        assertEquals(90_000L, gestureSeekTargetMs(80_555L, 1_000f, 400f, 90_000L))
        assertEquals(40_000L, gestureSeekTargetMs(10_555L, 100f, 400f, 90_000L))
    }

    @Test
    fun verticalValuesStayInSafeRanges() {
        assertEquals(MIN_WINDOW_BRIGHTNESS, verticalGestureFraction(0.5f, 1_000f, 100f), 0.0001f)
        assertEquals(1f, verticalGestureFraction(0.5f, -1_000f, 100f), 0.0001f)
        assertEquals(0, volumeForVerticalGesture(5, 15, 1_000f, 100f))
        assertEquals(15, volumeForVerticalGesture(5, 15, -1_000f, 100f))
    }

    @Test
    fun previewGateDropsDuplicateAndFastStaleRequests() {
        val gate = SeekPreviewRequestGate(250L)
        assertTrue(gate.shouldRequest(10_000L, 1_000L))
        assertFalse(gate.shouldRequest(10_000L, 1_300L))
        assertEquals(null, gate.delayUntilRequest(10_000L, 1_300L))
        assertFalse(gate.shouldRequest(11_000L, 1_100L))
        assertEquals(150L, gate.delayUntilRequest(11_000L, 1_100L))
        assertTrue(gate.shouldRequest(11_000L, 1_300L))
    }
}
