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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.EnumSet

class SmbFileSystem(
    private val config: MediaSourceConfig,
    private val credentialStore: CredentialStore,
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
    private var share: DiskShare? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (share != null) return@withContext
        val shareName = config.shareName?.takeIf { it.isNotBlank() }
            ?: throw SourceFailure.ProtocolMismatch()
        val smbClient = SMBClient()
        try {
            val smbConnection = smbClient.connect(config.endpoint.host, config.endpoint.port)
            val password = config.credentialId?.let(credentialStore::get)?.password.orEmpty()
            val auth = if (config.username.isNullOrBlank()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(config.username, password.toCharArray(), null)
            }
            val smbSession = smbConnection.authenticate(auth)
            val diskShare = smbSession.connectShare(shareName) as DiskShare
            client = smbClient
            connection = smbConnection
            session = smbSession
            share = diskShare
        } catch (error: Throwable) {
            runCatching { smbClient.close() }
            throw mapFailure(error)
        }
    }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val all = try {
                share!!.list(normalize(path)).asSequence()
                    .filterNot { it.fileName == "." || it.fileName == ".." }
                    .map {
                        RemoteEntry(
                            name = it.fileName,
                            path = path.child(it.fileName),
                            isDirectory = it.fileAttributes and DIRECTORY_ATTRIBUTE != 0L,
                            size = if (it.fileAttributes and DIRECTORY_ATTRIBUTE != 0L) null else it.endOfFile,
                            modifiedAt = runCatching { Instant.ofEpochMilli(it.changeTime.toEpochMillis()) }.getOrNull(),
                        )
                    }
                    .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    .toList()
            } catch (error: Throwable) {
                throw mapFailure(error)
            }
            val request = page ?: PageRequest(0, all.size.coerceAtLeast(1))
            val items = all.drop(request.offset).take(request.limit)
            val nextOffset = request.offset + items.size
            Page(items, if (nextOffset < all.size) PageRequest(nextOffset, request.limit) else null)
        }

    override suspend fun stat(path: RemotePath): RemoteFileInfo = withContext(Dispatchers.IO) {
        ensureConnected()
        try {
            val info = share!!.getFileInformation(normalize(path))
            RemoteFileInfo(path, info.standardInformation.endOfFile, null)
        } catch (error: Throwable) {
            throw mapFailure(error)
        }
    }

    override suspend fun open(path: RemotePath): RemoteReadHandle = withContext(Dispatchers.IO) {
        ensureConnected()
        try {
            val file = share!!.openFile(
                normalize(path),
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
        if (share == null) connect()
    }

    override fun close() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null
        session = null
        connection = null
        client = null
    }

    private fun normalize(path: RemotePath) = path.value.trim('/').replace('/', '\\')

    private fun mapFailure(error: Throwable): SourceFailure {
        val message = error.message.orEmpty().lowercase()
        return when {
            "logon" in message || "authentication" in message -> SourceFailure.AuthenticationRejected(error)
            "access denied" in message -> SourceFailure.PermissionDenied(error)
            "not found" in message || "no such file" in message -> SourceFailure.NotFound(error)
            "timeout" in message -> SourceFailure.Timeout(error)
            "connect" in message || "route" in message || "host" in message -> SourceFailure.HostUnreachable(error)
            else -> SourceFailure.Unknown(error)
        }
    }

    companion object { private const val DIRECTORY_ATTRIBUTE: Long = 16 }
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
