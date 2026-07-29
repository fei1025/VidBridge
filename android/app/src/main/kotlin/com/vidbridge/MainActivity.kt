package com.vidbridge

import android.Manifest
import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vidbridge.playback.PlaybackService
import com.vidbridge.playback.PlayerPlaybackState
import com.vidbridge.playback.PlayerState
import com.vidbridge.ui.*

class MainActivity : ComponentActivity() {
    var isPipMode by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        val container = (application as VidBridgeApplication).container
        setContent {
            val pip = isPipMode
            VidBridgeTheme { VidBridgeNav(container, pip) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 100
    }
}

@Composable
private fun VidBridgeNav(container: AppContainer, isPipMode: Boolean) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val isTv = remember(context) { context.packageManager.hasSystemFeature("android.software.leanback") }
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route.orEmpty()
    val playbackService = rememberPlaybackService()
    val playbackState by (playbackService?.engine?.state ?: kotlinx.coroutines.flow.MutableStateFlow(PlayerState()))
        .collectAsState()
    val session = remember { container.playbackSession.read() }
    val startDestination = remember(session) {
        session?.let {
            val playlist = it.playlistId?.let { id -> "&playlistId=${Uri.encode(id)}" }.orEmpty()
            "player/${it.sourceId}?path=${Uri.encode(it.path)}$playlist"
        } ?: "home"
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = startDestination, modifier = Modifier.fillMaxSize()) {
        composable("home") {
            HomeScreen(
                container = container,
                isTv = isTv,
                onSources = { nav.navigate("sources") },
                onSettings = { nav.navigate("settings") },
                onLibrary = { nav.navigate("library") },
                onOpen = { sourceId, path -> nav.navigate("player/$sourceId?path=${Uri.encode(path)}") },
                onDetails = { sourceId, path -> nav.navigate("details/$sourceId?path=${Uri.encode(path)}") },
            )
        }
        composable("sources") {
            SourcesScreen(
                container = container,
                onAdd = { nav.navigate("sources/add") },
                onEdit = { nav.navigate("sources/edit/$it") },
                onSettings = { nav.navigate("settings") },
                onLibrary = { nav.navigate("library") },
                onBrowse = { nav.navigate("browser/$it") },
                onHome = { nav.navigate("home") },
            )
        }
        composable("settings") {
            SettingsScreen(container) { nav.popBackStack() }
        }
        composable("library") {
            LibraryScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onPlaylists = { nav.navigate("playlists") },
                onDownloads = { nav.navigate("downloads") },
                isTv = isTv,
                onOpen = { sourceId, path, isDirectory ->
                    if (isDirectory) {
                        nav.navigate("browser/$sourceId?path=${Uri.encode(path)}")
                    } else {
                        nav.navigate("player/$sourceId?path=${Uri.encode(path)}")
                    }
                },
                onDetails = { sourceId, path ->
                    nav.navigate("details/$sourceId?path=${Uri.encode(path)}")
                },
            )
        }
        composable("playlists") {
            PlaylistScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onPlay = { sourceId, path, playlistId -> nav.navigate("player/$sourceId?path=${Uri.encode(path)}&playlistId=${Uri.encode(playlistId)}") },
            )
        }
        composable("downloads") {
            DownloadsScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onPlay = { nav.navigate("download-player/$it") },
            )
        }
        composable(
            route = "download-player/{downloadId}",
            arguments = listOf(navArgument("downloadId") { type = NavType.StringType }),
        ) { backStack ->
            DownloadedPlayerScreen(
                container = container,
                downloadId = requireNotNull(backStack.arguments?.getString("downloadId")),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "details/{sourceId}?path={path}",
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType },
            ),
        ) { backStack ->
            MediaDetailsScreen(
                container = container,
                sourceId = requireNotNull(backStack.arguments?.getString("sourceId")),
                path = requireNotNull(backStack.arguments?.getString("path")),
                onBack = { nav.popBackStack() },
                onPlay = { sourceId, path -> nav.navigate("player/$sourceId?path=${Uri.encode(path)}") },
            )
        }
        composable("sources/add") {
            AddSourceScreen(container) { nav.popBackStack() }
        }
        composable(
            route = "sources/edit/{sourceId}",
            arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
        ) { backStack ->
            AddSourceScreen(
                container = container,
                sourceId = requireNotNull(backStack.arguments?.getString("sourceId")),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "browser/{sourceId}?path={path}",
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStack ->
            val sourceId = requireNotNull(backStack.arguments?.getString("sourceId"))
            BrowserScreen(
                container = container,
                sourceId = sourceId,
                initialPath = backStack.arguments?.getString("path"),
                onBack = { nav.popBackStack() },
                onPlay = { entry ->
                    nav.navigate("player/$sourceId?path=${Uri.encode(entry.path.value)}")
                },
            )
        }
        composable(
            route = "player/{sourceId}?path={path}&playlistId={playlistId}",
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType },
                navArgument("playlistId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStack ->
            PlayerScreen(
                container = container,
                sourceId = requireNotNull(backStack.arguments?.getString("sourceId")),
                path = requireNotNull(backStack.arguments?.getString("path")),
                playlistId = backStack.arguments?.getString("playlistId"),
                onBack = { nav.popBackStack() },
            )
        }
        }
        val showMiniPlayer = !isPipMode &&
            !currentRoute.startsWith("player/") &&
            playbackService != null &&
            playbackState.playbackState !in setOf(PlayerPlaybackState.IDLE, PlayerPlaybackState.ENDED, PlayerPlaybackState.RELEASED)
        if (showMiniPlayer) {
            MiniPlayer(
                title = playbackService.currentTitle,
                state = playbackState,
                onOpen = { nav.navigate("player/${container.playbackSession.read()?.sourceId}?path=${Uri.encode(container.playbackSession.read()?.path.orEmpty())}") },
                onToggle = { if (playbackState.isPlaying) playbackService.engine.pause() else playbackService.engine.play() },
                onClose = { playbackService.stopPlayback() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun rememberPlaybackService(): PlaybackService? {
    val context = androidx.compose.ui.platform.LocalContext.current
    var service by remember { mutableStateOf<PlaybackService?>(null) }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? PlaybackService.LocalBinder)?.service()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val bound = context.bindService(Intent(context, PlaybackService::class.java), connection, Context.BIND_NOT_FOREGROUND)
        onDispose {
            if (bound) context.unbindService(connection)
            service = null
        }
    }
    return service
}

@Composable
private fun MiniPlayer(
    title: String,
    state: PlayerState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(title.ifBlank { "正在播放" }, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            IconButton(onClick = onToggle) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "播放/暂停") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "停止") }
        }
    }
}
