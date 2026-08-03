package com.vidbridge.playback

import kotlin.math.abs
import kotlin.math.roundToLong

const val GESTURE_SEEK_SPAN_MS = 120_000L
const val MIN_WINDOW_BRIGHTNESS = 0.01f

enum class PlaybackGesture { SEEK, BRIGHTNESS, VOLUME }

/** Resolves one touch sequence after it has crossed the platform touch slop. */
fun resolvePlaybackGesture(
    deltaX: Float,
    deltaY: Float,
    downX: Float,
    width: Float,
): PlaybackGesture {
    if (abs(deltaX) >= abs(deltaY)) return PlaybackGesture.SEEK
    return if (downX < width / 2f) PlaybackGesture.BRIGHTNESS else PlaybackGesture.VOLUME
}

/** One full display width represents two minutes, with the target aligned to a video second. */
fun gestureSeekTargetMs(
    startPositionMs: Long,
    deltaX: Float,
    width: Float,
    durationMs: Long,
): Long {
    if (durationMs <= 0L || width <= 0f) return 0L
    val offset = (deltaX / width * GESTURE_SEEK_SPAN_MS).roundToLong()
    return ((startPositionMs + offset).coerceIn(0L, durationMs) / 1_000L) * 1_000L
}

/** Moving upward increases the value; the window brightness never becomes fully black. */
fun verticalGestureFraction(initial: Float, deltaY: Float, height: Float): Float {
    if (height <= 0f) return initial.coerceIn(MIN_WINDOW_BRIGHTNESS, 1f)
    return (initial - deltaY / height).coerceIn(MIN_WINDOW_BRIGHTNESS, 1f)
}

fun volumeForVerticalGesture(
    initialVolume: Int,
    maxVolume: Int,
    deltaY: Float,
    height: Float,
): Int {
    if (maxVolume <= 0 || height <= 0f) return 0
    val initialFraction = initialVolume.coerceIn(0, maxVolume).toFloat() / maxVolume
    return (verticalGestureFraction(initialFraction, deltaY, height) * maxVolume)
        .roundToLong()
        .toInt()
        .coerceIn(0, maxVolume)
}

/** Prevents expensive preview decodes for repeated positions and stale rapid motion. */
class SeekPreviewRequestGate(private val minimumIntervalMs: Long = 250L) {
    private var lastPositionMs: Long? = null
    private var lastRequestAtMs: Long = Long.MIN_VALUE

    fun shouldRequest(positionMs: Long, nowMs: Long): Boolean {
        val samePosition = positionMs == lastPositionMs
        val withinInterval = lastRequestAtMs != Long.MIN_VALUE && nowMs - lastRequestAtMs < minimumIntervalMs
        if (samePosition || withinInterval) return false
        lastPositionMs = positionMs
        lastRequestAtMs = nowMs
        return true
    }

    /** Returns when a distinct trailing request may run, or null for a duplicate. */
    fun delayUntilRequest(positionMs: Long, nowMs: Long): Long? {
        if (positionMs == lastPositionMs) return null
        if (lastRequestAtMs == Long.MIN_VALUE) return 0L
        return (minimumIntervalMs - (nowMs - lastRequestAtMs)).coerceAtLeast(0L)
    }

    fun reset() {
        lastPositionMs = null
        lastRequestAtMs = Long.MIN_VALUE
    }
}
