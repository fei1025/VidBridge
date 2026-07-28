package com.vidbridge.protocol.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.vidbridge.core.security.CredentialStore
import com.vidbridge.protocol.api.*
import jcifs.SmbConstants
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.EnumSet
import java.util.Properties

class SmbFileSystem(
    private val config: MediaSourceConfig,
    private val credentialStore: CredentialStore,
    private val shareDiscovery: SmbShareDiscovery = JcifsSmbShareDiscovery(),
) : RemoteFileSystem {
    override val sourceId: String = config.id
    override val capabilities = SourceCapabilities(
        canList = true,
        canStat = true,
        canSeekRead = true,
        canStreamRead = true,
    )

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private val shares = linkedMapOf<String, DiskShare>()

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (session != null) return@withContext
        val smbClient = SMBClient()
        try {
            val smbConnection = smbClient.connect(config.endpoint.host, config.endpoint.port)
            val password = password()
            val auth = if (config.username.isNullOrBlank()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(config.username, password.toCharArray(), null)
            }
            val smbSession = smbConnection.authenticate(auth)
            client = smbClient
            connection = smbConnection
            session = smbSession
            configuredShare()?.let(::diskShare)
        } catch (error: Throwable) {
            close()
            runCatching { smbClient.close() }
            throw mapFailure(error)
        }
    }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> =
        withContext(Dispatchers.IO) {
            ensureConnected()
            try {
                val location = SmbPathRouter.resolve(config.shareName, path)
                val all = if (location == null) {
                    shareDiscovery.discover(config, password()).map { name ->
                        RemoteEntry(name, path.child(name), true, null, null)
                    }
                } else {
                    diskShare(location.shareName).list(normalize(location.relativePath)).asSequence()
                        .filterNot { it.fileName == "." || it.fileName == ".." }
                        .map {
                            val isDirectory = it.fileAttributes and DIRECTORY_ATTRIBUTE != 0L
                            RemoteEntry(
                                name = it.fileName,
                                path = path.child(it.fileName),
                                isDirectory = isDirectory,
                                size = if (isDirectory) null else it.endOfFile,
                                modifiedAt = runCatching { Instant.ofEpochMilli(it.changeTime.toEpochMillis()) }.getOrNull(),
                            )
                        }
                        .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        .toList()
                }
                paginate(all, page)
            } catch (error: Throwable) {
                throw mapFailure(error)
            }
        }

    override suspend fun stat(path: RemotePath): RemoteFileInfo = withContext(Dispatchers.IO) {
        ensureConnected()
        val location = SmbPathRouter.resolve(config.shareName, path)
            ?: throw SourceFailure.UnsupportedOperation()
        try {
            val info = diskShare(location.shareName).getFileInformation(normalize(location.relativePath))
            RemoteFileInfo(path, info.standardInformation.endOfFile, null)
        } catch (error: Throwable) {
            throw mapFailure(error)
        }
    }

    override suspend fun open(path: RemotePath): RemoteReadHandle = withContext(Dispatchers.IO) {
        ensureConnected()
        val location = SmbPathRouter.resolve(config.shareName, path)
            ?: throw SourceFailure.UnsupportedOperation()
        try {
            val file = diskShare(location.shareName).openFile(
                normalize(location.relativePath),
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
            )
            SmbReadHandle(file, file.fileInformation.standardInformation.endOfFile)
        } catch (error: Throwable) {
            throw mapFailure(error)
        }
    }

    private suspend fun ensureConnected() {
        if (session == null) connect()
    }

    private fun diskShare(name: String): DiskShare {
        shares.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.let { return it.value }
        val connected = session?.connectShare(name) as? DiskShare
            ?: throw SourceFailure.ProtocolMismatch()
        shares[name] = connected
        return connected
    }

    override fun close() {
        shares.values.forEach { share -> runCatching { share.close() } }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        shares.clear()
        session = null
        connection = null
        client = null
    }

    private fun password() = config.credentialId?.let(credentialStore::get)?.password.orEmpty()
    private fun configuredShare() = config.shareName?.trim()?.takeIf(String::isNotEmpty)
    private fun normalize(path: RemotePath) = path.value.trim('/').replace('/', '\\')

    private fun mapFailure(error: Throwable): SourceFailure {
        if (error is SourceFailure) return error
        val message = error.message.orEmpty().lowercase()
        return when {
            "logon" in message || "authentication" in message || "credentials" in message -> SourceFailure.AuthenticationRejected(error)
            "access denied" in message -> SourceFailure.PermissionDenied(error)
            "not found" in message || "no such file" in message -> SourceFailure.NotFound(error)
            "timeout" in message -> SourceFailure.Timeout(error)
            "connect" in message || "route" in message || "host" in message -> SourceFailure.HostUnreachable(error)
            else -> SourceFailure.Unknown(error)
        }
    }

    companion object { private const val DIRECTORY_ATTRIBUTE: Long = 16 }
}

