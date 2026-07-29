package com.vidbridge.ui

import android.graphics.Bitmap
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.IBinder
import android.os.Build
import android.util.Rational
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.VLCVideoLayout
import com.vidbridge.AppContainer
import com.vidbridge.core.settings.*
import com.vidbridge.core.diagnostics.CrashReporter
import com.vidbridge.core.database.ContinueWatchingRow
import com.vidbridge.core.database.FavoriteItemRow
import com.vidbridge.core.database.LibraryItemRow
import com.vidbridge.core.database.PlaybackQueueRow
import com.vidbridge.core.database.MediaVersionRow
import com.vidbridge.core.database.PlaylistEntity
import com.vidbridge.core.database.PlaylistItemRow
import com.vidbridge.core.database.DownloadEntity
import com.vidbridge.core.database.DownloadStatus
import com.vidbridge.core.database.ScanJobEntity
import com.vidbridge.protocol.api.MediaSourceConfig
import com.vidbridge.protocol.api.MediaSourceType
import com.vidbridge.protocol.api.PageRequest
import com.vidbridge.protocol.api.RemoteEntry
import com.vidbridge.protocol.api.RemotePath
import com.vidbridge.playback.LibVlcPlayerEngine
import com.vidbridge.playback.PlayerMedia
import com.vidbridge.playback.PlayerState
import com.vidbridge.playback.PlayerTrack
import com.vidbridge.playback.PlayerVideoScale
import com.vidbridge.playback.PlaybackService
import com.vidbridge.playback.localRecoveryPath
import com.vidbridge.playback.PlaybackSourceResolver
import kotlinx.coroutines.flow.emptyFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    isTv: Boolean = false,
    onSources: () -> Unit,
    onSettings: () -> Unit,
    onLibrary: () -> Unit,
    onOpen: (String, String) -> Unit,
    onDetails: (String, String) -> Unit,
) {
    val vm: LibraryViewModel = viewModel(factory = SimpleViewModelFactory { LibraryViewModel(container.mediaLibrary) })
    val state by vm.state.collectAsStateWithLifecycle()
    val recentItems = state.items
        .groupBy { item -> if (item.kind == "EPISODE") item.groupKey.ifBlank { item.mediaKey } else item.mediaKey }
        .values
        .map { group ->
            val first = group.maxByOrNull { it.modifiedAtEpochMs ?: Long.MIN_VALUE } ?: group.first()
            if (first.kind == "EPISODE") first.copy(title = first.groupTitle.ifBlank { first.title }) else first
        }
        .sortedWith(compareByDescending<LibraryItemRow> { it.modifiedAtEpochMs ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        .take(20)
    val tvInitialFocusKey = if (!isTv) null else state.continueWatching.firstOrNull()
        ?.let { "continue:${it.sourceId}:${it.path}" }
        ?: recentItems.firstOrNull()?.mediaKey
    val tvInitialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(tvInitialFocusKey) {
        if (isTv && tvInitialFocusKey != null) {
            delay(120)
            runCatching { tvInitialFocusRequester.requestFocus() }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VidBridge") },
                actions = {
                    IconButton(onClick = onSources) { Icon(Icons.Default.Storage, "来源") }
                    IconButton(onClick = onLibrary) { Icon(Icons.Default.VideoLibrary, "媒体库") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                },
            )
        },
    ) { padding ->
        if (state.items.isEmpty() && state.continueWatching.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Movie, null, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("媒体库还没有内容", style = MaterialTheme.typography.titleLarge)
                    Text("先添加来源并扫描媒体库")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onSources) { Text("管理媒体来源") }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.continueWatching.isNotEmpty()) {
                    item { HomeSectionTitle("继续观看", onClick = onLibrary) }
                    item {
                            LazyRow(contentPadding = PaddingValues(horizontal = if (isTv) 48.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp)) {
                            items(state.continueWatching, key = { "continue:${it.sourceId}:${it.path}" }) { item ->
                                ContinueHomeCard(
                                    item = item,
                                    container = container,
                                    focusRequester = if (tvInitialFocusKey == "continue:${item.sourceId}:${item.path}") tvInitialFocusRequester else null,
                                ) { onOpen(item.sourceId, item.path) }
                            }
                        }
                    }
                }
                if (state.items.isNotEmpty()) {
                    item { HomeSectionTitle("最近媒体", onClick = onLibrary) }
                    item {
                            LazyRow(contentPadding = PaddingValues(horizontal = if (isTv) 48.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp)) {
                            items(recentItems, key = { it.mediaKey }) { item ->
                                MediaHomeCard(
                                    item = item,
                                    container = container,
                                    focusRequester = if (tvInitialFocusKey == item.mediaKey) tvInitialFocusRequester else null,
                                    onDetails = { onDetails(item.sourceId, item.path) },
                                    onPlay = { onOpen(item.sourceId, item.path) },
                                )
                            }
                        }
                    }
                }
                if (state.favorites.isNotEmpty()) {
                    item { HomeSectionTitle("收藏", onClick = onLibrary) }
                    item {
                            LazyRow(contentPadding = PaddingValues(horizontal = if (isTv) 48.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp)) {
                            items(state.favorites.take(20), key = { "favorite:${it.mediaKey}" }) { item ->
                                Card(onClick = { if (!item.isDirectory) onOpen(item.sourceId, item.path) }) {
                                    Column(Modifier.width(180.dp)) {
                                        if (item.isDirectory) {
                                            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Folder, null, Modifier.size(48.dp))
                                            }
                                        } else {
                                            ArtworkThumb(container, item.sourceId, item.artworkPath, item.name, Modifier.fillMaxWidth().height(150.dp))
                                        }
                                        Column(Modifier.padding(12.dp)) {
                                        Text(item.name, maxLines = 2)
                                        Text(item.sourceName, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text("查看全部") }
    }
}

@Composable
private fun ContinueHomeCard(
    item: ContinueWatchingRow,
    container: AppContainer,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.width(240.dp)
            .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .border(if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Transparent), RectangleShape),
        ) {
            Column(Modifier.padding(14.dp)) {
            ArtworkThumb(container, item.sourceId, item.artworkPath, item.title, Modifier.fillMaxWidth().height(135.dp))
            Spacer(Modifier.height(8.dp))
            Text(item.title, maxLines = 2, style = MaterialTheme.typography.titleMedium)
            Text(item.sourceName, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MediaHomeCard(
    item: LibraryItemRow,
    container: AppContainer,
    focusRequester: FocusRequester? = null,
    onDetails: () -> Unit,
    onPlay: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onDetails,
        modifier = Modifier.width(170.dp)
            .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .border(if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Transparent), RectangleShape),
    ) {
        Column {
            ArtworkThumb(container, item.sourceId, item.artworkPath, item.title, Modifier.fillMaxWidth().height(190.dp))
            Column(Modifier.padding(10.dp)) {
                Text(item.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                Text(if (item.kind == "EPISODE") "S%02dE%02d · 自动连播".format(item.season ?: 0, item.episode ?: 0) else item.sourceName, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onPlay, contentPadding = PaddingValues(0.dp)) { Text(if (item.kind == "EPISODE") "播放剧集" else "播放") }
                PlaybackProgress(item.watchedPositionMs, item.watchedDurationMs)
            }
        }
    }
}

@Composable
private fun ArtworkThumb(container: AppContainer, sourceId: String, path: String?, title: String, modifier: Modifier) {
    var bitmap by remember(sourceId, path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(sourceId, path) {
        bitmap = path?.let { container.artwork.load(sourceId, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } ?: Icon(Icons.Default.Movie, "海报占位", Modifier.size(48.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    container: AppContainer,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSettings: () -> Unit,
    onLibrary: () -> Unit,
    onBrowse: (String) -> Unit,
    onHome: () -> Unit,
) {
    val vm: SourcesViewModel = viewModel(factory = SimpleViewModelFactory { SourcesViewModel(container.sources, container.fileSystems, container.mediaLibrary, container.downloads, container.playbackHistory, container.playlists, container.playbackSession) })
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VidBridge") },
                actions = {
                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, "首页") }
                    IconButton(onClick = onLibrary) { Icon(Icons.Default.VideoLibrary, "媒体库") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("添加来源") },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.sources.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storage, null, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("还没有媒体来源", style = MaterialTheme.typography.titleLarge)
                    Text("添加本地、SMB、NFS、WebDAV、SFTP、Jellyfin、Emby 或 Plex 来源")
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.sources, key = { it.id }) { source ->
                    SourceCard(source, state.scanJobs[source.id], { onBrowse(source.id) }, { onEdit(source.id) }, { vm.test(source) }, { vm.scan(source) }, { vm.cancelScan(source) }, { vm.delete(source) })
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: MediaSourceConfig,
    scanJob: ScanJobEntity?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val location = when (source.type) {
        MediaSourceType.LOCAL -> source.rootUri.orEmpty()
        MediaSourceType.SMB -> listOfNotNull(
            "${source.endpoint.host}:${source.endpoint.port}",
            source.shareName?.takeIf { it.isNotBlank() },
        ).joinToString("/")
        MediaSourceType.WEBDAV -> "${source.endpoint.scheme}://${source.endpoint.host}:${source.endpoint.port}/${source.rootPath}"
        else -> "${source.endpoint.host}:${source.endpoint.port}"
    }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (source.type == MediaSourceType.LOCAL) Icons.Default.PhoneAndroid else Icons.Default.Dns, null, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(source.displayName, style = MaterialTheme.typography.titleMedium)
                Text("${source.type.name} · $location", style = MaterialTheme.typography.bodyMedium)
                scanJob?.let { Text(scanStatus(it), style = MaterialTheme.typography.bodySmall) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { menu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("测试连接") },
                        onClick = { menu = false; onTest() },
                        leadingIcon = { Icon(Icons.Default.WifiFind, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (scanJob?.status in setOf("RUNNING", "RETRYING")) "取消扫描" else "扫描媒体库") },
                        onClick = {
                            menu = false
                            if (scanJob?.status in setOf("RUNNING", "RETRYING")) onCancelScan() else onScan()
                        },
                        leadingIcon = { Icon(Icons.Default.Sync, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { menu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceScreen(container: AppContainer, sourceId: String? = null, onBack: () -> Unit) {
    val vm: SourceFormViewModel = viewModel(
        key = sourceId ?: "new-source",
        factory = SimpleViewModelFactory { SourceFormViewModel(container.sources, sourceId) },
    )
    val draft by vm.draft.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val dlnaDevices by vm.dlnaDevices.collectAsStateWithLifecycle()
    val discoveringDlna by vm.discoveringDlna.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment.orEmpty()
            vm.setLocalTree(uri.toString(), name)
        }
    }
    LaunchedEffect(Unit) { vm.saved.collect { onBack() } }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (sourceId == null) "添加媒体来源" else "编辑媒体来源") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = draft.type == MediaSourceType.LOCAL,
                        onClick = { vm.setType(MediaSourceType.LOCAL) },
                        label = { Text("本地") },
                    )
                    FilterChip(
                        selected = draft.type == MediaSourceType.SMB,
                        onClick = { vm.setType(MediaSourceType.SMB) },
                        label = { Text("SMB") },
                    )
                    FilterChip(
                        selected = draft.type == MediaSourceType.NFS,
                        onClick = { vm.setType(MediaSourceType.NFS) },
                        label = { Text("NFS") },
                    )
                    FilterChip(
                        selected = draft.type == MediaSourceType.WEBDAV,
                        onClick = { vm.setType(MediaSourceType.WEBDAV) },
                        label = { Text("WebDAV") },
                    )
                    FilterChip(
                        selected = draft.type == MediaSourceType.SFTP,
                        onClick = { vm.setType(MediaSourceType.SFTP) },
                        label = { Text("SFTP") },
                    )
                    FilterChip(
                        selected = draft.type == MediaSourceType.JELLYFIN,
                        onClick = { vm.setType(MediaSourceType.JELLYFIN) },
                        label = { Text("Jellyfin") },
                    )
                    FilterChip(
                        selected = draft.type == MediaSourceType.EMBY,
                        onClick = { vm.setType(MediaSourceType.EMBY) },
                        label = { Text("Emby") },
                    )
                    FilterChip(
                        selected = draft.type == MediaSourceType.PLEX,
                        onClick = { vm.setType(MediaSourceType.PLEX) },
                        label = { Text("Plex") },
                    )
                    FilterChip(
                        selected = draft.type == MediaSourceType.DLNA,
                        onClick = { vm.setType(MediaSourceType.DLNA) },
                        label = { Text("DLNA") },
                    )
                }
            }
            item { FormField("名称", draft.displayName) { vm.update { d -> d.copy(displayName = it) } } }
            if (draft.type == MediaSourceType.LOCAL) {
                item {
                    OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (draft.rootUri == null) "选择本地文件夹" else "重新选择文件夹")
                    }
                }
                draft.rootUri?.let { uri -> item { Text(uri, style = MaterialTheme.typography.bodySmall) } }
            } else {
                item { FormField("主机", draft.host) { vm.update { d -> d.copy(host = it) } } }
                if (draft.type == MediaSourceType.DLNA) {
                    item {
                        OutlinedButton(
                            onClick = vm::discoverDlna,
                            enabled = !discoveringDlna,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (discoveringDlna) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.Wifi, null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (discoveringDlna) "正在扫描局域网设备…" else "扫描局域网 DLNA 设备")
                        }
                    }
                    items(dlnaDevices, key = { it.location }) { device ->
                        ListItem(
                            headlineContent = { Text(device.server ?: device.location) },
                            supportingContent = { Text(device.location) },
                            modifier = Modifier.clickable { vm.selectDlna(device) },
                        )
                    }
                }
                if (draft.type == MediaSourceType.WEBDAV) {
                    item {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("WebDAV 使用 HTTPS") },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                        )
                    }
                }
                if (draft.type == MediaSourceType.NFS) {
                    item {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("NFSv3 · AUTH_SYS 只读") },
                            leadingIcon = { Icon(Icons.Default.Storage, null) },
                        )
                    }
                }
                if (draft.type == MediaSourceType.JELLYFIN || draft.type == MediaSourceType.EMBY || draft.type == MediaSourceType.PLEX) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(draft.tls, onCheckedChange = vm::setTls)
                            Text("使用 HTTPS", Modifier.padding(start = 12.dp))
                        }
                    }
                }
                item { FormField("端口", draft.port, KeyboardType.Number) { vm.update { d -> d.copy(port = it) } } }
                if (draft.type == MediaSourceType.SMB) {
                    item {
                        FormField("共享名称（可选，留空自动发现）", draft.shareName) {
                            vm.update { d -> d.copy(shareName = it) }
                        }
                    }
                }
                item {
                    FormField(
                        when {
                            draft.type == MediaSourceType.NFS -> "NFS 导出路径（必填，例如 /volume1/video）"
                            draft.type == MediaSourceType.WEBDAV -> "WebDAV 根路径（可选）"
                            draft.type == MediaSourceType.SFTP -> "SFTP 根路径（可选）"
                            draft.type == MediaSourceType.JELLYFIN || draft.type == MediaSourceType.EMBY || draft.type == MediaSourceType.PLEX -> "API 根路径（可选）"
                            draft.type == MediaSourceType.DLNA -> "设备描述地址（可选，例如 http://192.168.1.20:8200/device.xml）"
                            draft.shareName.isBlank() -> "初始路径（可选，首段为共享名）"
                            else -> "共享内初始目录（可选）"
                        },
                        draft.rootPath,
                    ) { vm.update { d -> d.copy(rootPath = it) } }
                }
                if (draft.type != MediaSourceType.NFS && draft.type != MediaSourceType.SFTP && draft.type != MediaSourceType.JELLYFIN && draft.type != MediaSourceType.EMBY && draft.type != MediaSourceType.PLEX && draft.type != MediaSourceType.DLNA) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(draft.anonymous, onCheckedChange = { value -> vm.update { it.copy(anonymous = value) } })
                        Text("免密码/来宾访问", Modifier.padding(start = 12.dp))
                    }
                }
                if (draft.type == MediaSourceType.JELLYFIN || draft.type == MediaSourceType.EMBY || draft.type == MediaSourceType.PLEX) {
                    item {
                        OutlinedTextField(
                            value = draft.password,
                            onValueChange = { value -> vm.update { it.copy(password = value) } },
                            label = { Text("API Token") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                } else if (!draft.anonymous) {
                    item { FormField("用户名", draft.username) { vm.update { d -> d.copy(username = it) } } }
                    item {
                        OutlinedTextField(
                            value = draft.password,
                            onValueChange = { value -> vm.update { it.copy(password = value) } },
                            label = { Text("密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item { Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text("保存") } }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    container: AppContainer,
    sourceId: String,
    initialPath: String? = null,
    onBack: () -> Unit,
    onPlay: (RemoteEntry) -> Unit,
) {
    val vm: BrowserViewModel = viewModel(
        key = "$sourceId:${initialPath.orEmpty()}",
        factory = SimpleViewModelFactory { BrowserViewModel(sourceId, container.fileSystems, container.sources, container.settings, container.mediaLibrary, initialPath) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler { if (!vm.up()) onBack() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(state.path.value.ifEmpty { "共享目录" }, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = { if (!vm.up()) onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            },
            actions = {
                IconButton(onClick = { vm.open(state.path) }) { Icon(Icons.Default.Refresh, "刷新") }
            },
        )
    }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.open(state.path) }) { Text("重试") }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.entries, key = { it.path.value }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.name) },
                        supportingContent = { if (!entry.isDirectory) Text(formatBytes(entry.size)) },
                        leadingContent = { Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Movie, null) },
                        trailingContent = {
                            val favorite = vm.favoriteKey(entry) in state.favoriteKeys
                            IconButton(onClick = { vm.toggleFavorite(entry) }) {
                                Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, if (favorite) "取消收藏" else "收藏")
                            }
                        },
                        modifier = Modifier.clickable {
                            if (entry.isDirectory) vm.open(entry.path) else if (isVideo(entry.name)) onPlay(entry)
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long?): String = when {
    bytes == null -> ""
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "${bytes / 1024} KB"
}

private fun isVideo(name: String) = name.substringAfterLast('.', "").lowercase() in
    setOf("mp4", "mkv", "avi", "mov", "ts", "m2ts", "webm", "mpeg", "mpg", "m4v")

private fun scanStatus(job: ScanJobEntity): String = when (job.status) {
    "RUNNING" -> "正在扫描：${job.scannedEntries} 项，${job.scannedMedia} 个视频"
    "RETRYING" -> "连接中断，等待重试 · 已发现 ${job.scannedMedia} 个视频"
    "SUCCESS" -> "媒体库已更新 · ${job.scannedMedia} 个视频"
    "CANCELED" -> "扫描已取消"
    else -> "扫描失败：${job.errorMessage.orEmpty()}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onPlaylists: () -> Unit,
    onDownloads: () -> Unit,
    isTv: Boolean = false,
    onOpen: (String, String, Boolean) -> Unit,
    onDetails: (String, String) -> Unit,
) {
    val vm: LibraryViewModel = viewModel(factory = SimpleViewModelFactory { LibraryViewModel(container.mediaLibrary) })
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.preferences.collectAsStateWithLifecycle(initialValue = PlayerPreferences())
    val scope = rememberCoroutineScope()
    val tmdbAttempted = remember { mutableSetOf<String>() }
    LaunchedEffect(settings.tmdbApiKey, state.items) {
        val apiKey = settings.tmdbApiKey
        if (apiKey.isNotBlank()) {
            state.items
                .filter { it.kind != "VIDEO" }
                .groupBy { it.groupKey.ifBlank { it.mediaKey } }
                .values
                .mapNotNull { it.firstOrNull() }
                .take(60)
                .forEach { item ->
                    if (tmdbAttempted.add(item.mediaKey)) container.tmdb.enrich(item, apiKey)
                }
        }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var mediaFilter by rememberSaveable { mutableIntStateOf(0) }
    var sortMode by rememberSaveable { mutableIntStateOf(0) }
    var selectedLibraryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearHistory by rememberSaveable { mutableStateOf(false) }
    val filteredItems = when (mediaFilter) {
        1 -> state.items.filter { it.kind == "MOVIE" }
        2 -> state.items.filter { it.kind == "EPISODE" }
        3 -> state.items.filter { it.kind == "VIDEO" }
        else -> state.items
    }
    val visibleItems = filteredItems
        .let { items ->
            if (mediaFilter == 2) {
                items.groupBy { it.groupKey.ifBlank { it.mediaKey } }
                    .values
                    .map { group ->
                        val first = group.minWithOrNull(compareBy<LibraryItemRow> { it.season ?: Int.MAX_VALUE }.thenBy { it.episode ?: Int.MAX_VALUE })
                            ?: group.first()
                        first.copy(title = first.groupTitle.ifBlank { first.title })
                    }
            } else items
        }
        .let { items ->
            when (sortMode) {
                1 -> items.sortedWith(compareBy<LibraryItemRow> { it.year ?: Int.MAX_VALUE }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                2 -> items.sortedWith(compareByDescending<LibraryItemRow> { it.modifiedAtEpochMs ?: Long.MIN_VALUE }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                3 -> items.sortedWith(compareByDescending<LibraryItemRow> { it.size ?: Long.MIN_VALUE }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                else -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            }
        }
    val query = state.query.trim()
    val visibleContinueWatching = state.continueWatching.filter { item ->
        query.isBlank() || listOf(item.title, item.sourceName, item.path).any { it.contains(query, ignoreCase = true) }
    }
    val visibleFavorites = state.favorites.filter { item ->
        query.isBlank() || listOf(item.name, item.sourceName, item.path).any { it.contains(query, ignoreCase = true) }
    }
    val visibleHistory = state.recentHistory.filter { item ->
        query.isBlank() || listOf(item.title, item.sourceName, item.path).any { it.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("媒体库") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = onDownloads) { Icon(Icons.Default.Download, "下载") }
                    IconButton(onClick = onPlaylists) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "播放列表") }
                    if (state.recentHistory.isNotEmpty()) {
                        IconButton(onClick = { showClearHistory = true }) {
                            Icon(Icons.Default.DeleteSweep, "清空播放历史")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::search,
                label = { Text("搜索标题、演员或路径") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 16.dp)) {
                listOf("全部", "继续观看", "收藏", "最近播放").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 4),
                    ) { Text(label) }
                }
            }
            if (selectedTab == 0) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("全部", "电影", "剧集", "视频").forEachIndexed { index, label ->
                        FilterChip(
                            selected = mediaFilter == index,
                            onClick = { mediaFilter = index },
                            label = { Text(label) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("名称", "年份", "最近更新", "文件大小").forEachIndexed { index, label ->
                        FilterChip(
                            selected = sortMode == index,
                            onClick = { sortMode = index },
                            label = { Text("排序：$label") },
                        )
                    }
                }
            }
            when (selectedTab) {
                1 -> if (visibleContinueWatching.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有可继续观看的内容") }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(visibleContinueWatching, key = { "${it.sourceId}:${it.path}" }) { item ->
                            ContinueWatchingItem(
                                item = item,
                                container = container,
                                onPlay = { onOpen(item.sourceId, item.path, false) },
                            )
                        }
                    }
                }
                2 -> if (visibleFavorites.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有收藏的视频或文件夹") }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(visibleFavorites, key = { it.mediaKey }) { item ->
                            FavoriteItem(item) { onOpen(item.sourceId, item.path, item.isDirectory) }
                        }
                    }
                }
                0 -> if (visibleItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("媒体库为空，请先从来源菜单启动扫描")
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val tabletLayout = !isTv && maxWidth >= 600.dp
                        val selectedItem = visibleItems.firstOrNull { it.mediaKey == selectedLibraryKey }
                            ?: visibleItems.first()
                        LaunchedEffect(selectedItem.mediaKey) { selectedLibraryKey = selectedItem.mediaKey }
                        if (tabletLayout) {
                            Row(Modifier.fillMaxSize()) {
                                LibraryGrid(
                                    items = visibleItems,
                                    container = container,
                                    columns = GridCells.Adaptive(160.dp),
                                    contentPadding = PaddingValues(12.dp),
                                    modifier = Modifier.weight(1.65f).fillMaxHeight(),
                                    onCardClick = { item -> selectedLibraryKey = item.mediaKey },
                                    onPlay = { item -> onOpen(item.sourceId, item.path, false) },
                                    onFavorite = vm::toggleFavorite,
                                )
                                LibraryPreviewPane(
                                    media = selectedItem,
                                    container = container,
                                    modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 12.dp, end = 12.dp, bottom = 12.dp),
                                    onDetails = { onDetails(selectedItem.sourceId, selectedItem.path) },
                                    onPlay = { onOpen(selectedItem.sourceId, selectedItem.path, false) },
                                )
                            }
                        } else {
                            LibraryGrid(
                                items = visibleItems,
                                container = container,
                                columns = if (isTv) GridCells.Fixed(5) else GridCells.Adaptive(150.dp),
                                contentPadding = PaddingValues(if (isTv) 32.dp else 12.dp),
                                modifier = Modifier.fillMaxSize(),
                                onCardClick = { item -> onDetails(item.sourceId, item.path) },
                                onPlay = { item -> onOpen(item.sourceId, item.path, false) },
                                onFavorite = vm::toggleFavorite,
                            )
                        }
                    }
                }
                3 -> if (visibleHistory.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有播放记录") }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(visibleHistory, key = { "history:${it.sourceId}:${it.path}" }) { item ->
                            ContinueWatchingItem(
                                item = item,
                                container = container,
                                onPlay = { onOpen(item.sourceId, item.path, false) },
                                onClear = { scope.launch { container.playbackHistory.clear(item.sourceId, item.path) } },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text("清空播放历史？") },
            text = { Text("只清除播放进度和最近播放记录，不会删除媒体文件、收藏、下载或播放列表。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { container.playbackHistory.clearAll() }
                        showClearHistory = false
                    },
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LibraryGrid(
    items: List<LibraryItemRow>,
    container: AppContainer,
    columns: GridCells,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onCardClick: (LibraryItemRow) -> Unit,
    onPlay: (LibraryItemRow) -> Unit,
    onFavorite: (LibraryItemRow) -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        gridItems(items, key = { it.mediaKey }) { item ->
            var focused by remember { mutableStateOf(false) }
            Card(
                onClick = { onCardClick(item) },
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .border(if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Transparent), RectangleShape),
            ) {
                Column {
                    ArtworkThumb(container, item.sourceId, item.artworkPath, item.title, Modifier.fillMaxWidth().height(190.dp))
                    Column(Modifier.padding(10.dp)) {
                        Text(item.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (item.kind == "EPISODE") "${item.sourceName} · S%02dE%02d · 自动连播".format(item.season ?: 0, item.episode ?: 0)
                            else item.sourceName,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = { onPlay(item) }) {
                                Text(if (item.kind == "EPISODE") "播放剧集" else "播放")
                            }
                            IconButton(onClick = { onFavorite(item) }) {
                                Icon(if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder, "收藏")
                            }
                        }
                        PlaybackProgress(item.watchedPositionMs, item.watchedDurationMs)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPreviewPane(
    media: LibraryItemRow,
    container: AppContainer,
    modifier: Modifier,
    onDetails: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(modifier = modifier) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ArtworkThumb(container, media.sourceId, media.backdropPath ?: media.artworkPath, media.title, Modifier.fillMaxWidth().height(220.dp))
            }
            item { Text(media.title, style = MaterialTheme.typography.headlineSmall) }
            item {
                Text(
                    buildString {
                        append(media.sourceName)
                        media.year?.let { append(" · ").append(it) }
                        if (media.kind == "EPISODE") append(" · S%02dE%02d".format(media.season ?: 0, media.episode ?: 0))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            media.rating?.let { rating -> item { Text("评分：%.1f".format(rating)) } }
            media.director?.takeIf(String::isNotBlank)?.let { director -> item { Text("导演：$director") } }
            media.plot?.takeIf(String::isNotBlank)?.let { plot -> item { Text(plot) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPlay) { Text("播放") }
                    OutlinedButton(onClick = onDetails) { Text("打开详情") }
                }
            }
        }
    }
}

@Composable
private fun LibraryItem(
    item: LibraryItemRow,
    onDetails: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = {
            Column {
                Text(item.sourceName + if (item.kind == "EPISODE") " · S%02dE%02d".format(item.season, item.episode) else "")
                Text(formatBytes(item.size))
            }
        },
        leadingContent = { Icon(Icons.Default.Movie, null) },
        trailingContent = {
            Row {
                IconButton(onClick = onDetails) { Icon(Icons.Default.Info, "详情") }
                IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, "播放") }
                IconButton(onClick = onFavorite) {
                    Icon(if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder, if (item.favorite) "取消收藏" else "收藏")
                }
            }
        },
        modifier = Modifier.clickable(onClick = onDetails),
    )
    HorizontalDivider()
}

@Composable
private fun ContinueWatchingItem(
    item: ContinueWatchingRow,
    container: AppContainer,
    onPlay: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val progress = if (item.durationMs > 0) item.positionMs.toFloat() / item.durationMs else 0f
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = {
            Column {
                Text(item.sourceName)
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        },
        leadingContent = {
            ArtworkThumb(container, item.sourceId, item.artworkPath, item.title, Modifier.size(72.dp))
        },
        trailingContent = {
            onClear?.let {
                IconButton(onClick = it) { Icon(Icons.Default.DeleteOutline, "清除记录") }
            }
        },
        modifier = Modifier.clickable(onClick = onPlay),
    )
    HorizontalDivider()
}
@Composable
private fun FavoriteItem(item: FavoriteItemRow, onOpen: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = { Text(item.sourceName) },
        leadingContent = { Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.Movie, null) },
        trailingContent = { Icon(Icons.Default.Star, "已收藏") },
        modifier = Modifier.clickable(onClick = onOpen),
    )
    HorizontalDivider()
}

@Composable
private fun PlaybackProgress(positionMs: Long?, durationMs: Long?) {
    if (positionMs == null || durationMs == null || durationMs <= 0L || positionMs <= 0L) return
    LinearProgressIndicator(
        progress = { (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onPlay: (String, String, String) -> Unit,
) {
    val vm: PlaylistViewModel = viewModel(factory = SimpleViewModelFactory { PlaylistViewModel(container.playlists) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var deletePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放列表") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "新建播放列表") } },
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.widthIn(min = 150.dp, max = 230.dp).fillMaxHeight(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.playlists.isEmpty()) {
                    item { Text("还没有播放列表", style = MaterialTheme.typography.bodySmall) }
                }
                items(state.playlists, key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name, maxLines = 2) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = {
                                        renamePlaylistId = playlist.id
                                        renameName = playlist.name
                                    },
                                ) { Icon(Icons.Default.Edit, "重命名播放列表") }
                                IconButton(onClick = { deletePlaylistId = playlist.id }) {
                                    Icon(Icons.Default.DeleteOutline, "删除播放列表")
                                }
                            }
                        },
                        modifier = Modifier.clickable { vm.select(playlist.id) },
                        colors = ListItemDefaults.colors(
                            containerColor = if (playlist.id == state.selectedId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
            VerticalDivider()
            if (state.selectedId == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("新建一个播放列表，收藏想连续观看的内容") }
            } else if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("列表为空，可在媒体详情中加入视频") }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                    items(state.items, key = { "${it.playlistId}:${it.mediaKey}" }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title, maxLines = 2) },
                            supportingContent = { Text(item.path, maxLines = 1) },
                            leadingContent = { Text("${item.position + 1}") },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { vm.moveUp(item.playlistId, item.mediaKey) },
                                        enabled = item.position > (state.items.minOfOrNull { it.position } ?: item.position),
                                    ) { Icon(Icons.Default.KeyboardArrowUp, "上移") }
                                    IconButton(
                                        onClick = { vm.moveDown(item.playlistId, item.mediaKey) },
                                        enabled = item.position < (state.items.maxOfOrNull { it.position } ?: item.position),
                                    ) { Icon(Icons.Default.KeyboardArrowDown, "下移") }
                                    IconButton(onClick = { onPlay(item.sourceId, item.path, item.playlistId) }) { Icon(Icons.Default.PlayArrow, "播放") }
                                    IconButton(onClick = { vm.remove(item.playlistId, item.mediaKey) }) { Icon(Icons.Default.Delete, "移除") }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建播放列表") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.create(newName); newName = ""; showCreate = false }, enabled = newName.isNotBlank()) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }
    deletePlaylistId?.let { playlistId ->
        val playlist = state.playlists.firstOrNull { it.id == playlistId }
        AlertDialog(
            onDismissRequest = { deletePlaylistId = null },
            title = { Text("删除播放列表？") },
            text = { Text("将删除“${playlist?.name ?: "此列表"}”及其中的条目，但不会删除媒体文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(playlistId)
                        deletePlaylistId = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletePlaylistId = null }) { Text("取消") } },
        )
    }
    renamePlaylistId?.let { playlistId ->
        AlertDialog(
            onDismissRequest = { renamePlaylistId = null },
            title = { Text("重命名播放列表") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.rename(playlistId, renameName)
                        renamePlaylistId = null
                    },
                    enabled = renameName.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renamePlaylistId = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
) {
    val vm: DownloadsViewModel = viewModel(factory = SimpleViewModelFactory { DownloadsViewModel(container.downloads) })
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线下载") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        if (state.downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有离线下载，媒体详情中可以添加下载")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.downloads, key = { it.id }) { download ->
                    val localFileAvailable = download.status == DownloadStatus.COMPLETED.name && java.io.File(download.localPath).isFile
                    val progress = download.totalBytes?.takeIf { it > 0 }?.let {
                        (download.downloadedBytes.toFloat() / it).coerceIn(0f, 1f)
                    }
                    ListItem(
                        headlineContent = { Text(download.title, maxLines = 2) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(downloadStatusText(download, localFileAvailable))
                                Text("${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}")
                                progress?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth()) }
                                download.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Download, "离线文件") },
                        trailingContent = {
                            Row {
                                if (localFileAvailable) {
                                    IconButton(onClick = { onPlay(download.id) }) { Icon(Icons.Default.PlayArrow, "播放离线文件") }
                                }
                                when (download.status) {
                                    DownloadStatus.RUNNING.name, DownloadStatus.QUEUED.name ->
                                        IconButton(onClick = { vm.pause(download.id) }) { Icon(Icons.Default.Pause, "暂停下载") }
                                    DownloadStatus.PAUSED.name, DownloadStatus.FAILED.name ->
                                        IconButton(onClick = { vm.retry(download.id) }) { Icon(Icons.Default.Refresh, "重试下载") }
                                    DownloadStatus.COMPLETED.name -> if (!localFileAvailable) {
                                        IconButton(onClick = { vm.retry(download.id) }) { Icon(Icons.Default.Refresh, "重新下载") }
                                    }
                                }
                                IconButton(onClick = { vm.delete(download.id) }) { Icon(Icons.Default.Delete, "删除下载") }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun downloadStatusText(download: DownloadEntity, localFileAvailable: Boolean = true): String = when {
    download.status == DownloadStatus.COMPLETED.name && !localFileAvailable -> "本地文件缺失"
    download.status == DownloadStatus.QUEUED.name -> "等待下载"
    download.status == DownloadStatus.RUNNING.name -> "正在下载"
    download.status == DownloadStatus.PAUSED.name -> "已暂停"
    download.status == DownloadStatus.COMPLETED.name -> "已完成"
    download.status == DownloadStatus.CANCELED.name -> "已取消"
    else -> "下载失败"
}

@Composable
fun DownloadedPlayerScreen(
    container: AppContainer,
    downloadId: String,
    onBack: () -> Unit,
) {
    val downloadFlow = remember(downloadId) {
        container.downloads.observeAll().map { items -> items.firstOrNull { it.id == downloadId } }
    }
    val download by downloadFlow
        .collectAsStateWithLifecycle(initialValue = null)
    val item = download
    if (item == null || item.status != DownloadStatus.COMPLETED.name || !java.io.File(item.localPath).isFile) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("离线文件不可用") }
    } else {
        PlayerScreen(
            container = container,
            sourceId = item.sourceId,
            path = item.path,
            download = item,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsScreen(
    container: AppContainer,
    sourceId: String,
    path: String,
    onBack: () -> Unit,
    onPlay: (String, String) -> Unit,
) {
    val itemFlow = remember(sourceId, path) {
        container.mediaLibrary.observeMedia()
            .map { rows -> rows.firstOrNull { it.sourceId == sourceId && it.path == path } }
    }
    val item by itemFlow.collectAsStateWithLifecycle(initialValue = null)
    val settings by container.settings.preferences.collectAsStateWithLifecycle(initialValue = PlayerPreferences())
    val groupKey = item?.groupKey.orEmpty()
    val episodes by remember(groupKey) { container.mediaLibrary.observeEpisodes(groupKey) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var versions by remember { mutableStateOf<List<MediaVersionRow>>(emptyList()) }
    var artwork by remember { mutableStateOf<Bitmap?>(null) }
    var backdrop by remember { mutableStateOf<Bitmap?>(null) }
    var selectedSeason by remember(item?.groupKey) { mutableIntStateOf(item?.season ?: 1) }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    val downloads by container.downloads.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val currentDownload = downloads.firstOrNull { it.sourceId == sourceId && it.path == path }
    val currentDownloadAvailable = currentDownload?.let {
        it.status == DownloadStatus.COMPLETED.name && java.io.File(it.localPath).isFile
    } == true
    val downloadScope = rememberCoroutineScope()
    LaunchedEffect(item?.mediaKey) {
        versions = item?.mediaKey?.let { key -> container.mediaLibrary.getVersions(key) }.orEmpty()
    }
    LaunchedEffect(item?.sourceId, item?.artworkPath) {
        artwork = null
        val media = item ?: return@LaunchedEffect
        val artworkPath = media.artworkPath ?: return@LaunchedEffect
        artwork = container.artwork.load(media.sourceId, artworkPath)
    }
    LaunchedEffect(item?.sourceId, item?.backdropPath) {
        backdrop = null
        val media = item ?: return@LaunchedEffect
        val backdropPath = media.backdropPath ?: return@LaunchedEffect
        backdrop = container.artwork.load(media.sourceId, backdropPath)
    }
    LaunchedEffect(item?.mediaKey, settings.tmdbApiKey) {
        val media = item ?: return@LaunchedEffect
        if (settings.tmdbApiKey.isNotBlank()) container.tmdb.enrich(media, settings.tmdbApiKey)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.title ?: "媒体详情") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        val media = item
        if (media == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("媒体不存在，可能已从来源中删除")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            backdrop?.let { bitmap ->
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().alpha(0.55f),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                            }
                            artwork?.let { bitmap ->
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = media.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            } ?: Icon(Icons.Default.Movie, "海报占位", modifier = Modifier.size(72.dp))
                            if (artwork == null && media.artworkPath != null) {
                                Text("本地海报：${media.artworkPath.substringAfterLast('/')}", Modifier.align(Alignment.BottomCenter).padding(12.dp))
                            }
                        }
                    }
                }
                item {
                    Text(media.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        buildString {
                            append(media.sourceName)
                            if (media.year != null) append(" · ${media.year}")
                            if (media.kind == "EPISODE") append(" · S%02dE%02d".format(media.season ?: 0, media.episode ?: 0))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPlay(media.sourceId, media.path) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (media.kind == "EPISODE") "从本集开始连播" else "播放")
                        }
                        OutlinedButton(onClick = { showPlaylistDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, null)
                            Spacer(Modifier.width(8.dp))
                            Text("加入播放列表")
                        }
                        OutlinedButton(
                            onClick = {
                                downloadScope.launch { container.downloads.enqueue(media.sourceId, media.path, media.title, media.size) }
                            },
                            enabled = !currentDownloadAvailable,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (currentDownload?.status) {
                                    DownloadStatus.RUNNING.name, DownloadStatus.QUEUED.name -> "下载中…"
                                    DownloadStatus.PAUSED.name -> "继续下载"
                                    DownloadStatus.FAILED.name -> "重试下载"
                                    DownloadStatus.COMPLETED.name -> if (currentDownloadAvailable) "已下载" else "重新下载"
                                    else -> "下载到本机"
                                },
                            )
                        }
                        if (media.watchedPositionMs != null && media.watchedDurationMs != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        downloadScope.launch {
                                            container.playbackHistory.save(
                                                media.sourceId,
                                                media.path,
                                                media.watchedDurationMs,
                                                media.watchedDurationMs,
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("标记已看") }
                                TextButton(
                                    onClick = {
                                        downloadScope.launch { container.playbackHistory.clear(media.sourceId, media.path) }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("清除进度") }
                            }
                        }
                    }
                }
                if (media.kind == "EPISODE" && episodes.isNotEmpty()) {
                    item {
                        Text("剧集列表 · ${episodes.size} 集", style = MaterialTheme.typography.titleMedium)
                        Text("播放任意一集后，libVLC 会按季和集数自动播放后续内容", style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            episodes.mapNotNull { it.season }.distinct().sorted().forEach { season ->
                                FilterChip(
                                    selected = selectedSeason == season,
                                    onClick = { selectedSeason = season },
                                    label = { Text("第 $season 季") },
                                )
                            }
                        }
                    }
                    items(episodes.filter { it.season == selectedSeason }, key = { it.mediaKey }) { episode ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    "S%02dE%02d · %s".format(
                                        episode.season ?: 0,
                                        episode.episode ?: 0,
                                        episode.title,
                                    ),
                                )
                            },
                            supportingContent = { Text("${episode.fileName} · ${formatBytes(episode.size)}") },
                            trailingContent = {
                                IconButton(onClick = { onPlay(episode.sourceId, episode.path) }) {
                                    Icon(Icons.Default.PlayArrow, "播放本集")
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
                if (versions.size > 1) {
                    item {
                        Text("播放版本", style = MaterialTheme.typography.titleMedium)
                    }
                    items(versions, key = { it.mediaKey }) { version ->
                        ListItem(
                            headlineContent = { Text(version.label ?: "其他版本") },
                            supportingContent = { Text("${version.fileName} · ${formatBytes(version.size)}") },
                            trailingContent = {
                                IconButton(onClick = { onPlay(media.sourceId, version.path) }) {
                                    Icon(Icons.Default.PlayArrow, "播放此版本")
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
                media.plot?.takeIf(String::isNotBlank)?.let { plot ->
                    item {
                        Text("简介", style = MaterialTheme.typography.titleMedium)
                        Text(plot)
                    }
                }
                media.rating?.let { rating ->
                    item {
                        Text("评分：%.1f / 10".format(rating), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                media.director?.takeIf(String::isNotBlank)?.let { director ->
                    item {
                        Text("导演", style = MaterialTheme.typography.titleMedium)
                        Text(director)
                    }
                }
                media.castMembers
                    ?.split('\n')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { cast ->
                        item {
                            Text("演员", style = MaterialTheme.typography.titleMedium)
                            Text(cast.joinToString(" · "))
                        }
                    }
                item {
                    Text("文件信息", style = MaterialTheme.typography.titleMedium)
                    Text("来源：${media.sourceName}")
                    Text("文件：${media.fileName}")
                    Text("大小：${formatBytes(media.size)}")
                    Text("路径：${media.path}")
                }
            }
        }
    }
    if (showPlaylistDialog && item != null) {
        AddToPlaylistDialog(
            container = container,
            item = item!!,
            onDismiss = { showPlaylistDialog = false },
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    container: AppContainer,
    item: LibraryItemRow,
    onDismiss: () -> Unit,
) {
    val vm: PlaylistViewModel = viewModel(factory = SimpleViewModelFactory { PlaylistViewModel(container.playlists) })
    val state by vm.state.collectAsStateWithLifecycle()
    var newName by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入播放列表") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.playlists.isEmpty()) Text("还没有播放列表")
                state.playlists.forEach { playlist ->
                    OutlinedButton(
                        onClick = { vm.add(playlist, item); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null)
                        Spacer(Modifier.width(8.dp))
                        Text(playlist.name)
                    }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新建列表并加入") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                vm.createAndAdd(newName, item)
                                onDismiss()
                            },
                            enabled = newName.isNotBlank(),
                        ) { Icon(Icons.Default.Add, "创建") }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SimpleViewModelFactory { SettingsViewModel(container.settings) })
    val preferences by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crashReporter = remember(context) { CrashReporter(context) }
    val latestCrash = remember(crashReporter) { crashReporter.latest() }
    var completion by remember(preferences.completionThreshold) {
        mutableFloatStateOf(preferences.completionThreshold)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SettingsSection("默认倍速") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            FilterChip(
                                selected = preferences.playbackSpeed == speed,
                                onClick = { vm.setPlaybackSpeed(speed) },
                                label = { Text(speed.toString().removeSuffix(".0") + "×") },
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection("画面比例") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoScale.entries.forEach { scale ->
                            FilterChip(
                                selected = preferences.videoScale == scale,
                                onClick = { vm.setVideoScale(scale) },
                                label = {
                                    Text(when (scale) {
                                        VideoScale.FIT -> "适应"
                                        VideoScale.FILL -> "填充"
                                        VideoScale.ZOOM -> "裁切"
                                    })
                                },
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection("默认音轨和字幕") {
                    OutlinedTextField(
                        value = preferences.preferredAudioLanguage,
                        onValueChange = vm::setPreferredAudioLanguage,
                        label = { Text("音轨语言代码") },
                        supportingText = { Text("可填 eng、chi、jpn 等；留空使用媒体默认值") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = preferences.preferredSubtitleLanguage,
                        onValueChange = vm::setPreferredSubtitleLanguage,
                        label = { Text("字幕语言代码") },
                        supportingText = { Text("可填 eng、chi、jpn 等；留空使用媒体默认值") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                SettingsSection("网络缓冲") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BufferPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = preferences.bufferPreset == preset,
                                onClick = { vm.setBufferPreset(preset) },
                                label = {
                                    Text(when (preset) {
                                        BufferPreset.LOW_LATENCY -> "低延迟"
                                        BufferPreset.BALANCED -> "均衡"
                                        BufferPreset.STABLE -> "稳定"
                                    })
                                },
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection("播放完成阈值 " + (completion * 100).toInt() + "%") {
                    Slider(
                        value = completion,
                        onValueChange = { completion = it },
                        onValueChangeFinished = { vm.setCompletionThreshold(completion) },
                        valueRange = 0.5f..1f,
                        steps = 9,
                    )
                }
            }
            item { SettingSwitch("记忆播放进度", preferences.rememberProgress, vm::setRememberProgress) }
            item { SettingSwitch("播放时保持屏幕常亮", preferences.keepScreenOn, vm::setKeepScreenOn) }
            item { SettingSwitch("自动横屏", preferences.autoLandscape, vm::setAutoLandscape) }
            item { SettingSwitch("显示隐藏文件", preferences.showHiddenFiles, vm::setShowHiddenFiles) }
            item {
                SettingsSection("在线元数据（可选）") {
                    OutlinedTextField(
                        value = preferences.tmdbApiKey,
                        onValueChange = vm::setTmdbApiKey,
                        label = { Text("TMDB API Key") },
                        supportingText = { Text("留空则仅使用本地文件名和 NFO，不影响离线播放") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                SettingsSection("诊断报告") {
                    Text(
                        if (latestCrash == null) "暂无本地崩溃报告"
                        else "报告已脱敏，仅包含线程、时间和错误堆栈",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = {
                            crashReporter.copyLatestToCache()?.let { report ->
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    report,
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "分享诊断报告"))
                            }
                        },
                        enabled = latestCrash != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("分享最近一次报告") }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}
@Composable
fun PlayerScreen(
    container: AppContainer,
    sourceId: String,
    path: String,
    playlistId: String? = null,
    onBack: () -> Unit,
    download: DownloadEntity? = null,
) {
    val context = LocalContext.current
    val playbackSourceResolver = remember(context, container.credentialStore) {
        PlaybackSourceResolver(context, container.credentialStore)
    }
    val activity = context as? com.vidbridge.MainActivity
    val inPipMode = activity?.isPipMode == true
    val preferences by container.settings.preferences.collectAsStateWithLifecycle(initialValue = PlayerPreferences())
    val currentPreferences by rememberUpdatedState(preferences)
    var playbackService by remember { mutableStateOf<PlaybackService?>(null) }
    val engine = playbackService?.engine
    val playerState by (engine?.state ?: emptyFlow()).collectAsStateWithLifecycle(initialValue = PlayerState())
    var videoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var source by remember { mutableStateOf<MediaSourceConfig?>(null) }
    var activeSourceId by remember(sourceId) { mutableStateOf(sourceId) }
    var activePath by remember(path) { mutableStateOf(path) }
    var playbackQueue by remember { mutableStateOf<List<PlaybackQueueRow>>(emptyList()) }
    val currentQueue by rememberUpdatedState(playbackQueue)
    val currentSource by rememberUpdatedState(activeSourceId)
    val currentMediaPath by rememberUpdatedState(activePath)
    val recoverySession = remember { container.playbackSession.read() }
    val recoveredLocalPath = localRecoveryPath(
        recoverySession,
        sourceId,
        path,
    ) { java.io.File(it).isFile }
    val effectiveLocalPath = download?.localPath ?: recoveredLocalPath
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var exitHandled by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var externalSubtitlePath by rememberSaveable { mutableStateOf<String?>(null) }
    var autoSubtitlePath by rememberSaveable { mutableStateOf<String?>(null) }
    val subtitlePath = externalSubtitlePath ?: autoSubtitlePath
    var queueMenuExpanded by remember { mutableStateOf(false) }
    val playerFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            externalSubtitlePath = runCatching {
                withContext(Dispatchers.IO) {
                    val directory = java.io.File(context.cacheDir, "external-subtitles").apply { mkdirs() }
                    val extension = DocumentFile.fromSingleUri(context, uri)
                        ?.name
                        ?.substringAfterLast('.', "srt")
                        ?.replace(Regex("[^A-Za-z0-9]"), "")
                        ?.lowercase()
                        ?.takeIf { it in setOf("srt", "ass", "ssa", "vtt", "sub") }
                        ?: "srt"
                    val target = java.io.File(directory, "subtitle_${System.currentTimeMillis()}.$extension")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("无法读取字幕文件")
                    target.absolutePath
                }
            }.onFailure { Log.w("VidBridgeVLC", "Failed to import external subtitle", it) }.getOrNull()
        }
    }
    DisposableEffect(Unit) {
        val serviceIntent = Intent(context, PlaybackService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                playbackService = (binder as? PlaybackService.LocalBinder)?.service()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playbackService = null
            }
        }
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            runCatching { context.unbindService(connection) }
            playbackService = null
        }
    }
    LaunchedEffect(activeSourceId, playlistId) {
        source = null
        runCatching { container.sources.get(activeSourceId) }
            .onSuccess { source = it }
            .onFailure {
                Log.e("VidBridgeVLC", "Failed to load source $activeSourceId", it)
            }
        playbackQueue = when {
            download != null || recoveredLocalPath != null -> emptyList()
            playlistId != null -> runCatching {
                container.playlists.getItems(playlistId).map { item ->
                    PlaybackQueueRow(item.sourceId, item.path, item.title, null)
                }
            }.getOrDefault(emptyList())
            else -> runCatching { container.mediaLibrary.getPlaybackQueue(activeSourceId, activePath) }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(activeSourceId, activePath) {
        // An imported subtitle belongs to one media item and must never leak into the next queue item.
        externalSubtitlePath = null
        autoSubtitlePath = null
    }
    LaunchedEffect(source, activeSourceId, activePath, effectiveLocalPath) {
        autoSubtitlePath = null
        val config = source ?: return@LaunchedEffect
        if (effectiveLocalPath != null || config.type in setOf(MediaSourceType.JELLYFIN, MediaSourceType.EMBY, MediaSourceType.PLEX)) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                container.fileSystems.create(activeSourceId).use { remote ->
                    remote.connect()
                    val parent = RemotePath(activePath).parent
                    val entries = remote.list(parent, PageRequest(limit = 250)).items
                    val match = findMatchingSubtitle(activePath, entries) ?: return@withContext null
                    playbackSourceResolver.uri(config, match.path.value).toString()
                }
            }
        }.onSuccess { candidate ->
            if (externalSubtitlePath == null) autoSubtitlePath = candidate
        }.onFailure { Log.d("VidBridgeVLC", "No adjacent subtitle for $activePath", it) }
    }
    DisposableEffect(Unit) {
        onDispose { if (!exitHandled) saveEngineProgress(container, activeSourceId, activePath, engine, currentPreferences, scope) }
    }
    DisposableEffect(engine, videoLayout) {
        onDispose { engine?.detachSurface() }
    }
    LaunchedEffect(videoLayout, source, activeSourceId, activePath, engine, subtitlePath) {
        val layout = videoLayout ?: return@LaunchedEffect
        val config = source ?: return@LaunchedEffect
        val activeEngine = engine ?: return@LaunchedEffect
        val resumeAt = if (preferences.rememberProgress) {
            maxOf(
                com.vidbridge.playback.PlaybackHistoryRepository.resumePosition(
                    container.playbackHistory.get(activeSourceId, activePath),
                ),
                recoverySession
                    ?.takeIf { it.sourceId == activeSourceId && it.path == activePath }
                    ?.positionMs
                    ?.takeIf { it >= 30_000 }
                    ?: 0L,
            )
        } else 0
        runCatching {
            layout.post {
                activeEngine.attachSurface(layout)
                playbackService?.prepare(
                    PlayerMedia(
                        playbackSourceResolver.uri(config, activePath, effectiveLocalPath),
                        buildList {
                            if (effectiveLocalPath == null) addAll(playbackSourceResolver.options(config, preferences))
                            else add(":file-caching=300")
                            subtitlePath?.let { add(":sub-file=$it") }
                        },
                        resumeAt,
                    ),
                    activePath.substringAfterLast('/'),
                    activeSourceId,
                    activePath,
                    effectiveLocalPath,
                    playlistId,
                )
            }
        }.onFailure {
            Log.e("VidBridgeVLC", "Failed to schedule playback: $activePath", it)
        }
    }
    LaunchedEffect(videoLayout, inPipMode) {
        if (videoLayout != null && !inPipMode) runCatching { playerFocusRequester.requestFocus() }
    }
    fun exitPlayback() {
        exitHandled = true
        saveEngineProgress(container, activeSourceId, activePath, engine, currentPreferences, scope)
        onBack()
    }
    fun switchQueueItem(offset: Int) {
        val currentIndex = playbackQueue.indexOfFirst { it.sourceId == activeSourceId && it.path == activePath }
        val next = playbackQueue.getOrNull(currentIndex + offset) ?: return
        saveEngineProgress(container, activeSourceId, activePath, engine, currentPreferences, scope)
        activeSourceId = next.sourceId
        activePath = next.path
    }
    fun selectQueueItem(index: Int) {
        val next = playbackQueue.getOrNull(index) ?: return
        queueMenuExpanded = false
        if (next.sourceId == activeSourceId && next.path == activePath) return
        saveEngineProgress(container, activeSourceId, activePath, engine, currentPreferences, scope)
        activeSourceId = next.sourceId
        activePath = next.path
    }
    BackHandler { exitPlayback() }
    DisposableEffect(preferences.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (preferences.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(engine, preferences.playbackSpeed) {
        engine?.setSpeed(preferences.playbackSpeed)
    }
    LaunchedEffect(engine, preferences.audioDelayMs) {
        engine?.setAudioDelayMs(preferences.audioDelayMs)
    }
    LaunchedEffect(engine, preferences.subtitleDelayMs) {
        engine?.setSubtitleDelayMs(preferences.subtitleDelayMs)
    }
    LaunchedEffect(engine, preferences.videoScale) {
        engine?.setVideoScale(
            when (preferences.videoScale) {
                VideoScale.FIT -> PlayerVideoScale.FIT
                VideoScale.FILL -> PlayerVideoScale.FILL
                VideoScale.ZOOM -> PlayerVideoScale.ZOOM
            },
        )
    }
    LaunchedEffect(preferences.rememberProgress, preferences.completionThreshold) {
        while (isActive) {
            delay(10_000)
            runCatching {
                if (preferences.rememberProgress) container.playbackHistory.save(activeSourceId, activePath, playerState.positionMs, playerState.durationMs, preferences.completionThreshold)
            }.onFailure { Log.w("VidBridgeVLC", "Failed to save playback progress", it) }
        }
    }
    LaunchedEffect(engine) {
        while (isActive) {
            engine?.refreshPosition()
            engine?.refreshTracks()
            if (!isSeeking && playerState.durationMs > 0L) seekValue = playerState.positionMs.toFloat() / playerState.durationMs
            delay(500)
        }
    }
    LaunchedEffect(engine) {
        val activeEngine = engine ?: return@LaunchedEffect
        var wasEnded = activeEngine.state.value.playbackState == com.vidbridge.playback.PlayerPlaybackState.ENDED
        activeEngine.state.collect { state ->
            val isEnded = state.playbackState == com.vidbridge.playback.PlayerPlaybackState.ENDED
            if (isEnded && !wasEnded) {
                saveEngineProgress(container, currentSource, currentMediaPath, activeEngine, currentPreferences, scope)
                val currentIndex = currentQueue.indexOfFirst { it.sourceId == currentSource && it.path == currentMediaPath }
                val next = currentQueue.getOrNull(currentIndex + 1)
                if (next != null) {
                    activeSourceId = next.sourceId
                    activePath = next.path
                }
            }
            wasEnded = isEnded
        }
    }
    DisposableEffect(preferences.autoLandscape) {
        val activity = context as? Activity
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        if (preferences.autoLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            previousBehavior?.let { controller.systemBarsBehavior = it }
            if (preferences.autoLandscape) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        engine?.seekTo((playerState.positionMs - 10_000L).coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionRight -> {
                        engine?.seekTo((playerState.positionMs + 10_000L).coerceAtMost(playerState.durationMs))
                        true
                    }
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.MediaPlayPause -> {
                        controlsVisible = true
                        if (playerState.isPlaying) engine?.pause() else engine?.play()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { VLCVideoLayout(it) },
            update = {
                videoLayout = it
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(playerState.durationMs) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { position ->
                            val delta = if (position.x < size.width / 2f) -10_000L else 10_000L
                            engine?.seekTo((playerState.positionMs + delta).coerceIn(0L, playerState.durationMs))
                        },
                    )
                },
        )
        if (!inPipMode && controlsVisible) {
            IconButton(
                onClick = { exitPlayback() },
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = androidx.compose.ui.graphics.Color.White) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IconButton(
                    onClick = {
                        (context as? Activity)?.enterPictureInPictureMode(
                            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                        )
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) { Icon(Icons.Default.PictureInPictureAlt, "画中画", tint = androidx.compose.ui.graphics.Color.White) }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.68f),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (playerState.audioTracks.isNotEmpty() || playerState.subtitleTracks.isNotEmpty()) {
                            TrackMenu("音轨", playerState.audioTracks, playerState.selectedAudioTrack) { engine?.selectAudioTrack(it) }
                            if (playerState.audioTracks.isNotEmpty()) {
                                AudioDelayMenu(playerState.audioDelayMs) {
                                    engine?.setAudioDelayMs(it)
                                    scope.launch { container.settings.setAudioDelayMs(it) }
                                }
                            }
                            TrackMenu(
                                label = "字幕",
                                tracks = playerState.subtitleTracks,
                                selectedId = playerState.selectedSubtitleTrack,
                                offLabel = "关闭字幕",
                            ) { engine?.selectSubtitleTrack(it) }
                            if (playerState.subtitleTracks.isNotEmpty()) {
                                SubtitleDelayMenu(playerState.subtitleDelayMs) {
                                    engine?.setSubtitleDelayMs(it)
                                    scope.launch { container.settings.setSubtitleDelayMs(it) }
                                }
                            }
                        }
                        AssistChip(
                            onClick = { subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/ttml+xml")) },
                            label = { Text(if (subtitlePath == null) "加载字幕" else "更换字幕") },
                        )
                        if (subtitlePath != null) {
                            AssistChip(
                                onClick = { externalSubtitlePath = null; autoSubtitlePath = null },
                                label = { Text("清除外挂字幕") },
                            )
                        }
                    }
                    if (playerState.chapters.size > 1) {
                        ChapterMenu(playerState.chapters, playerState.currentChapter) { engine?.selectChapter(it) }
                    }
                    Slider(
                        value = if (isSeeking) seekValue else if (playerState.durationMs > 0L) playerState.positionMs.toFloat() / playerState.durationMs else 0f,
                        onValueChange = {
                            isSeeking = true
                            seekValue = it
                        },
                        onValueChangeFinished = {
                            engine?.seekTo((seekValue * playerState.durationMs).toLong())
                            isSeeking = false
                        },
                        enabled = playerState.durationMs > 0L,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatPlaybackTime(playerState.positionMs)} / ${formatPlaybackTime(playerState.durationMs)}",
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        val currentQueueIndex = playbackQueue.indexOfFirst { it.sourceId == activeSourceId && it.path == activePath }
                        if (playbackQueue.size > 1) {
                            Box {
                                AssistChip(
                                    onClick = { queueMenuExpanded = true },
                                    label = { Text("队列 ${if (currentQueueIndex >= 0) "${currentQueueIndex + 1}/${playbackQueue.size}" else "${playbackQueue.size}项"}") },
                                )
                                DropdownMenu(
                                    expanded = queueMenuExpanded,
                                    onDismissRequest = { queueMenuExpanded = false },
                                ) {
                                    playbackQueue.forEachIndexed { index, item ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${index + 1}. ${item.title.ifBlank { item.path.substringAfterLast('/') }}",
                                                    maxLines = 1,
                                                )
                                            },
                                            onClick = { selectQueueItem(index) },
                                            leadingIcon = if (index == currentQueueIndex) {
                                                { Icon(Icons.Default.PlayArrow, "当前播放") }
                                            } else null,
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = { switchQueueItem(-1) },
                            enabled = currentQueueIndex > 0,
                        ) {
                            Icon(Icons.Default.SkipPrevious, "上一集", tint = androidx.compose.ui.graphics.Color.White)
                        }
                        PlaybackSpeedMenu(playerState.speed) { engine?.setSpeed(it) }
                        IconButton(onClick = { engine?.seekTo(playerState.positionMs - 10_000L) }) {
                            Icon(Icons.Default.Replay10, "后退 10 秒", tint = androidx.compose.ui.graphics.Color.White)
                        }
                        IconButton(onClick = { engine?.seekTo((playerState.positionMs + 10_000L).coerceAtMost(playerState.durationMs)) }) {
                            Icon(Icons.Default.Forward10, "快进 10 秒", tint = androidx.compose.ui.graphics.Color.White)
                        }
                        IconButton(onClick = { if (playerState.isPlaying) engine?.pause() else engine?.play() }) {
                            Icon(
                                if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playerState.isPlaying) "暂停" else "播放",
                                tint = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                        IconButton(
                            onClick = { switchQueueItem(1) },
                            enabled = currentQueueIndex >= 0 && currentQueueIndex < playbackQueue.lastIndex,
                        ) {
                            Icon(Icons.Default.SkipNext, "下一集", tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        }
        playerState.errorMessage?.let { error ->
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("播放失败：$error")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { engine?.retry() }) { Text("重试") }
                }
            }
        }
    }
}

@Composable
private fun PlaybackSpeedMenu(speed: Float, onSelected: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text("${speed.toString().removeSuffix(".0")}×") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text("${value.toString().removeSuffix(".0")}×") },
                    onClick = { expanded = false; onSelected(value) },
                )
            }
        }
    }
}

@Composable
private fun ChapterMenu(
    chapters: List<com.vidbridge.playback.PlayerChapter>,
    currentChapter: Int?,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = chapters.getOrNull(currentChapter ?: -1)
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(current?.name ?: "章节") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            chapters.forEach { chapter ->
                DropdownMenuItem(
                    text = { Text("${chapter.index + 1}. ${chapter.name}") },
                    onClick = { expanded = false; onSelected(chapter.index) },
                )
            }
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun findMatchingSubtitle(mediaPath: String, entries: List<RemoteEntry>): RemoteEntry? {
    val mediaName = mediaPath.substringAfterLast('/').substringAfterLast('\\')
    val mediaStem = mediaName.substringBeforeLast('.', mediaName).lowercase()
    val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt", "sub")
    return entries
        .asSequence()
        .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in subtitleExtensions }
        .mapNotNull { entry ->
            val subtitleStem = entry.name.substringBeforeLast('.', entry.name).lowercase()
            val related = subtitleStem == mediaStem ||
                subtitleStem.startsWith("$mediaStem.") ||
                mediaStem.startsWith("$subtitleStem.")
            if (!related) return@mapNotNull null
            val languageScore = when {
                subtitleStem.endsWith(".zh") || subtitleStem.endsWith(".chs") || subtitleStem.endsWith(".sc") -> 0
                subtitleStem.endsWith(".en") -> 1
                else -> 2
            }
            languageScore to entry
        }
        .sortedWith(compareBy<Pair<Int, RemoteEntry>> { it.first }.thenBy { it.second.name.length })
        .map { it.second }
        .firstOrNull()
}

@Composable
private fun TrackMenu(
    label: String,
    tracks: List<PlayerTrack>,
    selectedId: Int?,
    offLabel: String? = null,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember(label) { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { expanded = true }, label = {
            Text(tracks.firstOrNull { it.id == selectedId }?.name ?: offLabel ?: label, maxLines = 1)
        })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (offLabel != null) {
                DropdownMenuItem(
                    text = { Text(offLabel) },
                    onClick = { expanded = false; onSelected(-1) },
                )
            }
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.name) },
                    onClick = { expanded = false; onSelected(track.id) },
                )
            }
        }
    }
}

@Composable
private fun SubtitleDelayMenu(delayMs: Long, onSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { expanded = true }, label = {
            Text("字幕 ${if (delayMs == 0L) "同步" else "%+d ms".format(delayMs)}")
        })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(-500L, -200L, -100L, 0L, 100L, 200L, 500L).forEach { value ->
                DropdownMenuItem(
                    text = { Text(if (value == 0L) "恢复同步" else "%+d ms".format(value)) },
                    onClick = { expanded = false; onSelected(value) },
                )
            }
        }
    }
}

@Composable
private fun AudioDelayMenu(delayMs: Long, onSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { expanded = true }, label = {
            Text("音频 ${if (delayMs == 0L) "同步" else "%+d ms".format(delayMs)}")
        })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(-500L, -200L, -100L, 0L, 100L, 200L, 500L).forEach { value ->
                DropdownMenuItem(
                    text = { Text(if (value == 0L) "恢复同步" else "%+d ms".format(value)) },
                    onClick = { expanded = false; onSelected(value) },
                )
            }
        }
    }
}

private fun saveEngineProgress(
    container: AppContainer,
    sourceId: String,
    path: String,
    engine: com.vidbridge.playback.LibVlcPlayerEngine?,
    preferences: PlayerPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (!preferences.rememberProgress) return
    val state = engine?.state?.value ?: return
    scope.launch {
        container.playbackHistory.save(sourceId, path, state.positionMs, state.durationMs, preferences.completionThreshold)
    }
}
