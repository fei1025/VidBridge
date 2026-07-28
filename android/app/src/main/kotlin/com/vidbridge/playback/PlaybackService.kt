package com.vidbridge.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vidbridge.VidBridgeApplication
import com.vidbridge.core.settings.BufferPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as VidBridgeApplication).container
        val dataSources = RoutingDataSourceFactory(this, RemoteDataSource.Factory(container.fileSystems))
        val preferences = runBlocking(Dispatchers.IO) { container.settings.preferences.first() }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
            .setLoadControl(loadControl(preferences.bufferPreset))
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    private fun loadControl(preset: BufferPreset): DefaultLoadControl {
        val durations = when (preset) {
            BufferPreset.LOW_LATENCY -> intArrayOf(10_000, 30_000, 500, 1_000)
            BufferPreset.BALANCED -> intArrayOf(30_000, 60_000, 1_500, 2_000)
            BufferPreset.STABLE -> intArrayOf(60_000, 120_000, 2_500, 5_000)
        }
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(durations[0], durations[1], durations[2], durations[3])
            .build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
