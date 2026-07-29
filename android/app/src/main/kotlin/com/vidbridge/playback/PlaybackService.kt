package com.vidbridge.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vidbridge.MainActivity
import com.vidbridge.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns the playback engine so playback can continue after the Activity leaves. */
class PlaybackService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = LocalBinder()
    lateinit var engine: LibVlcPlayerEngine
        private set
    private lateinit var session: android.media.session.MediaSession
    private var audioFocusRequest: AudioFocusRequest? = null
    private lateinit var audioFocusListener: AudioManager.OnAudioFocusChangeListener
    private lateinit var noisyReceiver: BroadcastReceiver
    private lateinit var networkManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var title: String = "VidBridge"
    val currentTitle: String get() = title
    private val sessionStore by lazy { PlaybackSessionStore(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        engine = LibVlcPlayerEngine(this)
        session = android.media.session.MediaSession(this, "VidBridge").apply {
            setCallback(object : android.media.session.MediaSession.Callback() {
                override fun onPlay() = engine.play()
                override fun onPause() = engine.pause()
                override fun onSeekTo(pos: Long) = engine.seekTo(pos)
                override fun onStop() = stopPlayback()
            })
            isActive = true
        }
        setupAudioFocus()
        setupNetworkRecovery()
        serviceScope.launch {
            engine.state.collectLatest { state ->
                updateNotification(state)
                PlaybackWidgetProvider.update(this@PlaybackService, title, state)
                if (state.playbackState == PlayerPlaybackState.ENDED) {
                    sessionStore.clear()
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
            }
        }
        serviceScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(1_000)
                engine.refreshPosition()
                sessionStore.updatePosition(engine.state.value.positionMs)
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification(engine.state.value))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: title
        when (intent?.action) {
            ACTION_PLAY -> engine.play()
            ACTION_PAUSE -> engine.pause()
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        engine.refreshPosition()
        sessionStore.updatePosition(engine.state.value.positionMs)
        super.onTaskRemoved(rootIntent)
    }

    fun prepare(
        media: PlayerMedia,
        mediaTitle: String,
        sourceId: String,
        path: String,
        localPath: String? = null,
        playlistId: String? = null,
    ) {
        title = mediaTitle.ifBlank { "VidBridge" }
        sessionStore.save(PlaybackSession(sourceId, path, title, media.startPositionMs, localPath, playlistId))
        engine.prepare(media)
        engine.play()
        updateNotification(engine.state.value)
    }

    fun stopPlayback() {
        engine.pause()
        sessionStore.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        runCatching { networkManager.unregisterNetworkCallback(networkCallback) }
        val audioManager = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
        session.isActive = false
        session.release()
        engine.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        fun service(): PlaybackService = this@PlaybackService
    }

    private fun updateNotification(state: PlayerState) {
        val manager = getSystemService(NotificationManager::class.java)
        val playbackState = when {
            state.playbackState == PlayerPlaybackState.PLAYING -> android.media.session.PlaybackState.STATE_PLAYING
            state.playbackState == PlayerPlaybackState.ENDED -> android.media.session.PlaybackState.STATE_NONE
            else -> android.media.session.PlaybackState.STATE_PAUSED
        }
        session.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title.ifBlank { "VidBridge" })
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, state.durationMs.coerceAtLeast(0L))
                .build(),
        )
        session.setPlaybackState(
            android.media.session.PlaybackState.Builder()
                .setActions(
                    android.media.session.PlaybackState.ACTION_PLAY or
                        android.media.session.PlaybackState.ACTION_PAUSE or
                        android.media.session.PlaybackState.ACTION_SEEK_TO or
                        android.media.session.PlaybackState.ACTION_STOP,
                )
                .setState(playbackState, state.positionMs, state.speed)
                .build(),
        )
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun setupAudioFocus() {
        val audioManager = getSystemService(AudioManager::class.java)
        audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) engine.pause()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build(),
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) engine.pause()
            }
        }
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    private fun setupNetworkRecovery() {
        networkManager = getSystemService(ConnectivityManager::class.java)
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (engine.state.value.playbackState == PlayerPlaybackState.ERROR) {
                    serviceScope.launch {
                        kotlinx.coroutines.delay(750)
                        engine.retry()
                    }
                }
            }
        }
        networkManager.registerDefaultNetworkCallback(networkCallback)
    }

    private fun buildNotification(state: PlayerState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val action = if (state.isPlaying) ACTION_PAUSE else ACTION_PLAY
        val actionIcon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val actionIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(if (state.isPlaying) "正在播放" else "已暂停")
            .setContentIntent(openIntent)
            .setOngoing(state.playbackState != PlayerPlaybackState.ENDED)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(actionIcon, if (state.isPlaying) "暂停" else "播放", actionIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", PendingIntent.getService(
                this,
                12,
                Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "播放", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val ACTION_PLAY = "com.vidbridge.playback.PLAY"
        const val ACTION_PAUSE = "com.vidbridge.playback.PAUSE"
        const val ACTION_STOP = "com.vidbridge.playback.STOP"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001
    }
}
