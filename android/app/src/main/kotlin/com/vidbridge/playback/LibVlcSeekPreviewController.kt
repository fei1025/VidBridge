package com.vidbridge.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

data class VlcSeekPreviewState(
    val active: Boolean = false,
    val targetPositionMs: Long = 0L,
    val hasFrame: Boolean = false,
    val failed: Boolean = false,
)

/** A persistent, muted software decoder dedicated to accurate seek previews. */
class LibVlcSeekPreviewController(context: Context) : AutoCloseable {
    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf("--no-audio", "--no-spu"),
    )
    private val player = MediaPlayer(libVlc)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(VlcSeekPreviewState())
    private var layout: VLCVideoLayout? = null
    private var mediaKey: String? = null
    private var pendingMedia: PlayerMedia? = null
    private var pendingTargetMs: Long = 0L
    private var priming = false
    private var softwareFallbackMediaKey: String? = null
    private var currentUsesHardwareDecoder = true
    private var generation = 0L
    private var scheduledGeneration = -1L
    private var released = false

    val state: StateFlow<VlcSeekPreviewState> = mutableState.asStateFlow()

    init {
        player.volume = 0
        player.setEventListener { event ->
            handler.post {
                when (event.type) {
                    MediaPlayer.Event.Playing -> seekToPendingTarget()
                    MediaPlayer.Event.TimeChanged -> onPreviewTime(event.timeChanged)
                    MediaPlayer.Event.EncounteredError -> {
                        retryWithSoftwareDecoderOrFail()
                    }
                }
            }
        }
    }

    fun attach(layout: VLCVideoLayout) {
        if (released || this.layout === layout) return
        runCatching { player.detachViews() }
        // TextureView is composited correctly inside a small Compose overlay and
        // can remain attached between all drag gestures.
        player.attachViews(layout, null, false, true)
        this.layout = layout
        if (mutableState.value.active || priming) startPendingRequest()
    }

    /** Opens and seeks the secondary decoder before the first drag starts. */
    fun prime(media: PlayerMedia, targetPositionMs: Long) {
        if (released || mutableState.value.active) return
        pendingMedia = media
        pendingTargetMs = targetPositionMs.coerceAtLeast(0L)
        priming = true
        generation++
        scheduledGeneration = -1L
        startPendingRequest()
    }

    fun request(media: PlayerMedia, targetPositionMs: Long) {
        if (released) return
        pendingMedia = media
        pendingTargetMs = targetPositionMs.coerceAtLeast(0L)
        priming = false
        generation++
        scheduledGeneration = -1L
        update {
            copy(
                active = true,
                targetPositionMs = pendingTargetMs,
                hasFrame = false,
                failed = false,
            )
        }
        startPendingRequest()
    }

    fun stopPreview() {
        if (released) return
        generation++
        scheduledGeneration = -1L
        handler.removeCallbacksAndMessages(null)
        runCatching { player.pause() }
        priming = false
        update { copy(active = false, hasFrame = false) }
    }

    fun clearMedia() {
        stopPreview()
        pendingMedia = null
        mediaKey = null
        softwareFallbackMediaKey = null
    }

    private fun startPendingRequest() {
        val media = pendingMedia ?: return
        if (layout == null || (!mutableState.value.active && !priming)) return
        runCatching {
            val key = previewMediaKey(media)
            if (key != mediaKey) {
                val item = Media(libVlc, media.uri)
                currentUsesHardwareDecoder = softwareFallbackMediaKey != key
                item.setHWDecoderEnabled(currentUsesHardwareDecoder, false)
                previewOptions(media).forEach(item::addOption)
                player.media = item
                item.release()
                mediaKey = key
                player.play()
            } else {
                seekToPendingTarget()
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to start preview at ${pendingTargetMs}ms", error)
            update { copy(hasFrame = false, failed = true) }
        }
    }

    private fun seekToPendingTarget() {
        if ((!mutableState.value.active && !priming) || released) return
        runCatching {
            player.setTime(pendingTargetMs, false)
            if (!player.isPlaying) player.play()
        }.onFailure { error ->
            Log.w(TAG, "Unable to seek preview to ${pendingTargetMs}ms", error)
            update { copy(hasFrame = false, failed = true) }
        }
    }

    private fun onPreviewTime(actualPositionMs: Long) {
        if (
            (!mutableState.value.active && !priming) ||
            abs(actualPositionMs - pendingTargetMs) > TARGET_TOLERANCE_MS
        ) return
        val currentGeneration = generation
        if (scheduledGeneration == currentGeneration) return
        scheduledGeneration = currentGeneration
        handler.postDelayed({
            if (
                !released &&
                currentGeneration == generation &&
                (mutableState.value.active || priming)
            ) {
                runCatching { player.pause() }
                if (mutableState.value.active) update { copy(hasFrame = true, failed = false) }
                priming = false
            }
        }, FRAME_SETTLE_MS)
    }

    private fun retryWithSoftwareDecoderOrFail() {
        val media = pendingMedia
        val key = media?.let(::previewMediaKey)
        if (media != null && key != null && currentUsesHardwareDecoder && softwareFallbackMediaKey != key) {
            Log.w(TAG, "Hardware preview failed; retrying with software decoder")
            softwareFallbackMediaKey = key
            mediaKey = null
            startPendingRequest()
            return
        }
        Log.w(TAG, "Preview decoder failed at ${pendingTargetMs}ms")
        priming = false
        update { copy(hasFrame = false, failed = true) }
    }

    override fun close() {
        if (released) return
        released = true
        handler.removeCallbacksAndMessages(null)
        runCatching { player.stop() }
        runCatching { player.detachViews() }
        layout = null
        player.release()
        libVlc.release()
    }

    private fun previewOptions(media: PlayerMedia): List<String> = buildList {
        addAll(media.options.filterNot {
            it.startsWith(":sub-file=") ||
                it.startsWith(":network-caching=") ||
                it.startsWith(":file-caching=") ||
                it.startsWith(":live-caching=")
        })
        add(":network-caching=$PREVIEW_CACHE_MS")
        add(":file-caching=$PREVIEW_CACHE_MS")
        add(":no-audio")
        add(":no-spu")
    }

    private fun previewMediaKey(media: PlayerMedia): String =
        media.uri.toString() + "\u0000" + previewOptions(media).joinToString("\u0000")

    private fun update(transform: VlcSeekPreviewState.() -> VlcSeekPreviewState) {
        mutableState.value = mutableState.value.transform()
    }

    private companion object {
        const val TAG = "VidBridgePreview"
        const val TARGET_TOLERANCE_MS = 500L
        const val FRAME_SETTLE_MS = 40L
        const val PREVIEW_CACHE_MS = 150
    }
}
