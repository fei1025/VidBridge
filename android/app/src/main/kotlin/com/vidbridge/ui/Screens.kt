package com.vidbridge.ui

import android.app.Activity
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.Intent
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.vidbridge.AppContainer
import com.vidbridge.core.settings.*
import com.vidbridge.core.database.ContinueWatchingRow
import com.vidbridge.core.database.FavoriteItemRow
import com.vidbridge.core.database.LibraryItemRow
import com.vidbridge.core.database.ScanJobEntity
import com.vidbridge.playback.PlaybackService
import com.vidbridge.playback.PlaybackUris
import com.vidbridge.protocol.api.MediaSourceConfig
import com.vidbridge.protocol.api.MediaSourceType
import com.vidbridge.protocol.api.RemoteEntry
import com.vidbridge.protocol.api.RemotePath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(container: AppContainer, onAdd: () -> Unit, onEdit: (String) -> Unit, onSettings: () -> Unit, onLibrary: () -> Unit, onBrowse: (String) -> Unit) {
    val vm: SourcesViewModel = viewModel(factory = SimpleViewModelFactory { SourcesViewModel(container.sources, container.fileSystems, container.mediaLibrary) })
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VidBridge") },
                actions = {
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
                    Text("添加本地、SMB 或 WebDAV 来源")
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        selected = draft.type == MediaSourceType.WEBDAV,
                        onClick = { vm.setType(MediaSourceType.WEBDAV) },
                        label = { Text("WebDAV") },
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
                            draft.type == MediaSourceType.WEBDAV -> "WebDAV 根路径（可选）"
                            draft.shareName.isBlank() -> "初始路径（可选，首段为共享名）"
                            else -> "共享内初始目录（可选）"
                        },
                        draft.rootPath,
                    ) { vm.update { d -> d.copy(rootPath = it) } }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(draft.anonymous, onCheckedChange = { value -> vm.update { it.copy(anonymous = value) } })
                        Text("匿名访问", Modifier.padding(start = 12.dp))
                    }
                }
                if (!draft.anonymous) {
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
fun LibraryScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String, String, Boolean) -> Unit) {
    val vm: LibraryViewModel = viewModel(factory = SimpleViewModelFactory { LibraryViewModel(container.mediaLibrary) })
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("媒体库") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::search,
                label = { Text("搜索标题或文件名") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 16.dp)) {
                listOf("全部", "继续观看", "收藏").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                    ) { Text(label) }
                }
            }
            when (selectedTab) {
                1 -> if (state.continueWatching.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有可继续观看的内容") }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.continueWatching, key = { "${it.sourceId}:${it.path}" }) { item ->
                            ContinueWatchingItem(item) { onOpen(item.sourceId, item.path, false) }
                        }
                    }
                }
                2 -> if (state.favorites.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有收藏的视频或文件夹") }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.favorites, key = { it.mediaKey }) { item ->
                            FavoriteItem(item) { onOpen(item.sourceId, item.path, item.isDirectory) }
                        }
                    }
                }
                else -> if (state.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("媒体库为空，请先从来源菜单启动扫描")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.items, key = { it.mediaKey }) { item ->
                            LibraryItem(item, { onOpen(item.sourceId, item.path, false) }, { vm.toggleFavorite(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItem(item: LibraryItemRow, onPlay: () -> Unit, onFavorite: () -> Unit) {
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
            IconButton(onClick = onFavorite) {
                Icon(if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder, if (item.favorite) "取消收藏" else "收藏")
            }
        },
        modifier = Modifier.clickable(onClick = onPlay),
    )
    HorizontalDivider()
}

@Composable
private fun ContinueWatchingItem(item: ContinueWatchingRow, onPlay: () -> Unit) {
    val progress = if (item.durationMs > 0) item.positionMs.toFloat() / item.durationMs else 0f
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = {
            Column {
                Text(item.sourceName)
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        },
        leadingContent = { Icon(Icons.Default.PlayCircle, null) },
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SimpleViewModelFactory { SettingsViewModel(container.settings) })
    val preferences by vm.state.collectAsStateWithLifecycle()
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
@UnstableApi
@Composable
fun PlayerScreen(container: AppContainer, sourceId: String, path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences by container.settings.preferences.collectAsStateWithLifecycle(initialValue = PlayerPreferences())
    val currentPreferences by rememberUpdatedState(preferences)
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            val active = controller
            val position = active?.currentPosition ?: 0
            val duration = active?.duration ?: 0
            val location = active?.currentPlaybackLocation() ?: (sourceId to path)
            if (currentPreferences.rememberProgress) {
                scope.launch {
                    container.playbackHistory.save(
                        location.first,
                        location.second,
                        position,
                        duration,
                        currentPreferences.completionThreshold,
                    )
                }
            }
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    LaunchedEffect(controller, sourceId, path) {
        controller?.apply {
            val resumeAt = if (preferences.rememberProgress) {
                com.vidbridge.playback.PlaybackHistoryRepository.resumePosition(
                    container.playbackHistory.get(sourceId, path),
                )
            } else {
                0
            }
            val queue = container.mediaLibrary.getPlaybackQueue(sourceId)
            val queueIndex = queue.indexOfFirst { it.path == path }
            if (queueIndex >= 0) {
                val mediaItems = queue.map { item ->
                    MediaItem.Builder()
                        .setMediaId("${item.sourceId}\u0000${item.path}")
                        .setUri(PlaybackUris.remote(item.sourceId, RemotePath(item.path)))
                        .setMimeType(item.mimeType)
                        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(item.title).build())
                        .build()
                }
                setMediaItems(mediaItems, queueIndex, resumeAt)
            } else {
                setMediaItem(MediaItem.fromUri(PlaybackUris.remote(sourceId, RemotePath(path))))
                if (resumeAt > 0) seekTo(resumeAt)
            }
            prepare()
            play()
        }
    }
    LaunchedEffect(controller, preferences.playbackSpeed) {
        controller?.setPlaybackSpeed(preferences.playbackSpeed)
    }
    LaunchedEffect(controller, preferences.rememberProgress, preferences.completionThreshold) {
        while (isActive) {
            delay(10_000)
            controller?.let { active ->
                if (preferences.rememberProgress) {
                    val location = active.currentPlaybackLocation() ?: (sourceId to path)
                    container.playbackHistory.save(
                        location.first,
                        location.second,
                        active.currentPosition,
                        active.duration,
                        preferences.completionThreshold,
                    )
                }
            }
        }
    }
    DisposableEffect(preferences.autoLandscape) {
        val activity = context as? Activity
        if (preferences.autoLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (preferences.autoLandscape) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = true } },
            update = {
                it.player = controller
                it.keepScreenOn = preferences.keepScreenOn
                it.resizeMode = when (preferences.videoScale) {
                    VideoScale.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    VideoScale.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    VideoScale.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = androidx.compose.ui.graphics.Color.White)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IconButton(
                onClick = {
                    (context as? Activity)?.enterPictureInPictureMode(
                        PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                    )
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Default.PictureInPictureAlt, "画中画", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
private fun MediaController.currentPlaybackLocation(): Pair<String, String>? {
    val uri = currentMediaItem?.localConfiguration?.uri ?: return null
    if (uri.scheme != PlaybackUris.SCHEME) return null
    val sourceId = uri.host ?: return null
    val path = uri.getQueryParameter("path") ?: return null
    return sourceId to path
}
