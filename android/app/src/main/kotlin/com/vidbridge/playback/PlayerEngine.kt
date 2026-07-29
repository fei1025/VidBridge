package com.vidbridge.playback

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import org.videolan.libvlc.util.VLCVideoLayout

enum class PlayerPlaybackState { IDLE, PREPARING, PLAYING, PAUSED, ENDED, ERROR, RELEASED }

enum class PlayerVideoScale { FIT, FILL, ZOOM }

data class PlayerTrack(val id: Int, val name: String)

data class PlayerChapter(val index: Int, val name: String, val startMs: Long, val durationMs: Long)

data class PlayerState(
    val playbackState: PlayerPlaybackState = PlayerPlaybackState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val audioTracks: List<PlayerTrack> = emptyList(),
    val subtitleTracks: List<PlayerTrack> = emptyList(),
    val chapters: List<PlayerChapter> = emptyList(),
    val currentChapter: Int? = null,
    val selectedAudioTrack: Int? = null,
    val selectedSubtitleTrack: Int? = null,
    val audioDelayMs: Long = 0L,
    val subtitleDelayMs: Long = 0L,
    val errorMessage: String? = null,
)

data class PlayerMedia(
    val uri: Uri,
    val options: List<String> = emptyList(),
    val startPositionMs: Long = 0L,
)

interface PlayerEngine {
    val state: StateFlow<PlayerState>

    fun attachSurface(layout: VLCVideoLayout)
    fun detachSurface()
    fun prepare(media: PlayerMedia)
    fun retry()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setVideoScale(scale: PlayerVideoScale)
    fun selectAudioTrack(id: Int)
    fun selectSubtitleTrack(id: Int)
    fun selectChapter(index: Int)
    fun setAudioDelayMs(delayMs: Long)
    fun setSubtitleDelayMs(delayMs: Long)
    fun release()
}
