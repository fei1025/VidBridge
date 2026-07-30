package com.vidbridge.playback

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.MediaPlayer.ScaleType
import org.videolan.libvlc.util.VLCVideoLayout
import com.vidbridge.protocol.api.safeUserMessage

/** Owns one libVLC instance and keeps UI/service code independent from its API. */
class LibVlcPlayerEngine(context: Context) : PlayerEngine {
    private val libVlc = LibVLC(context.applicationContext, arrayListOf("--audio-time-stretch"))
    private val player = MediaPlayer(libVlc)
    private val mutableState = MutableStateFlow(PlayerState())
    private var lastMedia: PlayerMedia? = null
    private var attachedLayout: VLCVideoLayout? = null
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    init {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> update { copy(playbackState = PlayerPlaybackState.PLAYING, isPlaying = true, errorMessage = null) }
                MediaPlayer.Event.Paused -> update { copy(playbackState = PlayerPlaybackState.PAUSED, isPlaying = false) }
                MediaPlayer.Event.EndReached -> update { copy(playbackState = PlayerPlaybackState.ENDED, isPlaying = false) }
                MediaPlayer.Event.EncounteredError -> update {
                    copy(playbackState = PlayerPlaybackState.ERROR, isPlaying = false, errorMessage = "libVLC 无法打开媒体")
                }
            }
        }
    }

    override fun attachSurface(layout: VLCVideoLayout) {
        if (attachedLayout === layout) return
        runCatching { player.detachViews() }
        player.attachViews(layout, null, true, false)
        attachedLayout = layout
    }

    override fun detachSurface() {
        if (attachedLayout == null) return
        runCatching { player.detachViews() }
        attachedLayout = null
    }

    override fun prepare(media: PlayerMedia) {
        check(mutableState.value.playbackState != PlayerPlaybackState.RELEASED) { "播放器已释放" }
        lastMedia = media
        update {
            copy(
                playbackState = PlayerPlaybackState.PREPARING,
                positionMs = 0L,
                durationMs = 0L,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                chapters = emptyList(),
                currentChapter = null,
                selectedAudioTrack = null,
                selectedSubtitleTrack = null,
                errorMessage = null,
            )
        }
        runCatching {
            val item = Media(libVlc, media.uri)
            media.options.forEach(item::addOption)
            player.media = item
            item.release()
            if (media.startPositionMs > 0L) player.time = media.startPositionMs
        }.onFailure { error ->
            Log.e(TAG, "Failed to prepare media (${error.javaClass.name})")
            update { copy(playbackState = PlayerPlaybackState.ERROR, errorMessage = error.safeUserMessage("无法准备媒体")) }
        }
    }

    override fun retry() {
        lastMedia?.let {
            prepare(it.copy(startPositionMs = retryStartPosition(it.startPositionMs, mutableState.value.positionMs)))
            play()
        }
    }

    override fun play() {
        // libVLC stays at EndReached after the last frame. Calling play() alone
        // does not reopen that media, so rebuild it before starting again.
        if (mutableState.value.playbackState == PlayerPlaybackState.ENDED) {
            lastMedia?.let { media ->
                prepare(media.copy(startPositionMs = restartPositionAfterEnd(
                    mutableState.value.positionMs,
                    mutableState.value.durationMs,
                )))
            }
        }
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        val position = positionMs.coerceAtLeast(0L)
        if (mutableState.value.playbackState == PlayerPlaybackState.ENDED) {
            // Seeking from EndReached also needs a fresh media item. Keep it
            // paused/preparing so the next press of Play starts at the seeked time.
            lastMedia?.let {
                prepare(it.copy(startPositionMs = position))
                return
            }
        }
        player.time = position
    }

    override fun setSpeed(speed: Float) {
        val value = speed.coerceIn(0.25f, 4f)
        player.rate = value
        update { copy(speed = value) }
    }

    override fun setVideoScale(scale: PlayerVideoScale) {
        val value = when (scale) {
            PlayerVideoScale.FIT -> ScaleType.SURFACE_BEST_FIT
            PlayerVideoScale.FILL -> ScaleType.SURFACE_FILL
            PlayerVideoScale.ZOOM -> ScaleType.SURFACE_ORIGINAL
        }
        player.setVideoScale(value)
    }

    override fun selectAudioTrack(id: Int) {
        if (player.setAudioTrack(id)) update { copy(selectedAudioTrack = id) }
    }

    override fun selectSubtitleTrack(id: Int) {
        if (player.setSpuTrack(id)) update { copy(selectedSubtitleTrack = id) }
    }

    override fun selectChapter(index: Int) {
        val chapter = mutableState.value.chapters.getOrNull(index) ?: return
        runCatching { player.setChapter(index) }
            .onSuccess {
                seekTo(chapter.startMs)
                update { copy(currentChapter = index) }
            }
            .onFailure { Log.w(TAG, "Failed to select chapter $index (${it.javaClass.name})") }
    }

    override fun setAudioDelayMs(delayMs: Long) {
        val value = clampAudioDelayMs(delayMs)
        if (player.setAudioDelay(value * 1_000L)) update { copy(audioDelayMs = value) }
    }

    override fun setSubtitleDelayMs(delayMs: Long) {
        val value = clampSubtitleDelayMs(delayMs)
        if (player.setSpuDelay(value * 1_000L)) update { copy(subtitleDelayMs = value) }
    }

    fun refreshPosition() {
        if (mutableState.value.playbackState == PlayerPlaybackState.RELEASED) return
        update {
            copy(
                positionMs = player.time.coerceAtLeast(0L),
                durationMs = player.length.coerceAtLeast(0L),
            )
        }
    }

    fun refreshTracks() {
        if (mutableState.value.playbackState == PlayerPlaybackState.RELEASED) return
        val audio = player.audioTracks.orEmpty().map { PlayerTrack(it.id, it.name) }
        val subtitles = player.spuTracks.orEmpty().map { PlayerTrack(it.id, it.name) }
        update {
            copy(
                audioTracks = audio,
                subtitleTracks = subtitles,
                chapters = readChapters(),
                currentChapter = player.chapter.takeIf { it >= 0 },
                selectedAudioTrack = player.audioTrack.takeUnless { it < 0 },
                selectedSubtitleTrack = player.spuTrack.takeUnless { it < 0 },
                audioDelayMs = (player.audioDelay / 1_000L).coerceIn(-10_000L, 10_000L),
                subtitleDelayMs = (player.spuDelay / 1_000L).coerceIn(-10_000L, 10_000L),
            )
        }
    }

    private fun readChapters(): List<PlayerChapter> {
        val title = player.title
        if (title < 0) return emptyList()
        return player.getChapters(title).orEmpty().mapIndexed { index, chapter ->
            PlayerChapter(
                index = index,
                name = chapter.name?.takeIf { it.isNotBlank() } ?: "章节 ${index + 1}",
                startMs = chapter.timeOffset.coerceAtLeast(0L),
                durationMs = chapter.duration.coerceAtLeast(0L),
            )
        }
    }

    override fun release() {
        if (mutableState.value.playbackState == PlayerPlaybackState.RELEASED) return
        runCatching { player.stop() }
        detachSurface()
        player.release()
        libVlc.release()
        update { copy(playbackState = PlayerPlaybackState.RELEASED) }
    }

    private fun update(transform: PlayerState.() -> PlayerState) {
        mutableState.value = mutableState.value.transform()
    }

    private companion object { const val TAG = "VidBridgePlayer" }
}

internal fun clampSubtitleDelayMs(value: Long): Long = value.coerceIn(-10_000L, 10_000L)
internal fun clampAudioDelayMs(value: Long): Long = value.coerceIn(-10_000L, 10_000L)

internal fun retryStartPosition(originalPositionMs: Long, currentPositionMs: Long): Long =
    maxOf(originalPositionMs, currentPositionMs).coerceAtLeast(0L)

internal fun restartPositionAfterEnd(positionMs: Long, durationMs: Long): Long =
    if (durationMs > 0L && positionMs >= (durationMs - 1_000L).coerceAtLeast(0L)) 0L
    else positionMs.coerceAtLeast(0L)