internal data class SmbLocation(val shareName: String, val relativePath: RemotePath)

internal object SmbPathRouter {
    fun resolve(configuredShare: String?, path: RemotePath): SmbLocation? {
        val fixedShare = configuredShare?.trim()?.trim('/', '\\')?.takeIf(String::isNotEmpty)
        val normalized = path.value.trim('/').replace('\\', '/')
        if (fixedShare != null) return SmbLocation(fixedShare, RemotePath(normalized))
        if (normalized.isEmpty()) return null
        return SmbLocation(
            shareName = normalized.substringBefore('/'),
            relativePath = RemotePath(normalized.substringAfter('/', "")),
        )
    }
}

fun interface SmbShareDiscovery {
    fun discover(config: MediaSourceConfig, password: String): List<String>
}

private class JcifsSmbShareDiscovery : SmbShareDiscovery {
    override fun discover(config: MediaSourceConfig, password: String): List<String> {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val baseContext = BaseContext(PropertyConfiguration(properties))
        val context = if (config.username.isNullOrBlank()) {
            baseContext.withAnonymousCredentials()
        } else {
            baseContext.withCredentials(NtlmPasswordAuthenticator("", config.username, password))
        }
        val root = SmbFile("smb://${config.endpoint.host}:${config.endpoint.port}/", context)
        return try {
            root.listFiles().asSequence()
                .filter { it.type == SmbConstants.TYPE_SHARE }
                .map { it.name.trimEnd('/') }
                .filter { it.isNotBlank() && !it.endsWith('$') }
                .distinctBy { it.lowercase() }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .toList()
        } finally {
            runCatching { root.close() }
            runCatching { context.close() }
            if (context !== baseContext) runCatching { baseContext.close() }
        }
    }
}

internal fun paginate(entries: List<RemoteEntry>, page: PageRequest?): Page<RemoteEntry> {
    val request = page ?: PageRequest(0, entries.size.coerceAtLeast(1))
    val items = entries.drop(request.offset).take(request.limit)
    val nextOffset = request.offset + items.size
    return Page(items, if (nextOffset < entries.size) PageRequest(nextOffset, request.limit) else null)
}

private class SmbReadHandle(
    private val file: com.hierynomus.smbj.share.File,
    override val size: Long,
) : RemoteReadHandle {
    override val seekable = true

    override suspend fun readAt(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        require(offset >= 0 && length >= 0)
        if (offset >= size || length == 0) return@withContext ByteArray(0)
        val buffer = ByteArray(minOf(length.toLong(), size - offset).toInt())
        val count = file.read(buffer, offset, 0, buffer.size)
        when {
            count <= 0 -> ByteArray(0)
            count == buffer.size -> buffer
            else -> buffer.copyOf(count)
        }
    }

    override fun stream(startOffset: Long): Flow<ByteArray> = flow {
        var offset = startOffset
        while (offset < size) {
            val bytes = readAt(offset, 1024 * 1024)
            if (bytes.isEmpty()) break
            emit(bytes)
            offset += bytes.size
        }
    }

    override fun close() = file.close()
}
