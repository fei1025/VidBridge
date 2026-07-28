package com.vidbridge.protocol.api

import kotlinx.coroutines.flow.Flow
import java.time.Instant

enum class MediaSourceType { LOCAL, SMB, NFS, WEBDAV, SFTP, JELLYFIN, EMBY, PLEX, DLNA }

data class Endpoint(
    val scheme: String,
    val host: String,
    val port: Int,
    val tls: Boolean = false,
)

data class MediaSourceConfig(
    val id: String,
    val displayName: String,
    val type: MediaSourceType,
    val endpoint: Endpoint,
    val rootPath: String,
    val shareName: String?,
    val rootUri: String? = null,
    val username: String?,
    val credentialId: String?,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@JvmInline
value class RemotePath(val value: String) {
    fun child(name: String) = RemotePath(listOf(value.trim('/'), name.trim('/')).filter { it.isNotEmpty() }.joinToString("/"))
    val parent: RemotePath get() = RemotePath(value.trim('/').substringBeforeLast('/', ""))
}

data class SourceCapabilities(
    val canList: Boolean,
    val canStat: Boolean,
    val canSeekRead: Boolean,
    val canStreamRead: Boolean,
    val canWrite: Boolean = false,
    val canDelete: Boolean = false,
    val canRename: Boolean = false,
    val canSearch: Boolean = false,
    val supportsChangeToken: Boolean = false,
    val supportsServerSideMetadata: Boolean = false,
)

data class RemoteEntry(
    val name: String,
    val path: RemotePath,
    val isDirectory: Boolean,
    val size: Long?,
    val modifiedAt: Instant?,
)

data class RemoteFileInfo(
    val path: RemotePath,
    val size: Long?,
    val modifiedAt: Instant?,
)

data class PageRequest(val offset: Int = 0, val limit: Int = 250)
data class Page<T>(val items: List<T>, val next: PageRequest?)

interface RemoteReadHandle : AutoCloseable {
    val size: Long?
    val seekable: Boolean
    suspend fun readAt(offset: Long, length: Int): ByteArray
    fun stream(startOffset: Long = 0): Flow<ByteArray>
}

interface RemoteFileSystem : AutoCloseable {
    val sourceId: String
    val capabilities: SourceCapabilities
    suspend fun connect()
    suspend fun list(path: RemotePath, page: PageRequest? = null): Page<RemoteEntry>
    suspend fun stat(path: RemotePath): RemoteFileInfo
    suspend fun open(path: RemotePath): RemoteReadHandle
}

sealed class SourceFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationRequired : SourceFailure("需要登录凭据")
    class AuthenticationRejected(cause: Throwable? = null) : SourceFailure("用户名或密码错误", cause)
    class HostUnreachable(cause: Throwable? = null) : SourceFailure("无法连接服务器", cause)
    class Timeout(cause: Throwable? = null) : SourceFailure("连接超时", cause)
    class PermissionDenied(cause: Throwable? = null) : SourceFailure("没有访问权限", cause)
    class CertificateRejected(cause: Throwable? = null) : SourceFailure("服务器证书不受信任", cause)
    class NotFound(cause: Throwable? = null) : SourceFailure("文件或目录不存在", cause)
    class UnsupportedOperation : SourceFailure("当前来源不支持此操作")
    class ProtocolMismatch(cause: Throwable? = null) : SourceFailure("协议不匹配", cause)
    class Unknown(cause: Throwable) : SourceFailure("未知错误", cause)
}
