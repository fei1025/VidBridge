package com.vidbridge.protocol.sftp

import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.vidbridge.core.security.CredentialStore
import com.vidbridge.protocol.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Instant

/** Read-only SFTP adapter. The SSH session is owned by the source instance. */
class SftpFileSystem(
    context: Context,
    private val config: MediaSourceConfig,
    private val credentials: CredentialStore,
) : RemoteFileSystem {
    override val sourceId: String = config.id
    override val capabilities = SourceCapabilities(
        canList = true,
        canStat = true,
        canSeekRead = true,
        canStreamRead = true,
    )

    private var session: Session? = null
    private var channel: ChannelSftp? = null
    private val knownHostsFile = context.applicationContext
        .getFileStreamPath(KNOWN_HOSTS_FILE)

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (channel?.isConnected == true) return@withContext
        val username = config.username?.takeIf(String::isNotBlank)
            ?: throw SourceFailure.AuthenticationRequired()
        val password = config.credentialId?.let(credentials::get)?.password
            ?: throw SourceFailure.AuthenticationRequired()
        try {
            val jsch = JSch()
            knownHostsFile.parentFile?.mkdirs()
            if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
            jsch.setKnownHosts(knownHostsFile.absolutePath)
            val sshSession = jsch.getSession(username, config.endpoint.host, config.endpoint.port)
            // Trust on first use, then reject a changed key. No host key is put in the media URI.
            sshSession.setConfig("StrictHostKeyChecking", "ask")
            sshSession.userInfo = SftpHostKeyPolicy
            sshSession.setPassword(password)
            sshSession.connect(CONNECT_TIMEOUT_MS)
            val sftp = sshSession.openChannel("sftp") as ChannelSftp
            sftp.connect(CONNECT_TIMEOUT_MS)
            session = sshSession
            channel = sftp
        } catch (error: Throwable) {
            close()
            throw mapFailure(error)
        }
    }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> = withContext(Dispatchers.IO) {
        ensureConnected()
        try {
            val all = synchronized(channel!!) {
                channel!!.ls(remotePath(path))
                    .asSequence()
                    .filterIsInstance<ChannelSftp.LsEntry>()
                    .filterNot { it.filename == "." || it.filename == ".." }
                    .map { entry ->
                        val attrs = entry.attrs
                        RemoteEntry(
                            name = entry.filename,
                            path = path.child(entry.filename),
                            isDirectory = attrs.isDir,
                            size = attrs.size.takeUnless { attrs.isDir },
                            modifiedAt = Instant.ofEpochSecond(attrs.mTime.toLong()),
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
        try {
            val attrs = synchronized(channel!!) { channel!!.stat(remotePath(path)) }
            RemoteFileInfo(path, attrs.size.takeUnless { attrs.isDir }, Instant.ofEpochSecond(attrs.mTime.toLong()))
        } catch (error: Throwable) {
            throw mapFailure(error)
        }
    }

    override suspend fun open(path: RemotePath): RemoteReadHandle = withContext(Dispatchers.IO) {
        ensureConnected()
        try {
            val attrs = synchronized(channel!!) { channel!!.stat(remotePath(path)) }
            SftpReadHandle(channel!!, remotePath(path), attrs.size.takeUnless { attrs.isDir })
        } catch (error: Throwable) {
            throw mapFailure(error)
        }
    }

    override fun close() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        channel = null
        session = null
    }

    private suspend fun ensureConnected() {
        if (channel?.isConnected != true) connect()
    }

    private fun remotePath(path: RemotePath): String = "/" + path.value.trim('/')

    private fun mapFailure(error: Throwable): SourceFailure {
        if (error is SourceFailure) return error
        val message = error.message.orEmpty().lowercase()
        return when {
            "hostkey" in message || "host key" in message -> SourceFailure.CertificateRejected(error)
            "auth" in message || "password" in message || "userauth" in message -> SourceFailure.AuthenticationRejected(error)
            "permission" in message || "denied" in message -> SourceFailure.PermissionDenied(error)
            "no such file" in message || "not found" in message -> SourceFailure.NotFound(error)
            "timeout" in message || "timed out" in message -> SourceFailure.Timeout(error)
            "unknownhost" in message || "connect" in message || "socket" in message -> SourceFailure.HostUnreachable(error)
            else -> SourceFailure.Unknown(error)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val KNOWN_HOSTS_FILE = "sftp_known_hosts"
    }
}

internal object SftpHostKeyPolicy : UserInfo {
    override fun getPassphrase(): String? = null
    override fun getPassword(): String? = null
    override fun promptPassword(message: String?): Boolean = false
    override fun promptPassphrase(message: String?): Boolean = false
    override fun promptYesNo(message: String?): Boolean =
        message?.contains("changed", ignoreCase = true) != true
    override fun showMessage(message: String?) = Unit
}

private class SftpReadHandle(
    private val channel: ChannelSftp,
    private val path: String,
    override val size: Long?,
) : RemoteReadHandle {
    override val seekable = true
    @Volatile private var closed = false

    override suspend fun readAt(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        check(!closed) { "读取句柄已关闭" }
        require(offset >= 0 && length >= 0)
        if (length == 0 || (size != null && offset >= size)) return@withContext ByteArray(0)
        synchronized(channel) {
            channel.get(path, null, offset).use { input ->
                readLimited(input, length)
            }
        }
    }

    override fun stream(startOffset: Long): Flow<ByteArray> = flow {
        var offset = startOffset
        while (!closed && (size == null || offset < size)) {
            val bytes = readAt(offset, CHUNK_SIZE)
            if (bytes.isEmpty()) break
            emit(bytes)
            offset += bytes.size
        }
    }

    override fun close() {
        closed = true
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArray(limit)
        var count = 0
        while (count < limit) {
            val read = input.read(output, count, limit - count)
            if (read < 0) break
            count += read
        }
        return if (count == output.size) output else output.copyOf(count)
    }

    private companion object {
        const val CHUNK_SIZE = 256 * 1024
    }
}

private fun paginate(items: List<RemoteEntry>, page: PageRequest?): Page<RemoteEntry> {
    val request = page ?: PageRequest(0, items.size.coerceAtLeast(1))
    val result = items.drop(request.offset).take(request.limit)
    val next = request.offset + result.size
    return Page(result, next.takeIf { it < items.size }?.let { PageRequest(it, request.limit) })
}
