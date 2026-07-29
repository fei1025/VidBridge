package com.vidbridge.protocol.nfs

import com.emc.ecs.nfsclient.nfs.io.Nfs3File
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3
import com.emc.ecs.nfsclient.rpc.CredentialUnix
import com.vidbridge.protocol.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Instant

/** Read-only NFSv3 adapter using AUTH_SYS credentials. */
class NfsFileSystem(
    private val config: MediaSourceConfig,
) : RemoteFileSystem {
    override val sourceId: String = config.id
    override val capabilities = SourceCapabilities(
        canList = true,
        canStat = true,
        canSeekRead = true,
        canStreamRead = true,
    )

    private var client: Nfs3? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (client != null) return@withContext
        try {
            client = Nfs3(
                config.endpoint.host,
                exportPath(),
                CredentialUnix(0, 0, null),
                MAX_RETRIES,
            )
            rootFile().exists()
        } catch (error: Throwable) {
            close()
            throw mapFailure(error)
        }
    }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> = withContext(Dispatchers.IO) {
        try {
            val all = synchronized(this@NfsFileSystem) {
                rootFile(path).listFiles().map { file ->
                    val directory = file.isDirectory
                    RemoteEntry(
                        name = file.name,
                        path = path.child(file.name),
                        isDirectory = directory,
                        size = if (directory) null else file.lengthEx(),
                        modifiedAt = Instant.ofEpochMilli(file.lastModified()),
                    )
                }.sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
            paginate(all, page)
        } catch (error: Throwable) {
            throw mapFailure(error)
        }
    }

    override suspend fun stat(path: RemotePath): RemoteFileInfo = withContext(Dispatchers.IO) {
        try {
            val file = rootFile(path)
            if (!file.exists()) throw SourceFailure.NotFound()
            RemoteFileInfo(path, file.lengthEx().takeUnless { file.isDirectory }, Instant.ofEpochMilli(file.lastModified()))
        } catch (error: Throwable) {
            throw mapFailure(error)
        }
    }

    override suspend fun open(path: RemotePath): RemoteReadHandle = withContext(Dispatchers.IO) {
        try {
            val file = rootFile(path)
            if (!file.exists() || file.isDirectory) throw SourceFailure.NotFound()
            NfsReadHandle(file, file.lengthEx())
        } catch (error: Throwable) {
            throw mapFailure(error)
        }
    }

    override fun close() {
        // Nfs3 has no public close API; dropping the client releases the source reference.
        client = null
    }

    private fun rootFile(path: RemotePath = RemotePath("")): Nfs3File {
        val nfs = client ?: throw SourceFailure.HostUnreachable()
        return nfs.newFile(nfsRelativePath(config.rootPath, path.value))
    }

    private fun exportPath(): String = "/" + config.rootPath.trim('/')

    private fun mapFailure(error: Throwable): SourceFailure {
        if (error is SourceFailure) return error
        val message = error.message.orEmpty().lowercase()
        return when {
            "permission" in message || "access" in message || "denied" in message -> SourceFailure.PermissionDenied(error)
            "not found" in message || "no such" in message || "no file" in message -> SourceFailure.NotFound(error)
            "timeout" in message || "timed out" in message -> SourceFailure.Timeout(error)
            "network" in message || "connect" in message || "mount" in message -> SourceFailure.HostUnreachable(error)
            else -> SourceFailure.Unknown(error)
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

internal fun nfsRelativePath(exportPath: String, mediaPath: String): String {
    val export = exportPath.trim('/')
    val media = mediaPath.trim('/')
    val relative = when {
        media.isBlank() || media == export -> ""
        media.startsWith("$export/") -> media.removePrefix("$export/")
        else -> media
    }
    return "/${relative.trim('/')}"
}

private class NfsReadHandle(
    private val file: Nfs3File,
    override val size: Long,
) : RemoteReadHandle {
    override val seekable = true
    @Volatile private var closed = false

    override suspend fun readAt(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        check(!closed) { "读取句柄已关闭" }
        require(offset >= 0 && length >= 0)
        if (length == 0 || offset >= size) return@withContext ByteArray(0)
        synchronized(file) {
            val response = file.read(offset, length, ByteArray(length), 0)
            response.bytes.copyOf(response.bytesRead)
        }
    }

    override fun stream(startOffset: Long): Flow<ByteArray> = flow {
        var offset = startOffset
        while (!closed && offset < size) {
            val bytes = readAt(offset, CHUNK_SIZE)
            if (bytes.isEmpty()) break
            emit(bytes)
            offset += bytes.size
        }
    }

    override fun close() {
        closed = true
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
