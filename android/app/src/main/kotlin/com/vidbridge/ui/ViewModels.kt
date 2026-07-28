package com.vidbridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vidbridge.protocol.api.*
import com.vidbridge.core.database.ContinueWatchingRow
import com.vidbridge.core.database.FavoriteItemRow
import com.vidbridge.core.database.LibraryItemRow
import com.vidbridge.library.MediaIdentity
import com.vidbridge.core.database.ScanJobEntity
import com.vidbridge.library.MediaLibraryRepository
import com.vidbridge.core.settings.*
import com.vidbridge.sources.SourceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class SourcesUiState(
    val sources: List<MediaSourceConfig> = emptyList(),
    val scanJobs: Map<String, ScanJobEntity> = emptyMap(),
    val isLoading: Boolean = true,
)

class SourcesViewModel(
    private val repository: SourceRepository,
    private val fileSystems: RemoteFileSystemFactory,
    private val mediaLibrary: MediaLibraryRepository,
) : ViewModel() {
    val state: StateFlow<SourcesUiState> = combine(
        repository.observeAll(),
        mediaLibrary.observeScanJobs(),
    ) { sources, jobs -> SourcesUiState(sources, jobs.associateBy { it.sourceId }, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcesUiState())
    val messages = MutableSharedFlow<String>()

    fun delete(source: MediaSourceConfig) = viewModelScope.launch { repository.delete(source.id) }
    fun scan(source: MediaSourceConfig) {
        mediaLibrary.scan(source.id)
    }
    fun cancelScan(source: MediaSourceConfig) {
        mediaLibrary.cancelScan(source.id)
    }

    fun test(source: MediaSourceConfig) = viewModelScope.launch {
        val message = try {
            fileSystems.create(source.id).use { remote ->
                remote.connect()
                if (source.type == MediaSourceType.SMB && source.shareName.isNullOrBlank()) {
                    remote.list(RemotePath(""))
                }
            }
            "${source.displayName} 连接成功"
        } catch (error: Throwable) {
            error.message ?: "连接失败"
        }
        messages.emit(message)
    }
}

data class LibraryUiState(
    val query: String = "",
    val items: List<LibraryItemRow> = emptyList(),
    val favorites: List<FavoriteItemRow> = emptyList(),
    val continueWatching: List<ContinueWatchingRow> = emptyList(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val repository: MediaLibraryRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    val state: StateFlow<LibraryUiState> = combine(
        query,
        query.flatMapLatest(repository::observeMedia),
        repository.observeFavorites(),
        repository.observeContinueWatching(),
    ) { value, items, favorites, continueWatching ->
        LibraryUiState(value, items, favorites, continueWatching)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun search(value: String) {
        query.value = value
    }

    fun toggleFavorite(item: LibraryItemRow) = viewModelScope.launch {
        repository.toggleFavorite(item)
    }
}
data class SourceDraft(
    val type: MediaSourceType = MediaSourceType.SMB,
    val displayName: String = "",
    val host: String = "",
    val port: String = "445",
    val tls: Boolean = true,
    val shareName: String = "",
    val rootPath: String = "",
    val rootUri: String? = null,
    val username: String = "",
    val password: String = "",
    val anonymous: Boolean = false,
)

object SourceFormValidator {
    fun validate(value: SourceDraft): String? {
        val port = value.port.toIntOrNull()
        return when {
            value.type !in setOf(MediaSourceType.LOCAL, MediaSourceType.SMB, MediaSourceType.WEBDAV) -> "当前版本不支持该来源"
            value.displayName.isBlank() -> "请输入来源名称"
            value.type == MediaSourceType.LOCAL && value.rootUri == null -> "请选择本地文件夹"
            value.type != MediaSourceType.LOCAL && value.host.isBlank() -> "请输入 IP 地址或主机名"
            value.host.contains("://") || value.host.contains('/') || value.host.contains('\\') ->
                "主机只填写 IP 地址或主机名，不要包含协议或路径"
            value.type != MediaSourceType.LOCAL && (port == null || port !in 1..65535) -> "端口必须是 1 到 65535 之间的数字"
            value.type != MediaSourceType.LOCAL && !value.anonymous && value.username.isBlank() -> "请输入用户名或启用匿名访问"
            else -> null
        }
    }
}

class SourceFormViewModel(private val repository: SourceRepository, private val sourceId: String? = null) : ViewModel() {
    val draft = MutableStateFlow(SourceDraft())
    private var existing: MediaSourceConfig? = null

    init {
        if (sourceId != null) {
            viewModelScope.launch {
                repository.get(sourceId)?.let { source ->
                    existing = source
                    draft.value = SourceDraft(
                        type = source.type,
                        displayName = source.displayName,
                        host = source.endpoint.host,
                        port = source.endpoint.port.toString(),
                        tls = source.endpoint.tls,
                        shareName = source.shareName.orEmpty(),
                        rootPath = source.rootPath,
                        rootUri = source.rootUri,
                        username = source.username.orEmpty(),
                        password = "",
                        anonymous = source.username.isNullOrBlank(),
                    )
                }
            }
        }
    }
    val error = MutableStateFlow<String?>(null)
    val saved = MutableSharedFlow<Unit>()

    fun update(transform: (SourceDraft) -> SourceDraft) {
        draft.value = transform(draft.value)
        error.value = null
    }

    fun setType(type: MediaSourceType) {
        update {
            it.copy(
                type = type,
                tls = type == MediaSourceType.WEBDAV,
                port = when (type) {
                    MediaSourceType.LOCAL -> "0"
                    MediaSourceType.SMB -> "445"
                    MediaSourceType.WEBDAV -> "443"
                    else -> it.port
                },
                shareName = if (type == MediaSourceType.SMB) it.shareName else "",
            )
        }
    }

    fun setLocalTree(uri: String, suggestedName: String) {
        update {
            it.copy(
                rootUri = uri,
                displayName = it.displayName.ifBlank { suggestedName.ifBlank { "本地视频" } },
            )
        }
    }
    fun setTls(enabled: Boolean) {
        update {
            val conventionalOldPort = if (it.tls) "443" else "80"
            it.copy(tls = enabled, port = if (it.port == conventionalOldPort) if (enabled) "443" else "80" else it.port)
        }
    }

    fun save() = viewModelScope.launch {
        val value = draft.value
        error.value = SourceFormValidator.validate(value)
        if (error.value != null) return@launch
        val now = Instant.now()
        repository.save(
            MediaSourceConfig(
                id = existing?.id ?: UUID.randomUUID().toString(),
                displayName = value.displayName.trim(),
                type = value.type,
                endpoint = Endpoint(
                    scheme = when (value.type) {
                        MediaSourceType.LOCAL -> "content"
                        MediaSourceType.SMB -> "smb"
                        MediaSourceType.WEBDAV -> if (value.tls) "https" else "http"
                        else -> value.type.name.lowercase()
                    },
                    host = value.host.trim().takeUnless { value.type == MediaSourceType.LOCAL }.orEmpty(),
                    port = if (value.type == MediaSourceType.LOCAL) 0 else value.port.toInt(),
                    tls = value.type == MediaSourceType.WEBDAV && value.tls,
                ),
                rootPath = value.rootPath.trim().trim('/', '\\'),
                shareName = value.shareName.trim().takeIf { value.type == MediaSourceType.SMB && it.isNotEmpty() },
                rootUri = value.rootUri.takeIf { value.type == MediaSourceType.LOCAL },
                username = value.username.trim().takeUnless { value.type == MediaSourceType.LOCAL || value.anonymous || it.isEmpty() },
                credentialId = existing?.credentialId,
                enabled = true,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
            value.password.takeUnless { value.type == MediaSourceType.LOCAL || value.anonymous },
        )
        saved.emit(Unit)
    }
}

data class BrowserUiState(
    val path: RemotePath = RemotePath(""),
    val entries: List<RemoteEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val favoriteKeys: Set<String> = emptySet(),
)

class BrowserViewModel(
    private val sourceId: String,
    private val fileSystems: RemoteFileSystemFactory,
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
    private val mediaLibrary: MediaLibraryRepository,
    initialPath: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()
    private var fs: RemoteFileSystem? = null
    private var root = RemotePath("")
    private var rawEntries: List<RemoteEntry> = emptyList()
    private var showHiddenFiles = false

    init {
        viewModelScope.launch {
            root = RemotePath(sources.get(sourceId)?.rootPath.orEmpty().trim('/'))
            val start = initialPath?.takeIf { it.isNotBlank() }?.let(::RemotePath) ?: root
            open(start)
        }
        viewModelScope.launch {
            settings.preferences.map { it.showHiddenFiles }.distinctUntilChanged().collect { show ->
                showHiddenFiles = show
                _state.update { it.copy(entries = visibleEntries()) }
            }
        }
        viewModelScope.launch {
            mediaLibrary.observeFavoriteKeys(sourceId).collect { keys ->
                _state.update { it.copy(favoriteKeys = keys.toSet()) }
            }
        }
    }

    fun open(path: RemotePath) = viewModelScope.launch {
        _state.update { it.copy(path = path, loading = true, error = null) }
        try {
            val remote = fs ?: fileSystems.create(sourceId).also {
                it.connect()
                fs = it
            }
            val page = remote.list(path)
            rawEntries = page.items
            _state.update { it.copy(entries = visibleEntries(), loading = false) }
        } catch (error: Throwable) {
            _state.update { it.copy(loading = false, error = error.message ?: "连接失败") }
        }
    }

    fun toggleFavorite(entry: RemoteEntry) = viewModelScope.launch {
        mediaLibrary.toggleFavorite(sourceId, entry)
    }

    fun favoriteKey(entry: RemoteEntry): String = MediaIdentity.entryKey(
        sourceId,
        entry.path.value,
        entry.isDirectory,
        entry.size,
        entry.modifiedAt?.toEpochMilli(),
    )
    fun up(): Boolean {
        val current = _state.value.path
        if (current.value == root.value) return false
        open(current.parent)
        return true
    }

    private fun visibleEntries() = rawEntries.filter { showHiddenFiles || !it.name.startsWith(".") }

    override fun onCleared() { fs?.close() }
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val state = repository.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerPreferences(),
    )

    fun setPlaybackSpeed(value: Float) = viewModelScope.launch { repository.setPlaybackSpeed(value) }
    fun setVideoScale(value: VideoScale) = viewModelScope.launch { repository.setVideoScale(value) }
    fun setBufferPreset(value: BufferPreset) = viewModelScope.launch { repository.setBufferPreset(value) }
    fun setRememberProgress(value: Boolean) = viewModelScope.launch { repository.setRememberProgress(value) }
    fun setCompletionThreshold(value: Float) = viewModelScope.launch { repository.setCompletionThreshold(value) }
    fun setAutoLandscape(value: Boolean) = viewModelScope.launch { repository.setAutoLandscape(value) }
    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { repository.setKeepScreenOn(value) }
    fun setShowHiddenFiles(value: Boolean) = viewModelScope.launch { repository.setShowHiddenFiles(value) }
}
@Suppress("UNCHECKED_CAST")
class SimpleViewModelFactory<VM : ViewModel>(private val create: () -> VM) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
